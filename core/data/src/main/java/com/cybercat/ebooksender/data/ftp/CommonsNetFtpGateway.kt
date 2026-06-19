package com.cybercat.ebooksender.data.ftp

import com.cybercat.ebooksender.data.network.LocalDeviceNetworkProvider
import com.cybercat.ebooksender.model.RemoteDevice
import com.cybercat.ebooksender.model.normalizeFtpRelativeRootPath
import com.cybercat.ebooksender.model.normalizeFtpRootPath
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply

class CommonsNetFtpGateway @Inject constructor(
    private val localDeviceNetworkProvider: LocalDeviceNetworkProvider
) : FtpGateway {
    override suspend fun checkConnection(device: RemoteDevice): Result<FtpSessionInfo> =
        withFtpClient(device) { client ->
            runCatching {
                client.listFiles()
                check(FTPReply.isPositiveCompletion(client.replyCode)) {
                    "Cannot read ${device.workingRootPath}: ${client.replyString}"
                }
                FtpSessionInfo(
                    rootPath = device.workingRootPath,
                    systemType = runCatching { client.systemType }.getOrNull()
                )
            }
        }

    override suspend fun uploadAtomically(
        device: RemoteDevice,
        remoteRelativePath: String,
        input: InputStream,
        onProgress: ((Long) -> Unit)?
    ): Result<Unit> {
        val normalized = remoteRelativePath.toSafeRelativeFtpPath()
        val directory = normalized.substringBeforeLast('/', missingDelimiterValue = "")
        val fileName = normalized.substringAfterLast('/')
        val tempName = ".$fileName.uploading"
        val tempPath = if (directory.isBlank()) tempName else "$directory/$tempName"

        return try {
            withFtpClient(device) { client ->
                try {
                    if (directory.isNotBlank()) {
                        client.makeDirectories(directory)
                    }

                    val uploadContext = currentCoroutineContext()
                    val activeDataStream = AtomicReference<Closeable?>()
                    val cancellationHandle = uploadContext[Job]?.invokeOnCompletion { cause ->
                        if (cause is CancellationException) {
                            runCatching { activeDataStream.get()?.close() }
                            runCatching { client.disconnect() }
                        }
                    }

                    try {
                        val output = client.storeFileStream(tempPath)
                            ?: error("FTP upload failed: ${client.replyString}")
                        activeDataStream.set(output)
                        output.use { stream ->
                            input.copyToFtp(
                                output = stream,
                                onProgress = onProgress,
                                ensureActive = { uploadContext.ensureActive() }
                            )
                        }
                        uploadContext.ensureActive()
                        activeDataStream.set(null)
                        check(client.completePendingCommand()) {
                            "FTP upload failed: ${client.replyString}"
                        }
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        if (uploadContext[Job]?.isCancelled == true) {
                            throw CancellationException("FTP upload canceled").also {
                                it.initCause(error)
                            }
                        }
                        throw error
                    } finally {
                        activeDataStream.getAndSet(null)?.let { stream ->
                            runCatching { stream.close() }
                        }
                        cancellationHandle?.dispose()
                    }

                    uploadContext.ensureActive()

                    if (client.listFiles(normalized).isNotEmpty()) {
                        client.deleteFile(normalized)
                    }

                    uploadContext.ensureActive()

                    check(client.rename(tempPath, normalized)) {
                        "FTP rename failed: ${client.replyString}"
                    }

                    Result.success(Unit)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    Result.failure(error)
                }
            }
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                deleteFile(device, tempPath)
            }
            throw error
        }
    }

    override suspend fun listDirectories(
        device: RemoteDevice,
        remoteRelativePath: String
    ): Result<List<String>> = withFtpClient(device) { client ->
        runCatching {
            val normalizedPath = remoteRelativePath.toSafeRelativeFtpPathOrBlank()
            client.listFiles(normalizedPath)
                .filter(FTPFile::isDirectory)
                .mapNotNull { file -> file.name?.toSafeFtpEntryNameOrNull() }
                .sorted()
        }
    }

    override suspend fun listEntries(
        device: RemoteDevice,
        remoteRelativePath: String
    ): Result<List<FtpEntry>> = withFtpClient(device) { client ->
        runCatching {
            val normalizedPath = remoteRelativePath.toSafeRelativeFtpPathOrBlank()
            client.listFiles(normalizedPath)
                .mapNotNull { file ->
                    val safeName = file.name?.toSafeFtpEntryNameOrNull() ?: return@mapNotNull null
                    val childPath = buildSafeChildFtpPath(normalizedPath, safeName)
                        ?: return@mapNotNull null
                    FtpEntry(
                        name = safeName,
                        path = childPath,
                        isDirectory = file.isDirectory,
                        size = file.size,
                        modifiedAtMillis = file.timestamp?.timeInMillis
                    )
                }
                .sortedWith(compareBy<FtpEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    override suspend fun downloadFile(
        device: RemoteDevice,
        remoteRelativePath: String,
        output: OutputStream
    ): Result<Unit> = withFtpClient(device) { client ->
        runCatching {
            val normalized = remoteRelativePath.toSafeRelativeFtpPath()
            output.use { stream ->
                check(client.retrieveFile(normalized, stream)) {
                    "FTP download failed for $normalized: ${client.replyString}"
                }
            }
        }
    }

    override suspend fun deleteFile(
        device: RemoteDevice,
        remoteRelativePath: String
    ): Result<Unit> = withFtpClient(device) { client ->
        runCatching {
            val normalized = remoteRelativePath.toSafeRelativeFtpPath()
            check(client.deleteFile(normalized)) {
                "FTP delete file failed for $normalized: ${client.replyString}"
            }
        }
    }

    override suspend fun deleteDirectory(
        device: RemoteDevice,
        remoteRelativePath: String
    ): Result<Unit> = withFtpClient(device) { client ->
        runCatching {
            val normalized = remoteRelativePath.toSafeRelativeFtpPath()
            check(client.removeDirectory(normalized)) {
                "FTP delete directory failed for $normalized: ${client.replyString}"
            }
        }
    }

    override suspend fun rename(
        device: RemoteDevice,
        fromPath: String,
        toPath: String
    ): Result<Unit> = withFtpClient(device) { client ->
        runCatching {
            val normalizedFrom = fromPath.toSafeRelativeFtpPath()
            val normalizedTo = toPath.toSafeRelativeFtpPath()

            val pwd = client.printWorkingDirectory()
            var fromExists = false
            if (client.changeWorkingDirectory(normalizedFrom)) {
                fromExists = true
                if (pwd != null) {
                    client.changeWorkingDirectory(pwd)
                }
            }

            if (fromExists) {
                check(client.rename(normalizedFrom, normalizedTo)) {
                    "FTP rename failed from $normalizedFrom to $normalizedTo: ${client.replyString}"
                }
            }
        }
    }

    private suspend fun <T> withFtpClient(
        device: RemoteDevice,
        block: suspend (FTPClient) -> Result<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        val client = FTPClient()

        try {
            localDeviceNetworkProvider.socketFactory()?.let(client::setSocketFactory)
            client.controlEncoding = Charsets.UTF_8.name()
            client.connectTimeout = CONNECT_TIMEOUT_MS
            client.defaultTimeout = CONNECT_TIMEOUT_MS
            client.setDataTimeout(java.time.Duration.ofMillis(DATA_TIMEOUT_MS.toLong()))

            client.connect(device.host, device.port)
            client.soTimeout = DATA_TIMEOUT_MS
            check(FTPReply.isPositiveCompletion(client.replyCode)) {
                "FTP refused connection: ${client.replyString}"
            }

            check(client.login(device.username, "")) {
                "FTP login failed: ${client.replyString}"
            }

            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            client.sendCommand("OPTS UTF8 ON")

            client.openWorkingRoot(device)

            block(client)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Result.failure(error)
        } finally {
            if (client.isConnected) {
                runCatching { client.logout() }
                runCatching { client.disconnect() }
            }
        }
    }

    private fun FTPClient.openWorkingRoot(device: RemoteDevice) {
        val mountRoot = normalizeFtpRootPath(device.rootPath)
        check(changeWorkingDirectory(mountRoot)) {
            "Cannot open $mountRoot: $replyString"
        }

        val relativeRoot = normalizeFtpRelativeRootPath(device.relativeRootPath)
        if (relativeRoot.isBlank()) return

        makeDirectories(relativeRoot)
        check(changeWorkingDirectory(relativeRoot)) {
            "Cannot open ${device.workingRootPath}: $replyString"
        }
    }

    private fun FTPClient.makeDirectories(relativePath: String) {
        val original = printWorkingDirectory()
        relativePath
            .split('/')
            .filter { it.isNotBlank() }
            .forEach { part ->
                if (!changeWorkingDirectory(part)) {
                    makeDirectory(part)
                    check(changeWorkingDirectory(part)) {
                        "Cannot create FTP directory $part: $replyString"
                    }
                }
            }
        if (original != null) {
            changeWorkingDirectory(original)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val DATA_TIMEOUT_MS = 30_000
    }
}

private fun InputStream.copyToFtp(
    output: OutputStream,
    onProgress: ((Long) -> Unit)?,
    ensureActive: () -> Unit
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var bytesRead = 0L
    var lastUpdate = 0L

    fun reportProgress(force: Boolean = false) {
        val progressCallback = onProgress ?: return
        val now = System.currentTimeMillis()
        if (force || now - lastUpdate >= 100) {
            progressCallback(bytesRead)
            lastUpdate = now
        }
    }

    try {
        while (true) {
            ensureActive()
            val read = read(buffer)
            ensureActive()
            if (read <= 0) break
            output.write(buffer, 0, read)
            bytesRead += read
            reportProgress()
        }
        output.flush()
        ensureActive()
    } finally {
        reportProgress(force = true)
    }
}

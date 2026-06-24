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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
                client.checkReply("Cannot read ${device.workingRootPath}")
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
                        val output = client.storeFileStream(tempPath) ?: throw FtpReplyException(
                            replyCode = client.replyCode,
                            replyString = client.replyString ?: "",
                            message = "FTP upload failed: ${client.replyString}"
                        )
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
                        client.checkAction(client.completePendingCommand(), "FTP upload failed")
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

                    client.checkAction(client.rename(tempPath, normalized), "FTP rename failed")

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
            client.listSafeEntries(normalizedPath)
        }
    }

    override suspend fun listEntries(
        device: RemoteDevice,
        remoteRelativePaths: Collection<String>
    ): Result<Map<String, List<FtpEntry>>> {
        val paths = remoteRelativePaths.distinct()
        if (paths.isEmpty()) return Result.success(emptyMap())
        if (paths.size == 1) {
            val path = paths.single()
            return listEntries(device, path).map { entries -> mapOf(path to entries) }
        }

        return runCatching {
            val semaphore = Semaphore(MAX_PARALLEL_LIST_CONNECTIONS)
            coroutineScope {
                paths
                    .map { path ->
                        async {
                            semaphore.withPermit {
                                path to listEntries(device, path).getOrThrow()
                            }
                        }
                    }
                    .awaitAll()
                    .toMap()
            }
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
                client.checkAction(
                    client.retrieveFile(normalized, stream),
                    "FTP download failed for $normalized"
                )
            }
        }
    }

    override suspend fun deleteFile(
        device: RemoteDevice,
        remoteRelativePath: String
    ): Result<Unit> = withFtpClient(device) { client ->
        runCatching {
            val normalized = remoteRelativePath.toSafeRelativeFtpPath()
            client.checkAction(
                client.deleteFile(normalized),
                "FTP delete file failed for $normalized"
            )
        }
    }

    override suspend fun deleteFiles(
        device: RemoteDevice,
        remoteRelativePaths: Collection<String>
    ): Result<FtpBatchOperationResult> = withFtpClient(device) { client ->
        runCatching {
            client.deletePaths(remoteRelativePaths) { path ->
                checkAction(deleteFile(path), "FTP delete file failed for $path")
            }
        }
    }

    override suspend fun deleteDirectory(
        device: RemoteDevice,
        remoteRelativePath: String
    ): Result<Unit> = withFtpClient(device) { client ->
        runCatching {
            val normalized = remoteRelativePath.toSafeRelativeFtpPath()
            client.checkAction(
                client.removeDirectory(normalized),
                "FTP delete directory failed for $normalized"
            )
        }
    }

    override suspend fun deleteDirectories(
        device: RemoteDevice,
        remoteRelativePaths: Collection<String>
    ): Result<FtpBatchOperationResult> = withFtpClient(device) { client ->
        runCatching {
            client.deletePaths(remoteRelativePaths) { path ->
                checkAction(removeDirectory(path), "FTP delete directory failed for $path")
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
                client.checkAction(
                    client.rename(normalizedFrom, normalizedTo),
                    "FTP rename failed from $normalizedFrom to $normalizedTo"
                )
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
            client.checkReply("FTP refused connection")

            client.checkAction(client.login(device.username, ""), "FTP login failed")

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
        checkAction(changeWorkingDirectory(mountRoot), "Cannot open $mountRoot")

        val relativeRoot = normalizeFtpRelativeRootPath(device.relativeRootPath)
        if (relativeRoot.isBlank()) return

        makeDirectories(relativeRoot)
        checkAction(changeWorkingDirectory(relativeRoot), "Cannot open ${device.workingRootPath}")
    }

    private fun FTPClient.makeDirectories(relativePath: String) {
        val original = printWorkingDirectory()
        relativePath
            .split('/')
            .filter { it.isNotBlank() }
            .forEach { part ->
                if (!changeWorkingDirectory(part)) {
                    makeDirectory(part)
                    checkAction(changeWorkingDirectory(part), "Cannot create FTP directory $part")
                }
            }
        if (original != null) {
            changeWorkingDirectory(original)
        }
    }

    private fun FTPClient.listSafeEntries(normalizedPath: String): List<FtpEntry> =
        listFiles(normalizedPath)
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

    private fun FTPClient.deletePaths(
        remoteRelativePaths: Collection<String>,
        deletePath: FTPClient.(String) -> Unit
    ): FtpBatchOperationResult {
        val successfulPaths = mutableListOf<String>()
        var firstError: Throwable? = null

        remoteRelativePaths.distinct().forEach { path ->
            val normalized = path.toSafeRelativeFtpPath()
            runCatching {
                deletePath(normalized)
            }.onSuccess {
                successfulPaths += normalized
            }.onFailure { error ->
                if (firstError == null) firstError = error
            }
        }

        return FtpBatchOperationResult(
            successfulPaths = successfulPaths,
            firstError = firstError
        )
    }

    private fun FTPClient.checkReply(message: String) {
        if (!FTPReply.isPositiveCompletion(replyCode)) {
            throw FtpReplyException(
                replyCode = replyCode,
                replyString = replyString ?: "",
                message = "$message: ${replyString?.trim()}"
            )
        }
    }

    private fun FTPClient.checkAction(success: Boolean, message: String) {
        if (!success) {
            throw FtpReplyException(
                replyCode = replyCode,
                replyString = replyString ?: "",
                message = "$message: ${replyString?.trim()}"
            )
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val DATA_TIMEOUT_MS = 30_000
        const val MAX_PARALLEL_LIST_CONNECTIONS = 4
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

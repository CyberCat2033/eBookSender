package com.cybercat.pocketbooksender.data.ftp

import com.cybercat.pocketbooksender.data.network.LocalDeviceNetworkProvider
import com.cybercat.pocketbooksender.model.PocketBookDevice
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply

class CommonsNetFtpGateway @Inject constructor(
    private val localDeviceNetworkProvider: LocalDeviceNetworkProvider,
) : FtpGateway {
    override suspend fun checkConnection(device: PocketBookDevice): Result<FtpSessionInfo> =
        withFtpClient(device) { client ->
            runCatching {
                client.listFiles()
                check(FTPReply.isPositiveCompletion(client.replyCode)) {
                    "Cannot read ${device.rootPath}: ${client.replyString}"
                }
                FtpSessionInfo(
                    rootPath = device.rootPath,
                    systemType = runCatching { client.systemType }.getOrNull(),
                )
            }
        }

    override suspend fun uploadAtomically(
        device: PocketBookDevice,
        remoteRelativePath: String,
        input: InputStream,
        onProgress: ((Long) -> Unit)?,
    ): Result<Unit> = withFtpClient(device) { client ->
        runCatching {
            val normalized = remoteRelativePath.trimStart('/')
            val directory = normalized.substringBeforeLast('/', missingDelimiterValue = "")
            val fileName = normalized.substringAfterLast('/')
            val tempName = ".$fileName.uploading"
            val tempPath = if (directory.isBlank()) tempName else "$directory/$tempName"

            if (directory.isNotBlank()) {
                client.makeDirectories(directory)
            }

            val wrappedInput = if (onProgress != null) {
                ProgressInputStream(input, onProgress)
            } else {
                input
            }

            wrappedInput.use { stream ->
                check(client.storeFile(tempPath, stream)) {
                    "FTP upload failed: ${client.replyString}"
                }
            }

            if (client.listFiles(normalized).isNotEmpty()) {
                client.deleteFile(normalized)
            }

            check(client.rename(tempPath, normalized)) {
                "FTP rename failed: ${client.replyString}"
            }
        }
    }

    override suspend fun listDirectories(
        device: PocketBookDevice,
        remoteRelativePath: String,
    ): Result<List<String>> = withFtpClient(device) { client ->
        runCatching {
            client.listFiles(remoteRelativePath.trimStart('/'))
                .filter(FTPFile::isDirectory)
                .map(FTPFile::getName)
                .filterNot { it == "." || it == ".." }
                .sorted()
        }
    }

    override suspend fun listEntries(
        device: PocketBookDevice,
        remoteRelativePath: String,
    ): Result<List<FtpEntry>> = withFtpClient(device) { client ->
        runCatching {
            val normalizedPath = remoteRelativePath.trim('/')
            client.listFiles(normalizedPath)
                .filterNot { it.name == "." || it.name == ".." }
                .map { file ->
                    val childPath = if (normalizedPath.isBlank()) {
                        file.name
                    } else {
                        "$normalizedPath/${file.name}"
                    }
                    FtpEntry(
                        name = file.name,
                        path = childPath,
                        isDirectory = file.isDirectory,
                        size = file.size,
                        modifiedAtMillis = file.timestamp?.timeInMillis,
                    )
                }
                .sortedWith(compareBy<FtpEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    override suspend fun downloadFile(
        device: PocketBookDevice,
        remoteRelativePath: String,
        output: OutputStream,
    ): Result<Unit> = withFtpClient(device) { client ->
        runCatching {
            val normalized = remoteRelativePath.trimStart('/')
            output.use { stream ->
                check(client.retrieveFile(normalized, stream)) {
                    "FTP download failed for $normalized: ${client.replyString}"
                }
            }
        }
    }

    override suspend fun deleteFile(
        device: PocketBookDevice,
        remoteRelativePath: String,
    ): Result<Unit> = withFtpClient(device) { client ->
        runCatching {
            val normalized = remoteRelativePath.toSafeRelativeFtpPath()
            check(client.deleteFile(normalized)) {
                "FTP delete file failed for $normalized: ${client.replyString}"
            }
        }
    }

    override suspend fun deleteDirectory(
        device: PocketBookDevice,
        remoteRelativePath: String,
    ): Result<Unit> = withFtpClient(device) { client ->
        runCatching {
            val normalized = remoteRelativePath.toSafeRelativeFtpPath()
            check(client.removeDirectory(normalized)) {
                "FTP delete directory failed for $normalized: ${client.replyString}"
            }
        }
    }

    override suspend fun rename(
        device: PocketBookDevice,
        fromPath: String,
        toPath: String,
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
        device: PocketBookDevice,
        block: (FTPClient) -> Result<T>,
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

            check(client.changeWorkingDirectory(device.rootPath)) {
                "Cannot open ${device.rootPath}: ${client.replyString}"
            }

            block(client)
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            if (client.isConnected) {
                runCatching { client.logout() }
                runCatching { client.disconnect() }
            }
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

private class ProgressInputStream(
    private val delegate: InputStream,
    private val onProgress: (Long) -> Unit
) : InputStream() {
    private var bytesRead = 0L
    private var lastUpdate = 0L

    private fun reportProgress(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || now - lastUpdate >= 100) { // Throttle updates to every 100ms
            onProgress(bytesRead)
            lastUpdate = now
        }
    }

    override fun read(): Int {
        val b = delegate.read()
        if (b != -1) {
            bytesRead++
            reportProgress()
        }
        return b
    }

    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val result = delegate.read(b, off, len)
        if (result != -1) {
            bytesRead += result
            reportProgress()
        }
        return result
    }

    override fun close() {
        delegate.close()
        reportProgress(force = true)
    }

    override fun available(): Int = delegate.available()
    override fun skip(n: Long): Long {
        val result = delegate.skip(n)
        if (result > 0) {
            bytesRead += result
            reportProgress()
        }
        return result
    }
}

private fun String.toSafeRelativeFtpPath(): String {
    val trimmed = replace('\\', '/').trim()
    require(trimmed.isNotBlank()) { "FTP path is empty" }
    require(!trimmed.startsWith("/")) { "FTP path must be relative" }

    val segments = trimmed
        .split('/')
        .filter { it.isNotBlank() }
    require(segments.none { it == "." || it == ".." }) { "FTP path must not traverse directories" }

    return segments.joinToString("/")
}

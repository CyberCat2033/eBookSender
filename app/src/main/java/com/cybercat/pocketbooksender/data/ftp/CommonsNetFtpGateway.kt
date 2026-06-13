package com.cybercat.pocketbooksender.data.ftp

import com.cybercat.pocketbooksender.model.PocketBookDevice
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply

class CommonsNetFtpGateway @Inject constructor() : FtpGateway {
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

            input.use { stream ->
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

    private suspend fun <T> withFtpClient(
        device: PocketBookDevice,
        block: (FTPClient) -> Result<T>,
    ): Result<T> = withContext(Dispatchers.IO) {
        val client = FTPClient()
        client.controlEncoding = Charsets.UTF_8.name()
        client.connectTimeout = CONNECT_TIMEOUT_MS
        client.setDataTimeout(DATA_TIMEOUT_MS)
        client.defaultTimeout = CONNECT_TIMEOUT_MS

        try {
            client.connect(device.host, device.port)
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

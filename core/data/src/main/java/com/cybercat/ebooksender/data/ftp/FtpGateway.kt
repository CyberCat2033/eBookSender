package com.cybercat.ebooksender.data.ftp

import com.cybercat.ebooksender.model.RemoteDevice
import java.io.InputStream
import java.io.OutputStream

interface FtpGateway {
    suspend fun checkConnection(device: RemoteDevice): Result<FtpSessionInfo>

    suspend fun uploadAtomically(
        device: RemoteDevice,
        remoteRelativePath: String,
        input: InputStream,
        onProgress: ((Long) -> Unit)? = null
    ): Result<Unit>

    suspend fun listDirectories(
        device: RemoteDevice,
        remoteRelativePath: String
    ): Result<List<String>>

    suspend fun listEntries(
        device: RemoteDevice,
        remoteRelativePath: String
    ): Result<List<FtpEntry>>

    suspend fun listEntries(
        device: RemoteDevice,
        remoteRelativePaths: Collection<String>
    ): Result<Map<String, List<FtpEntry>>> {
        val entriesByPath = mutableMapOf<String, List<FtpEntry>>()
        remoteRelativePaths.distinct().forEach { path ->
            listEntries(device, path)
                .onSuccess { entries -> entriesByPath[path] = entries }
                .onFailure { error -> return Result.failure(error) }
        }
        return Result.success(entriesByPath)
    }

    suspend fun downloadFile(
        device: RemoteDevice,
        remoteRelativePath: String,
        output: OutputStream
    ): Result<Unit>

    suspend fun deleteFile(device: RemoteDevice, remoteRelativePath: String): Result<Unit>

    suspend fun deleteFiles(
        device: RemoteDevice,
        remoteRelativePaths: Collection<String>
    ): Result<FtpBatchOperationResult> {
        val successfulPaths = mutableListOf<String>()
        var firstError: Throwable? = null
        remoteRelativePaths.distinct().forEach { path ->
            deleteFile(device, path)
                .onSuccess { successfulPaths += path }
                .onFailure { error ->
                    if (firstError == null) firstError = error
                }
        }
        return Result.success(
            FtpBatchOperationResult(
                successfulPaths = successfulPaths,
                firstError = firstError
            )
        )
    }

    suspend fun deleteDirectory(device: RemoteDevice, remoteRelativePath: String): Result<Unit>

    suspend fun deleteDirectories(
        device: RemoteDevice,
        remoteRelativePaths: Collection<String>
    ): Result<FtpBatchOperationResult> {
        val successfulPaths = mutableListOf<String>()
        var firstError: Throwable? = null
        remoteRelativePaths.distinct().forEach { path ->
            deleteDirectory(device, path)
                .onSuccess { successfulPaths += path }
                .onFailure { error ->
                    if (firstError == null) firstError = error
                }
        }
        return Result.success(
            FtpBatchOperationResult(
                successfulPaths = successfulPaths,
                firstError = firstError
            )
        )
    }

    suspend fun rename(device: RemoteDevice, fromPath: String, toPath: String): Result<Unit>
}

data class FtpSessionInfo(val rootPath: String, val systemType: String?)

data class FtpEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedAtMillis: Long?
)

data class FtpBatchOperationResult(val successfulPaths: List<String>, val firstError: Throwable?)

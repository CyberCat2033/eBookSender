package com.cybercat.pocketbooksender.data.ftp

import com.cybercat.pocketbooksender.model.PocketBookDevice
import java.io.InputStream
import java.io.OutputStream

interface FtpGateway {
    suspend fun checkConnection(device: PocketBookDevice): Result<FtpSessionInfo>

    suspend fun uploadAtomically(
        device: PocketBookDevice,
        remoteRelativePath: String,
        input: InputStream,
        onProgress: ((Long) -> Unit)? = null,
    ): Result<Unit>

    suspend fun listDirectories(
        device: PocketBookDevice,
        remoteRelativePath: String,
    ): Result<List<String>>

    suspend fun listEntries(
        device: PocketBookDevice,
        remoteRelativePath: String,
    ): Result<List<FtpEntry>>

    suspend fun downloadFile(
        device: PocketBookDevice,
        remoteRelativePath: String,
        output: OutputStream,
    ): Result<Unit>

    suspend fun deleteFile(
        device: PocketBookDevice,
        remoteRelativePath: String,
    ): Result<Unit>
}

data class FtpSessionInfo(
    val rootPath: String,
    val systemType: String?,
)

data class FtpEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedAtMillis: Long?,
)

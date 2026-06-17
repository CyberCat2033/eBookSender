package com.cybercat.pocketbooksender.data.ftp

import com.cybercat.pocketbooksender.model.RemoteDevice
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

    suspend fun downloadFile(
        device: RemoteDevice,
        remoteRelativePath: String,
        output: OutputStream
    ): Result<Unit>

    suspend fun deleteFile(device: RemoteDevice, remoteRelativePath: String): Result<Unit>

    suspend fun deleteDirectory(device: RemoteDevice, remoteRelativePath: String): Result<Unit>

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

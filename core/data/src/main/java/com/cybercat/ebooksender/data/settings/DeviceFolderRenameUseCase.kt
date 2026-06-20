package com.cybercat.ebooksender.data.settings

import com.cybercat.ebooksender.data.device.DeviceLibraryRefresher
import com.cybercat.ebooksender.data.ftp.FtpGateway
import com.cybercat.ebooksender.data.ftp.FtpReplyException
import com.cybercat.ebooksender.model.FolderRenameMethod
import com.cybercat.ebooksender.model.RemoteDevice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceFolderRenameUseCase @Inject constructor(
    private val ftpGateway: FtpGateway,
    private val deviceLibraryRefresher: DeviceLibraryRefresher
) {
    sealed class Result {
        object Success : Result()
        object AlreadyExists : Result()
        object NotSupported : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun renameFolder(device: RemoteDevice, oldName: String, newName: String): Result {
        if (device.supportsFolderRename == FolderRenameMethod.None) {
            return Result.NotSupported
        }

        val result = ftpGateway.rename(device, oldName, newName)
        return if (result.isSuccess) {
            deviceLibraryRefresher.refreshAndWait(device)
            Result.Success
        } else {
            val error = result.exceptionOrNull()
            if (isNotSupportedRenameError(error)) {
                Result.NotSupported
            } else if (isAlreadyExistsRenameError(error)) {
                Result.AlreadyExists
            } else {
                Result.Error(error?.localizedMessage ?: "unknown error")
            }
        }
    }

    private fun isNotSupportedRenameError(error: Throwable?): Boolean {
        val ftpException = error.findInCausalChain<FtpReplyException>()
        if (ftpException != null) {
            return ftpException.replyCode == 502 || ftpException.replyCode == 500
        }
        return false
    }

    private fun isAlreadyExistsRenameError(error: Throwable?): Boolean {
        val ftpException = error.findInCausalChain<FtpReplyException>()
        if (ftpException != null) {
            return ftpException.replyCode == 550
        }
        return false
    }

    private inline fun <reified T : Throwable> Throwable?.findInCausalChain(): T? {
        val seen = mutableSetOf<Throwable>()
        var current = this
        while (current != null && seen.add(current)) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }
}

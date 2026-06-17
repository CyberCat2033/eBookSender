package com.cybercat.pocketbooksender.data.settings

import com.cybercat.pocketbooksender.data.ftp.FtpGateway
import com.cybercat.pocketbooksender.data.pocketbook.PocketBookRescanCoordinator
import com.cybercat.pocketbooksender.model.PocketBookDevice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceFolderRenameUseCase @Inject constructor(
    private val ftpGateway: FtpGateway,
    private val rescanCoordinator: PocketBookRescanCoordinator
) {
    sealed class Result {
        object Success : Result()
        object AlreadyExists : Result()
        object NotSupported : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun renameFolder(
        device: PocketBookDevice,
        oldName: String,
        newName: String
    ): Result {
        val result = ftpGateway.rename(device, oldName, newName)
        return if (result.isSuccess) {
            rescanCoordinator.requestRescanAndWait(device)
            Result.Success
        } else {
            val error = result.exceptionOrNull()
            val errorMsg = error?.message.orEmpty()
            if (isNotSupportedRenameError(errorMsg)) {
                Result.NotSupported
            } else if (errorMsg.contains("550") || errorMsg.contains("exist", ignoreCase = true)) {
                Result.AlreadyExists
            } else {
                Result.Error(error?.localizedMessage ?: "unknown error")
            }
        }
    }

    private fun isNotSupportedRenameError(errorMsg: String): Boolean {
        val msg = errorMsg.lowercase()
        return msg.contains("502") ||
            msg.contains("500") ||
            msg.contains("not implemented") ||
            msg.contains("not supported") ||
            msg.contains("command not implemented")
    }
}

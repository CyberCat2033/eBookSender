package com.cybercat.pocketbooksender.data.settings

import com.cybercat.pocketbooksender.data.ftp.FtpGateway
import com.cybercat.pocketbooksender.data.pocketbook.PocketBookRescanCoordinator
import com.cybercat.pocketbooksender.model.FolderRenameMethod
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
        if (device.supportsFolderRename == FolderRenameMethod.None) {
            return Result.NotSupported
        }

        val result = ftpGateway.rename(device, oldName, newName)
        return if (result.isSuccess) {
            if (device.supportsRescan) {
                rescanCoordinator.requestRescanAndWait(device)
            }
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
        return error.messagesInCausalChain().any { message ->
            val unsupportedCommand =
                message.contains("not implemented") ||
                    message.contains("not supported") ||
                    message.contains("unsupported") ||
                    message.contains("command not implemented")
            val serverCommandFailure = message.contains("500") && message.contains("command")

            message.contains("502") || unsupportedCommand || serverCommandFailure
        }
    }

    private fun isAlreadyExistsRenameError(error: Throwable?): Boolean {
        return error.messagesInCausalChain().any { message ->
            message.contains("550") || message.contains("exist")
        }
    }

    private fun Throwable?.messagesInCausalChain(): List<String> = buildList {
        val seen = mutableSetOf<Throwable>()
        var current = this@messagesInCausalChain
        while (current != null && seen.add(current)) {
            current.message?.lowercase()?.let(::add)
            current = current.cause
        }
    }
}

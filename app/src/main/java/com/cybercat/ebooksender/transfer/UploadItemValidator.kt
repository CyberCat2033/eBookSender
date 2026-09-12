package com.cybercat.ebooksender.transfer

import com.cybercat.ebooksender.data.transfer.SkippedUploadFile
import com.cybercat.ebooksender.data.transfer.UploadFileSkipReason
import com.cybercat.ebooksender.domain.AllSupportedExtensions
import com.cybercat.ebooksender.domain.contentExtension
import javax.inject.Inject

class UploadItemValidator @Inject constructor() {
    fun validate(
        uriString: String,
        displayName: String,
        fileSize: Long,
        existingIdentityKeys: Set<String>,
        maxFileSizeBytes: Long
    ): Result {
        if (uriString in existingIdentityKeys) return Result.Duplicate

        val isSupported = displayName.contentExtension() in AllSupportedExtensions

        return when {
            !isSupported -> Result.Skipped(
                SkippedUploadFile(
                    displayName = displayName,
                    reason = UploadFileSkipReason.UnsupportedFormat
                )
            )

            fileSize > maxFileSizeBytes -> Result.Skipped(
                SkippedUploadFile(
                    displayName = displayName,
                    reason = UploadFileSkipReason.TooLarge
                )
            )

            else -> Result.Accepted(displayName)
        }
    }

    sealed interface Result {
        data class Accepted(val displayName: String) : Result
        data object Duplicate : Result
        data class Skipped(val file: SkippedUploadFile) : Result
    }
}

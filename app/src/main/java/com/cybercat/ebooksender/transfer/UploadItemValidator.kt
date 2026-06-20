package com.cybercat.ebooksender.transfer

import android.net.Uri
import com.cybercat.ebooksender.data.transfer.SkippedUploadFile
import com.cybercat.ebooksender.data.transfer.UploadFileSkipReason
import com.cybercat.ebooksender.domain.AllSupportedExtensions
import com.cybercat.ebooksender.domain.bookExtension
import java.util.UUID
import javax.inject.Inject

class UploadItemValidator @Inject constructor(private val localFileResolver: LocalFileResolver) {
    fun validate(uri: Uri, existingIdentityKeys: Set<String>, maxFileSizeBytes: Long): Result {
        val uriString = uri.toString()
        if (uriString in existingIdentityKeys) return Result.Duplicate

        val displayName = localFileResolver.resolveDisplayName(uri)
            ?: uri.lastPathSegment
            ?: "Book-${UUID.randomUUID()}"
        val extension = displayName.bookExtension().lowercase().trim()
        val isSupported = extension in AllSupportedExtensions ||
            (
                extension.endsWith(".zip") &&
                    extension.removeSuffix(".zip") in AllSupportedExtensions
                )
        val fileSize = localFileResolver.resolveFileSize(uri)

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

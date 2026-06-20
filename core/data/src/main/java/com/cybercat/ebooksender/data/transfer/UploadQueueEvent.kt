package com.cybercat.ebooksender.data.transfer

sealed interface UploadQueueEvent

data class UploadFilesSkipped(val files: List<SkippedUploadFile>, val maxFileSizeMb: Int) :
    UploadQueueEvent

data class SkippedUploadFile(val displayName: String, val reason: UploadFileSkipReason)

enum class UploadFileSkipReason {
    UnsupportedFormat,
    TooLarge
}

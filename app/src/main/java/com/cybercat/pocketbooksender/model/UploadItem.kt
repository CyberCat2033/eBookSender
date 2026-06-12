package com.cybercat.pocketbooksender.model

import android.graphics.Bitmap

data class UploadItem(
    val id: String,
    val sourceUri: String,
    val originalName: String,
    val extension: String,
    val category: BookCategory,
    val title: String,
    val author: String? = null,
    val programmingTag: String? = null,
    val mangaSeries: String? = null,
    val mangaVolume: String? = null,
    val coverUri: String? = null,
    val preview: Bitmap? = null,
    val plannedPath: String,
    val status: UploadStatus = UploadStatus.Pending,
    val progress: Float = 0f,
)

enum class UploadStatus {
    Pending,
    Preparing,
    Uploading,
    Uploaded,
    Failed,
    Skipped,
}

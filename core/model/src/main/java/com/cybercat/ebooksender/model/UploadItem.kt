package com.cybercat.ebooksender.model

data class UploadItem(
    val id: String,
    val sourceUri: String,
    val originalName: String,
    val extension: String,
    val category: BookCategory,
    val title: String,
    val author: String? = null,
    val documentsTag: String? = null,
    val mangaSeries: String? = null,
    val mangaVolume: String? = null,
    val year: String? = null,
    val language: String? = null,
    val series: String? = null,
    val seriesIndex: String? = null,
    val publisher: String? = null,
    val coverUri: String? = null,
    val plannedPath: String,
    val status: UploadStatus = UploadStatus.Pending,
    val progress: Float = 0f
)

enum class UploadStatus {
    Pending,
    Preparing,
    Uploading,
    Uploaded,
    Failed,
    Skipped
}

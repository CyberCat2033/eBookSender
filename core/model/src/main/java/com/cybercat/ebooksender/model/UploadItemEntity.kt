package com.cybercat.ebooksender.model

import kotlinx.serialization.Serializable

@Serializable
data class UploadItemEntity(
    val id: String? = null,
    val sourceUri: String? = null,
    val originalName: String? = null,
    val extension: String? = null,
    val category: String? = null,
    val title: String? = null,
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
    val plannedPath: String? = null,
    val status: String? = null,
)

fun UploadItem.toEntity(): UploadItemEntity =
    UploadItemEntity(
        id = id,
        sourceUri = sourceUri,
        originalName = originalName,
        extension = extension,
        category = category.name,
        title = title,
        author = author,
        documentsTag = documentsTag,
        mangaSeries = mangaSeries,
        mangaVolume = mangaVolume,
        year = year,
        language = language,
        series = series,
        seriesIndex = seriesIndex,
        publisher = publisher,
        coverUri = coverUri,
        plannedPath = plannedPath,
        status = status.name,
    )

fun UploadItemEntity.toDomain(
    fallbackId: () -> String,
    fallbackCategory: (String) -> BookCategory,
    fallbackExtension: (String) -> String,
    fallbackTitle: (String) -> String,
): UploadItem? {
    val sourceUri = sourceUri ?: return null
    val originalName = originalName ?: return null
    val category = enumValueOrNull<BookCategory>(category) ?: fallbackCategory(originalName)
    val status = enumValueOrNull<UploadStatus>(status) ?: UploadStatus.Pending

    return UploadItem(
        id = id ?: fallbackId(),
        sourceUri = sourceUri,
        originalName = originalName,
        extension = extension ?: fallbackExtension(originalName),
        category = category,
        title = title ?: fallbackTitle(originalName),
        author = author,
        documentsTag = documentsTag,
        mangaSeries = mangaSeries,
        mangaVolume = mangaVolume,
        year = year,
        language = language,
        series = series,
        seriesIndex = seriesIndex,
        publisher = publisher,
        coverUri = coverUri,
        plannedPath = plannedPath.orEmpty(),
        status = status,
        progress = 0f,
    )
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? =
    value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

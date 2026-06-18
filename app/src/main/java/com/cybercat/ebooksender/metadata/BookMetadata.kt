package com.cybercat.ebooksender.metadata

import android.graphics.Bitmap

data class BookMetadata(
    val title: String,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val coverUri: String? = null,
    val preview: Bitmap? = null,
    val series: String? = null,
    val seriesIndex: String? = null,
    val language: String? = null,
    val year: String? = null,
    val publisher: String? = null,
)

interface MetadataExtractor {
    suspend fun extract(sourceUri: String, displayName: String): BookMetadata
}

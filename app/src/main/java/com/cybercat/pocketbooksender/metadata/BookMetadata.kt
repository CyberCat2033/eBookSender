package com.cybercat.pocketbooksender.metadata

import android.graphics.Bitmap

data class BookMetadata(
    val title: String,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val coverUri: String? = null,
    val preview: Bitmap? = null,
    val series: String? = null,
    val language: String? = null,
)

interface MetadataExtractor {
    suspend fun extract(sourceUri: String, displayName: String): BookMetadata
}

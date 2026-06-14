package com.cybercat.pocketbooksender.ui

import com.cybercat.pocketbooksender.data.manga.MangaChapter
import com.cybercat.pocketbooksender.data.manga.MangaSeriesDetails

internal fun MangaChapter.subscriptionUpdateSelectionKey(): String =
    stableSelectionKey(sourceId, seriesId, stableKey.ifBlank { chapterId })

internal fun MangaSeriesDetails.subscriptionUpdateSeriesKey(): String =
    stableSelectionKey(sourceId, seriesId)

private fun stableSelectionKey(vararg parts: String): String =
    buildString {
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                append('|')
            }
            append(part.length)
            append(':')
            append(part)
        }
    }

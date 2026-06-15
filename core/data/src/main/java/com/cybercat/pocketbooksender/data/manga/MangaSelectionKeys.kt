package com.cybercat.pocketbooksender.data.manga

fun MangaChapter.mangaStableSelectionKey(): String =
    stableMangaSelectionKey(sourceId, seriesId, stableKey.ifBlank { chapterId })

fun MangaSeriesDetails.mangaStableSelectionKey(): String =
    stableMangaSelectionKey(sourceId, seriesId)

private fun stableMangaSelectionKey(vararg parts: String): String =
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

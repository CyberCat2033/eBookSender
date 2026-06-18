package com.cybercat.pocketbooksender.data.manga

interface MangaSeriesPageLoader {
    suspend fun openSeries(sourceId: String, seriesId: String): MangaSeriesPage
}

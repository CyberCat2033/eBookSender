package com.cybercat.ebooksender.data.manga

interface MangaSeriesPageLoader {
    suspend fun openSeries(sourceId: String, seriesId: String): MangaSeriesPage
}

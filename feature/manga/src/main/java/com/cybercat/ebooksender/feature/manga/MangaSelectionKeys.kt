package com.cybercat.ebooksender.feature.manga

import com.cybercat.ebooksender.data.manga.MangaChapter
import com.cybercat.ebooksender.data.manga.MangaSeriesDetails
import com.cybercat.ebooksender.data.manga.mangaStableSelectionKey

internal fun MangaChapter.subscriptionUpdateSelectionKey(): String =
    mangaStableSelectionKey()

internal fun MangaSeriesDetails.subscriptionUpdateSeriesKey(): String =
    mangaStableSelectionKey()

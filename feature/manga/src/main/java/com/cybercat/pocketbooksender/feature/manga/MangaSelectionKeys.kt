package com.cybercat.pocketbooksender.feature.manga

import com.cybercat.pocketbooksender.data.manga.MangaChapter
import com.cybercat.pocketbooksender.data.manga.MangaSeriesDetails
import com.cybercat.pocketbooksender.data.manga.mangaStableSelectionKey

internal fun MangaChapter.subscriptionUpdateSelectionKey(): String =
    mangaStableSelectionKey()

internal fun MangaSeriesDetails.subscriptionUpdateSeriesKey(): String =
    mangaStableSelectionKey()

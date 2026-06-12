package com.cybercat.pocketbooksender.ui

import com.cybercat.pocketbooksender.data.manga.ComxMangaAdapter
import com.cybercat.pocketbooksender.data.manga.MangaChapter
import com.cybercat.pocketbooksender.data.manga.MangaSeriesDetails
import com.cybercat.pocketbooksender.data.manga.MangaSeriesSearchResult
import com.cybercat.pocketbooksender.data.manga.MangaSourceSummary

data class MangaUiState(
    val sources: List<MangaSourceSummary> = emptyList(),
    val selectedSourceId: String = ComxMangaAdapter.SourceId,
    val searchInput: String = "",
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgressText: String? = null,
    val browserVisible: Boolean = false,
    val browserUrl: String = ComxMangaAdapter.HomeUrl,
    val currentWebUrl: String? = null,
    val searchResults: List<MangaSeriesSearchResult> = emptyList(),
    val selectedSeries: MangaSeriesDetails? = null,
    val chapters: List<MangaChapter> = emptyList(),
    val selectedChapterIds: Set<String> = emptySet(),
    val downloadedStableKeys: Set<String> = emptySet(),
    val statusMessage: String? = null,
    val errorMessage: String? = null,
) {
    val selectedChapters: List<MangaChapter> =
        chapters.filter { chapter -> chapter.chapterId in selectedChapterIds }

    val hasNewChapters: Boolean =
        chapters.any { chapter -> chapter.stableKey !in downloadedStableKeys }
}

package com.cybercat.pocketbooksender.feature.manga

import com.cybercat.pocketbooksender.data.manga.MangaChapter
import com.cybercat.pocketbooksender.data.manga.MangaChapterDownload
import com.cybercat.pocketbooksender.data.manga.MangaSeriesBookmark
import com.cybercat.pocketbooksender.data.manga.MangaSeriesDetails
import com.cybercat.pocketbooksender.data.manga.MangaSeriesSearchResult
import com.cybercat.pocketbooksender.data.manga.MangaSourceSummary
import com.cybercat.pocketbooksender.data.manga.MangaSubscriptionCheckResult

data class MangaUiState(
    val sources: List<MangaSourceSummary> = emptyList(),
    val selectedSourceId: String = "",
    val searchInput: String = "",
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: MangaDownloadUiProgress? = null,
    val browserVisible: Boolean = false,
    val browserUrl: String = "",
    val currentWebUrl: String? = null,
    val searchResults: List<MangaSeriesSearchResult> = emptyList(),
    val selectedSeries: MangaSeriesDetails? = null,
    val selectedSeriesScrollRequest: Long = 0L,
    val chapters: List<MangaChapter> = emptyList(),
    val selectedChapterIds: Set<String> = emptySet(),
    val downloadedStableKeys: Set<String> = emptySet(),
    val downloadedChapters: List<MangaChapterDownload> = emptyList(),
    val savedSeries: List<MangaSeriesBookmark> = emptyList(),
    val isCheckingSubscriptions: Boolean = false,
    val subscriptionUpdates: List<MangaSubscriptionCheckResult> = emptyList(),
    val subscriptionUpdatesVisible: Boolean = false,
    val selectedSubscriptionUpdateChapterKeys: Set<String> = emptySet(),
    val lastReadChapterText: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val isAuthorized: Boolean = false,
    val pendingLoginPost: MangaPendingLoginPost? = null,
) {
    val selectedChapters: List<MangaChapter> =
        chapters.filter { chapter -> chapter.chapterId in selectedChapterIds }

    val hasNewChapters: Boolean =
        chapters.any { chapter -> chapter.stableKey !in downloadedStableKeys }
}

data class MangaDownloadUiProgress(
    val title: String,
    val detail: String,
    val currentChapterTitle: String?,
    val progress: Float?,
)

data class MangaPendingLoginPost(
    val url: String,
    val postBody: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MangaPendingLoginPost
        if (url != other.url) return false
        if (!postBody.contentEquals(other.postBody)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + postBody.contentHashCode()
        return result
    }
}

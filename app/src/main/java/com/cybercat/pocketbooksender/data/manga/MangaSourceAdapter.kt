package com.cybercat.pocketbooksender.data.manga

import java.io.File

interface MangaSourceAdapter {
    val id: String
    val title: String
    val capabilities: MangaSourceCapabilities

    suspend fun authState(): MangaAuthState
    suspend fun searchSeries(query: String): List<MangaSeriesSearchResult>
    suspend fun getSeries(seriesId: String): MangaSeriesDetails
    suspend fun listChapters(seriesId: String): List<MangaChapter>
    suspend fun getSeriesPage(seriesId: String): MangaSeriesPage =
        MangaSeriesPage(
            details = getSeries(seriesId),
            chapters = listChapters(seriesId),
        )
    suspend fun getChapterPages(chapterId: String): List<MangaPage>
    suspend fun downloadPage(page: MangaPage): MangaDownloadedPage
    suspend fun downloadChapterArchive(
        chapter: MangaChapter,
        outputFile: File,
    ): MangaDownloadedArchive? = null
}

interface HtmlMangaSourceAdapter : MangaSourceAdapter {
    val homeUrl: String

    fun buildSearchUrl(query: String): String
    fun ownsUrl(url: String): Boolean
    fun parseSearchResults(url: String, html: String): List<MangaSeriesSearchResult>
    fun parseSeriesPage(url: String, html: String): MangaSeriesPage?
    fun parseChapterPages(url: String, html: String): List<MangaPage>
}

data class MangaSourceCapabilities(
    val authMode: MangaAuthMode,
    val supportsSearch: Boolean = true,
    val supportsChapterRanges: Boolean = true,
)

enum class MangaAuthMode {
    None,
    WebLogin,
    Cookie,
}

sealed interface MangaAuthState {
    data object NotRequired : MangaAuthState
    data object Required : MangaAuthState
    data class Authenticated(
        val accountLabel: String? = null,
        val expiresAtMillis: Long? = null,
    ) : MangaAuthState
    data class Failed(
        val message: String,
    ) : MangaAuthState
}

data class MangaLoginRequest(
    val sourceId: String,
    val loginUrl: String,
    val successUrlPattern: String?,
)

data class MangaSeriesSearchResult(
    val sourceId: String,
    val seriesId: String,
    val title: String,
    val coverUrl: String?,
    val subtitle: String? = null,
)

data class MangaSeriesDetails(
    val sourceId: String,
    val seriesId: String,
    val title: String,
    val coverUrl: String?,
    val description: String?,
)

data class MangaChapter(
    val sourceId: String,
    val seriesId: String,
    val chapterId: String,
    val stableKey: String,
    val title: String,
    val numberForSort: Double?,
    val publishedAtMillis: Long?,
    val downloadUrl: String? = null,
)

data class MangaPage(
    val index: Int,
    val imageUrl: String,
    val refererUrl: String?,
    val fileExtension: String?,
)

data class MangaDownloadedPage(
    val bytes: ByteArray,
    val fileExtension: String,
)

data class MangaDownloadedArchive(
    val fileExtension: String,
)

data class MangaSeriesPage(
    val details: MangaSeriesDetails,
    val chapters: List<MangaChapter>,
)

data class MangaSourceSummary(
    val id: String,
    val title: String,
    val homeUrl: String,
)

data class MangaSeriesBookmark(
    val sourceId: String,
    val seriesId: String,
    val title: String,
    val coverUrl: String?,
    val description: String?,
    val favorite: Boolean,
    val subscribed: Boolean,
    val addedAtMillis: Long,
    val lastOpenedAtMillis: Long,
    val lastCheckedAtMillis: Long?,
)

data class MangaChapterDownload(
    val sourceId: String,
    val seriesId: String,
    val stableKey: String,
    val seriesTitle: String,
    val chapterTitle: String,
    val fileName: String,
    val downloadedAtMillis: Long,
)

data class MangaSubscriptionCheckResult(
    val page: MangaSeriesPage,
    val newChapters: List<MangaChapter>,
)

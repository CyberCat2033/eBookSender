package com.cybercat.pocketbooksender.data.manga

import com.cybercat.pocketbooksender.data.database.dao.MangaChapterHistoryDao
import com.cybercat.pocketbooksender.data.database.dao.MangaSeriesBookmarkDao
import com.cybercat.pocketbooksender.data.database.entity.MangaChapterHistoryEntity
import com.cybercat.pocketbooksender.data.database.entity.MangaSeriesBookmarkEntity
import com.cybercat.pocketbooksender.util.TimedCacheEntry
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class MangaRepository @Inject constructor(
    private val historyDao: MangaChapterHistoryDao,
    private val bookmarkDao: MangaSeriesBookmarkDao,
    private val sourceRegistry: MangaSourceRegistry,
    private val chapterDownloader: MangaChapterDownloader
) {
    private val searchCache =
        ConcurrentHashMap<String, TimedCacheEntry<List<MangaSeriesSearchResult>>>()
    private val seriesCache = ConcurrentHashMap<String, TimedCacheEntry<MangaSeriesPage>>()

    val sources: List<MangaSourceSummary> = sourceRegistry.sources

    val downloadedStableKeys: Flow<Set<String>> =
        historyDao.observeDownloadedStableKeys().map { keys -> keys.toSet() }

    val downloadedChapters: Flow<List<MangaChapterDownload>> =
        historyDao.observeHistory().map { items ->
            items.map { item -> item.toDownload() }
        }

    val savedSeries: Flow<List<MangaSeriesBookmark>> =
        bookmarkDao.observeSavedSeries()
            .map { items ->
                items.map { item -> item.toBookmark() }
            }

    suspend fun normalizeFavoriteSubscribedState() = withContext(Dispatchers.IO) {
        bookmarkDao.normalizeMutualExclusion()
    }

    suspend fun clearSavedSeries(): Boolean = withContext(Dispatchers.IO) {
        bookmarkDao.clearSavedSeries() > 0
    }

    suspend fun hasSavedSeries(): Boolean = withContext(Dispatchers.IO) {
        bookmarkDao.savedSeriesCount() > 0
    }

    suspend fun authState(sourceId: String): MangaAuthState = adapter(sourceId).authState()

    fun homeUrl(sourceId: String): String = sourceRegistry.homeUrl(sourceId)

    fun buildSearchUrl(sourceId: String, query: String): String =
        adapter(sourceId).buildSearchUrl(query)

    fun buildLoginPostBody(
        sourceId: String,
        username: String,
        password: String,
        doNotRemember: Boolean
    ): ByteArray? = adapter(sourceId).buildLoginPostBody(username, password, doNotRemember)

    suspend fun searchSeries(sourceId: String, query: String): List<MangaSeriesSearchResult> {
        val key = "$sourceId:${query.trim().lowercase()}"
        searchCache[key]?.takeIf { entry -> entry.isFresh(SEARCH_CACHE_TTL_MILLIS) }?.let { entry ->
            return entry.value
        }

        val results = adapter(sourceId).searchSeries(query)
        searchCache[key] = TimedCacheEntry(results)
        return results
    }

    suspend fun openSeries(sourceId: String, seriesId: String): MangaSeriesPage {
        val key = "$sourceId:${seriesId.normalizeCacheKey()}"
        seriesCache[key]?.takeIf { entry -> entry.isFresh(SERIES_CACHE_TTL_MILLIS) }?.let { entry ->
            saveSeriesSnapshot(entry.value.details)
            return entry.value
        }

        val page = runCatching {
            adapter(sourceId).getSeriesPage(seriesId)
        }.getOrElse { error ->
            recoverMovedSavedSeries(sourceId, seriesId, error) ?: throw error
        }
        seriesCache[key] = TimedCacheEntry(page)
        saveSeriesSnapshot(page.details)
        return page
    }

    fun parseWebPage(sourceId: String, url: String, html: String): MangaParsedPage {
        val adapter = adapter(sourceId)
        if (!adapter.ownsUrl(url)) return MangaParsedPage.Unsupported

        val seriesPage = runCatching { adapter.parseSeriesPage(url, html) }.getOrNull()
        if (seriesPage != null && seriesPage.chapters.isNotEmpty()) {
            return MangaParsedPage.Series(seriesPage)
        }

        val searchResults = runCatching {
            adapter.parseSearchResults(url, html)
        }.getOrDefault(emptyList())
        if (searchResults.isNotEmpty()) {
            return MangaParsedPage.SearchResults(searchResults)
        }

        val pages = runCatching { adapter.parseChapterPages(url, html) }.getOrDefault(emptyList())
        if (pages.isNotEmpty()) {
            return MangaParsedPage.ChapterPages(pages)
        }

        return MangaParsedPage.Unsupported
    }

    suspend fun saveSeriesSnapshot(series: MangaSeriesDetails) = withContext(Dispatchers.IO) {
        bookmarkDao.upsertSnapshot(
            sourceId = series.sourceId,
            seriesId = series.seriesId,
            title = series.title,
            coverUrl = series.coverUrl,
            description = series.description,
            openedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun setFavorite(series: MangaSeriesDetails, favorite: Boolean) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val updated = bookmarkDao.setFavorite(
                sourceId = series.sourceId,
                seriesId = series.seriesId,
                title = series.title,
                coverUrl = series.coverUrl,
                description = series.description,
                favorite = favorite,
                updatedAtMillis = now
            )
            if (updated == 0) {
                bookmarkDao.upsert(
                    series.toBookmarkEntity(favorite = favorite, subscribed = false, now = now)
                )
            }
        }

    suspend fun setSubscribed(series: MangaSeriesDetails, subscribed: Boolean) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val updated = bookmarkDao.setSubscribed(
                sourceId = series.sourceId,
                seriesId = series.seriesId,
                title = series.title,
                coverUrl = series.coverUrl,
                description = series.description,
                subscribed = subscribed,
                updatedAtMillis = now
            )
            if (updated == 0) {
                bookmarkDao.upsert(
                    series.toBookmarkEntity(favorite = false, subscribed = subscribed, now = now)
                )
            }
        }

    suspend fun checkSubscriptions(): List<MangaSubscriptionCheckResult> =
        withContext(Dispatchers.IO) {
            val downloadedStableKeys = historyDao.downloadedStableKeys().toSet()
            val checkedAtMillis = System.currentTimeMillis()
            bookmarkDao.subscribedSeries().mapNotNull { saved ->
                runCatching {
                    val page = openSeries(saved.sourceId, saved.seriesId)
                    bookmarkDao.markChecked(
                        page.details.sourceId,
                        page.details.seriesId,
                        checkedAtMillis
                    )
                    val newChapters = page.chapters.filter { chapter ->
                        chapter.stableKey !in downloadedStableKeys
                    }
                    MangaSubscriptionCheckResult(page = page, newChapters = newChapters)
                }.getOrElse { error ->
                    if (error is MangaAuthenticationExpiredException) {
                        throw error
                    }
                    null
                }
            }
        }

    suspend fun downloadMultipleSeriesChapters(
        targets: List<MangaChapterDownloadTarget>,
        onProgress: suspend (MangaDownloadProgress) -> Unit = {}
    ): MangaDownloadResult = withContext(Dispatchers.IO) {
        val batch = chapterDownloader.download(
            targets = targets,
            onProgress = onProgress
        )
        if (batch.historyItems.isNotEmpty()) {
            historyDao.upsertAll(batch.historyItems)
        }

        MangaDownloadResult(
            downloaded = batch.downloaded,
            failedMessages = batch.failedMessages
        )
    }

    suspend fun downloadChapters(
        sourceId: String,
        series: MangaSeriesDetails,
        chapters: List<MangaChapter>,
        onProgress: suspend (MangaDownloadProgress) -> Unit = {}
    ): MangaDownloadResult {
        require(sourceId == series.sourceId) {
            "Manga series source does not match selected source"
        }
        val targets = chapters.map { MangaChapterDownloadTarget(series, it) }
        return downloadMultipleSeriesChapters(targets, onProgress)
    }

    private suspend fun recoverMovedSavedSeries(
        sourceId: String,
        seriesId: String,
        error: Throwable
    ): MangaSeriesPage? {
        if (!error.isHttpNotFound()) return null

        val saved = bookmarkDao.findSeries(sourceId, seriesId) ?: return null
        val candidates = runCatching {
            searchSeries(sourceId, saved.title)
        }.getOrDefault(emptyList())
            .rankForSavedTitle(saved.title)
            .filterNot { result ->
                result.seriesId.normalizeCacheKey() ==
                    seriesId.normalizeCacheKey()
            }
            .take(MAX_MOVED_SERIES_CANDIDATES)

        candidates.forEach { candidate ->
            val page = runCatching {
                adapter(sourceId).getSeriesPage(candidate.seriesId)
            }.getOrNull() ?: return@forEach

            replaceSavedSeriesId(saved, page.details)
            return page
        }

        return null
    }

    private suspend fun replaceSavedSeriesId(
        saved: MangaSeriesBookmarkEntity,
        details: MangaSeriesDetails
    ) {
        val now = System.currentTimeMillis()
        val existing = bookmarkDao.findSeries(details.sourceId, details.seriesId)
        val replacement = MangaSeriesBookmarkEntity(
            sourceId = details.sourceId,
            seriesId = details.seriesId,
            title = details.title,
            coverUrl = details.coverUrl,
            description = details.description,
            favorite = saved.favorite || existing?.favorite == true,
            subscribed = saved.subscribed || existing?.subscribed == true,
            addedAtMillis = minOf(
                saved.addedAtMillis,
                existing?.addedAtMillis ?: saved.addedAtMillis
            ),
            lastOpenedAtMillis = now,
            lastCheckedAtMillis = existing?.lastCheckedAtMillis ?: saved.lastCheckedAtMillis
        )
        bookmarkDao.upsert(replacement)
        if (saved.seriesId.normalizeCacheKey() != details.seriesId.normalizeCacheKey()) {
            bookmarkDao.deleteSeries(saved.sourceId, saved.seriesId)
        }
    }

    private fun adapter(sourceId: String): HtmlMangaSourceAdapter = sourceRegistry.adapter(sourceId)

    private companion object {
        const val SEARCH_CACHE_TTL_MILLIS = 5 * 60 * 1000L
        const val SERIES_CACHE_TTL_MILLIS = 2 * 60 * 1000L
        const val MAX_MOVED_SERIES_CANDIDATES = 5
    }
}

sealed interface MangaParsedPage {
    data class SearchResults(val results: List<MangaSeriesSearchResult>) : MangaParsedPage
    data class Series(val page: MangaSeriesPage) : MangaParsedPage
    data class ChapterPages(val pages: List<MangaPage>) : MangaParsedPage
    data object Unsupported : MangaParsedPage
}

private fun MangaChapterHistoryEntity.toDownload(): MangaChapterDownload = MangaChapterDownload(
    sourceId = sourceId,
    seriesId = seriesId,
    stableKey = stableKey,
    seriesTitle = seriesTitle,
    chapterTitle = chapterTitle,
    fileName = fileName,
    downloadedAtMillis = downloadedAtMillis
)

private fun MangaSeriesBookmarkEntity.toBookmark(): MangaSeriesBookmark = MangaSeriesBookmark(
    sourceId = sourceId,
    seriesId = seriesId,
    title = title,
    coverUrl = coverUrl,
    description = description,
    favorite = favorite,
    subscribed = subscribed,
    addedAtMillis = addedAtMillis,
    lastOpenedAtMillis = lastOpenedAtMillis,
    lastCheckedAtMillis = lastCheckedAtMillis
)

private fun MangaSeriesDetails.toBookmarkEntity(
    favorite: Boolean,
    subscribed: Boolean,
    now: Long
): MangaSeriesBookmarkEntity = MangaSeriesBookmarkEntity(
    sourceId = sourceId,
    seriesId = seriesId,
    title = title,
    coverUrl = coverUrl,
    description = description,
    favorite = favorite,
    subscribed = subscribed,
    addedAtMillis = now,
    lastOpenedAtMillis = now,
    lastCheckedAtMillis = null
)

private fun String.normalizeCacheKey(): String = trim().trimEnd('/').lowercase()

private fun Throwable.isHttpNotFound(): Boolean =
    message?.contains("HTTP 404", ignoreCase = true) == true

private fun List<MangaSeriesSearchResult>.rankForSavedTitle(
    savedTitle: String
): List<MangaSeriesSearchResult> {
    val savedKey = savedTitle.mangaTitleMatchKey()
    if (savedKey.isBlank()) return emptyList()

    return map { result ->
        result to result.title.mangaTitleMatchScore(savedKey)
    }
        .filter { (_, score) -> score > 0 }
        .sortedWith(
            compareByDescending<Pair<MangaSeriesSearchResult, Int>> { (_, score) -> score }
                .thenBy { (result, _) -> result.title.length }
        )
        .map { (result, _) -> result }
        .distinctBy { result -> result.seriesId.normalizeCacheKey() }
}

private fun String.mangaTitleMatchScore(savedKey: String): Int {
    val resultKey = mangaTitleMatchKey()
    if (resultKey.isBlank()) return 0
    return when {
        resultKey == savedKey -> 4
        resultKey.contains(savedKey) -> 3
        savedKey.contains(resultKey) -> 2
        else -> 0
    }
}

private fun String.mangaTitleMatchKey(): String = lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")

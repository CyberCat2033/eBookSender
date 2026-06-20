package com.cybercat.ebooksender.data.manga

import com.cybercat.ebooksender.data.database.dao.MangaChapterHistoryDao
import com.cybercat.ebooksender.data.database.dao.MangaSeriesBookmarkDao
import com.cybercat.ebooksender.data.database.entity.MangaChapterHistoryEntity
import com.cybercat.ebooksender.data.database.entity.MangaSeriesBookmarkEntity
import com.cybercat.ebooksender.util.ExpiringLruCache
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class MangaRepository @Inject constructor(
    private val historyDao: MangaChapterHistoryDao,
    private val bookmarkDao: MangaSeriesBookmarkDao,
    private val sourceRegistry: MangaSourceRegistry,
    private val chapterDownloader: MangaChapterDownloader
) : MangaSeriesPageLoader {
    private val searchCache = ExpiringLruCache<String, List<MangaSeriesSearchResult>>(
        ttlMillis = SEARCH_CACHE_TTL_MILLIS,
        maxSize = SEARCH_CACHE_MAX_ENTRIES
    )
    private val seriesCache = ExpiringLruCache<String, MangaSeriesPage>(
        ttlMillis = SERIES_CACHE_TTL_MILLIS,
        maxSize = SERIES_CACHE_MAX_ENTRIES
    )

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
        searchCache.get(key)?.let { cachedResults ->
            return cachedResults
        }

        val results = adapter(sourceId).searchSeries(query)
        searchCache.put(key, results)
        return results
    }

    override suspend fun openSeries(sourceId: String, seriesId: String): MangaSeriesPage {
        val key = "$sourceId:${seriesId.mangaSeriesCacheKey()}"
        seriesCache.get(key)?.let { cachedPage ->
            saveSeriesSnapshot(cachedPage.details)
            return cachedPage
        }

        val page = runCatching {
            adapter(sourceId).getSeriesPage(seriesId)
        }.getOrElse { error ->
            recoverMovedSavedSeries(sourceId, seriesId, error) ?: throw error
        }
        seriesCache.put(key, page)
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
        setBookmarkFlag(series, isFavorite = favorite, isSubscribed = null)

    suspend fun setSubscribed(series: MangaSeriesDetails, subscribed: Boolean) =
        setBookmarkFlag(series, isFavorite = null, isSubscribed = subscribed)

    private suspend fun setBookmarkFlag(
        series: MangaSeriesDetails,
        isFavorite: Boolean?,
        isSubscribed: Boolean?
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val updated = when {
            isFavorite != null -> bookmarkDao.setFavorite(
                sourceId = series.sourceId,
                seriesId = series.seriesId,
                title = series.title,
                coverUrl = series.coverUrl,
                description = series.description,
                favorite = isFavorite,
                updatedAtMillis = now
            )

            isSubscribed != null -> bookmarkDao.setSubscribed(
                sourceId = series.sourceId,
                seriesId = series.seriesId,
                title = series.title,
                coverUrl = series.coverUrl,
                description = series.description,
                subscribed = isSubscribed,
                updatedAtMillis = now
            )

            else -> 0
        }
        if (updated == 0) {
            bookmarkDao.upsert(
                series.toBookmarkEntity(
                    favorite = isFavorite ?: false,
                    subscribed = isSubscribed ?: false,
                    now = now
                )
            )
        }
    }

    suspend fun downloadMultipleSeriesChapters(
        targets: List<MangaChapterDownloadTarget>,
        onProgress: suspend (MangaDownloadProgress) -> Unit = {},
        onChapterDownloaded: suspend (MangaDownloadedChapter) -> Unit = {}
    ): MangaDownloadResult = withContext(Dispatchers.IO) {
        val deliveredFiles = Collections.synchronizedSet(mutableSetOf<String>())
        try {
            val batch = chapterDownloader.download(
                targets = targets,
                onProgress = onProgress,
                onChapterDownloaded = { chapterBatch ->
                    withContext(NonCancellable) {
                        chapterBatch.saveHistory()
                        chapterBatch.downloaded.forEach { downloaded ->
                            val key = downloaded.file.absolutePath
                            if (deliveredFiles.add(key)) {
                                onChapterDownloaded(downloaded)
                            }
                        }
                    }
                }
            )
            batch.toResult()
        } catch (error: MangaChapterDownloadCancelledException) {
            val partialResult = withContext(NonCancellable) {
                error.partialBatch.toResult()
            }
            throw MangaDownloadCancelledException(partialResult)
        }
    }

    private suspend fun MangaChapterDownloadBatch.saveHistory() {
        if (historyItems.isNotEmpty()) {
            historyDao.upsertAll(historyItems)
        }
    }

    private suspend fun MangaChapterDownloadBatch.toResult(): MangaDownloadResult {
        saveHistory()
        return MangaDownloadResult(
            downloaded = downloaded,
            failedMessages = failedMessages
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
        if (!error.isMangaSeriesNotFound()) return null

        val saved = bookmarkDao.findSeries(sourceId, seriesId) ?: return null
        val candidates = rankRecoveredSeriesCandidates(
            savedTitle = saved.title,
            originalSeriesId = seriesId,
            results = runCatching {
                searchSeries(sourceId, saved.title)
            }.getOrDefault(emptyList()),
            maxCandidates = MAX_MOVED_SERIES_CANDIDATES
        )

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
        if (saved.seriesId.mangaSeriesCacheKey() != details.seriesId.mangaSeriesCacheKey()) {
            bookmarkDao.deleteSeries(saved.sourceId, saved.seriesId)
        }
    }

    private fun adapter(sourceId: String): HtmlMangaSourceAdapter = sourceRegistry.adapter(sourceId)

    private companion object {
        const val SEARCH_CACHE_TTL_MILLIS = 5 * 60 * 1000L
        const val SEARCH_CACHE_MAX_ENTRIES = 24
        const val SERIES_CACHE_TTL_MILLIS = 2 * 60 * 1000L
        const val SERIES_CACHE_MAX_ENTRIES = 24
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

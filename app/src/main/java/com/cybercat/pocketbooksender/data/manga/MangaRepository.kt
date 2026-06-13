package com.cybercat.pocketbooksender.data.manga

import android.content.Context
import android.net.Uri
import com.cybercat.pocketbooksender.data.database.dao.MangaChapterHistoryDao
import com.cybercat.pocketbooksender.data.database.dao.MangaSeriesBookmarkDao
import com.cybercat.pocketbooksender.data.database.entity.MangaChapterHistoryEntity
import com.cybercat.pocketbooksender.data.database.entity.MangaSeriesBookmarkEntity
import com.cybercat.pocketbooksender.domain.FilenameSanitizer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@Singleton
class MangaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyDao: MangaChapterHistoryDao,
    private val bookmarkDao: MangaSeriesBookmarkDao,
    private val comxAdapter: ComxMangaAdapter,
) {
    private val adapters: List<HtmlMangaSourceAdapter> = listOf(comxAdapter)
    private val fileLock = Any()
    private val searchCache = ConcurrentHashMap<String, TimedCacheEntry<List<MangaSeriesSearchResult>>>()
    private val seriesCache = ConcurrentHashMap<String, TimedCacheEntry<MangaSeriesPage>>()

    val sources: List<MangaSourceSummary> =
        adapters.map { adapter ->
            MangaSourceSummary(
                id = adapter.id,
                title = adapter.title,
                homeUrl = adapter.homeUrl,
            )
        }

    val downloadedStableKeys: Flow<Set<String>> =
        historyDao.observeDownloadedStableKeys().map { keys -> keys.toSet() }

    val downloadedChapters: Flow<List<MangaChapterDownload>> =
        historyDao.observeHistory().map { items ->
            items.map { item -> item.toDownload() }
        }

    val savedSeries: Flow<List<MangaSeriesBookmark>> =
        bookmarkDao.observeSavedSeries()
            .onStart { bookmarkDao.normalizeMutualExclusion() }
            .map { items ->
                items.map { item -> item.toBookmark() }
            }

    suspend fun authState(sourceId: String): MangaAuthState =
        adapter(sourceId).authState()

    fun homeUrl(sourceId: String): String =
        adapter(sourceId).homeUrl

    fun buildSearchUrl(sourceId: String, query: String): String =
        adapter(sourceId).buildSearchUrl(query)

    suspend fun searchSeries(
        sourceId: String,
        query: String,
    ): List<MangaSeriesSearchResult> {
        val key = "$sourceId:${query.trim().lowercase()}"
        searchCache[key]?.takeIf { entry -> entry.isFresh(SearchCacheTtlMillis) }?.let { entry ->
            return entry.value
        }

        val results = adapter(sourceId).searchSeries(query)
        searchCache[key] = TimedCacheEntry(results)
        return results
    }

    suspend fun openSeries(
        sourceId: String,
        seriesId: String,
    ): MangaSeriesPage {
        val key = "$sourceId:${seriesId.normalizeCacheKey()}"
        seriesCache[key]?.takeIf { entry -> entry.isFresh(SeriesCacheTtlMillis) }?.let { entry ->
            saveSeriesSnapshot(entry.value.details)
            return entry.value
        }

        val page = adapter(sourceId).getSeriesPage(seriesId)
        seriesCache[key] = TimedCacheEntry(page)
        saveSeriesSnapshot(page.details)
        return page
    }

    fun parseWebPage(
        sourceId: String,
        url: String,
        html: String,
    ): MangaParsedPage {
        val adapter = adapter(sourceId)
        if (!adapter.ownsUrl(url)) return MangaParsedPage.Unsupported

        val seriesPage = runCatching { adapter.parseSeriesPage(url, html) }.getOrNull()
        if (seriesPage != null && seriesPage.chapters.isNotEmpty()) {
            return MangaParsedPage.Series(seriesPage)
        }

        val searchResults = runCatching { adapter.parseSearchResults(url, html) }.getOrDefault(emptyList())
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
            openedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun setFavorite(
        series: MangaSeriesDetails,
        favorite: Boolean,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val updated = bookmarkDao.setFavorite(
            sourceId = series.sourceId,
            seriesId = series.seriesId,
            title = series.title,
            coverUrl = series.coverUrl,
            description = series.description,
            favorite = favorite,
            updatedAtMillis = now,
        )
        if (updated == 0) {
            bookmarkDao.upsert(series.toBookmarkEntity(favorite = favorite, subscribed = false, now = now))
        }
    }

    suspend fun setSubscribed(
        series: MangaSeriesDetails,
        subscribed: Boolean,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val updated = bookmarkDao.setSubscribed(
            sourceId = series.sourceId,
            seriesId = series.seriesId,
            title = series.title,
            coverUrl = series.coverUrl,
            description = series.description,
            subscribed = subscribed,
            updatedAtMillis = now,
        )
        if (updated == 0) {
            bookmarkDao.upsert(series.toBookmarkEntity(favorite = false, subscribed = subscribed, now = now))
        }
    }

    suspend fun checkSubscriptions(): List<MangaSubscriptionCheckResult> = withContext(Dispatchers.IO) {
        val downloadedStableKeys = historyDao.downloadedStableKeys().toSet()
        val checkedAtMillis = System.currentTimeMillis()
        bookmarkDao.subscribedSeries().mapNotNull { saved ->
            runCatching {
                val page = openSeries(saved.sourceId, saved.seriesId)
                bookmarkDao.markChecked(saved.sourceId, saved.seriesId, checkedAtMillis)
                val newChapters = page.chapters.filter { chapter ->
                    chapter.stableKey !in downloadedStableKeys
                }
                MangaSubscriptionCheckResult(page = page, newChapters = newChapters)
            }.getOrNull()
        }
    }

    suspend fun downloadChapters(
        sourceId: String,
        series: MangaSeriesDetails,
        chapters: List<MangaChapter>,
        onProgress: suspend (MangaDownloadProgress) -> Unit = {},
    ): MangaDownloadResult = withContext(Dispatchers.IO) {
        if (chapters.isEmpty()) {
            throw IllegalArgumentException("No manga chapters selected")
        }

        val adapter = adapter(sourceId)
        val outputDir = File(context.cacheDir, "manga").apply { mkdirs() }
        val completedChapters = AtomicInteger(0)
        val chapterSemaphore = Semaphore(MaxParallelChapters)
        val pageSemaphore = Semaphore(MaxParallelPages)

        val outcomes = coroutineScope {
            chapters.map { chapter ->
                async {
                    chapterSemaphore.withPermit {
                        downloadChapter(
                            sourceId = sourceId,
                            series = series,
                            chapter = chapter,
                            adapter = adapter,
                            outputDir = outputDir,
                            totalChapters = chapters.size,
                            completedChapters = completedChapters,
                            pageSemaphore = pageSemaphore,
                            onProgress = onProgress,
                        )
                    }
                }
            }.awaitAll()
        }

        val downloaded = outcomes.mapNotNull { outcome -> outcome.downloaded }
        val historyItems = outcomes.mapNotNull { outcome -> outcome.historyItem }
        val errors = outcomes.mapNotNull { outcome -> outcome.errorMessage }

        if (historyItems.isNotEmpty()) {
            historyDao.upsertAll(historyItems)
        }

        MangaDownloadResult(
            downloaded = downloaded,
            failedMessages = errors,
        )
    }

    private suspend fun downloadChapter(
        sourceId: String,
        series: MangaSeriesDetails,
        chapter: MangaChapter,
        adapter: MangaSourceAdapter,
        outputDir: File,
        totalChapters: Int,
        completedChapters: AtomicInteger,
        pageSemaphore: Semaphore,
        onProgress: suspend (MangaDownloadProgress) -> Unit,
    ): ChapterDownloadOutcome {
        return runCatching {
            onProgress(
                MangaDownloadProgress(
                    chapterTitle = chapter.title,
                    totalChapters = totalChapters,
                    completedChapters = completedChapters.get(),
                    totalPages = 0,
                    completedPages = 0,
                    detail = "Preparing",
                ),
            )

            val baseFileName = "${FilenameSanitizer.fileTitle(series.title, "Manga")}_" +
                FilenameSanitizer.fileTitle(chapter.title, "Chapter")
            val directArchive = downloadChapterArchive(
                adapter = adapter,
                chapter = chapter,
                outputDir = outputDir,
                baseFileName = baseFileName,
            ) { detail ->
                onProgress(
                    MangaDownloadProgress(
                        chapterTitle = chapter.title,
                        totalChapters = totalChapters,
                        completedChapters = completedChapters.get(),
                        totalPages = 0,
                        completedPages = 0,
                        detail = detail,
                    ),
                )
            }

            var fallbackPageCount = 0
            val outputFile = directArchive ?: downloadChapterFromPages(
                adapter = adapter,
                chapter = chapter,
                outputDir = outputDir,
                baseFileName = baseFileName,
                pageSemaphore = pageSemaphore,
                onPageProgress = { totalPages, completedPages, detail ->
                    fallbackPageCount = totalPages
                    onProgress(
                        MangaDownloadProgress(
                            chapterTitle = chapter.title,
                            totalChapters = totalChapters,
                            completedChapters = completedChapters.get(),
                            totalPages = totalPages,
                            completedPages = completedPages,
                            detail = detail,
                        ),
                    )
                },
            )

            val pageCount = fallbackPageCount
            val progressDetail = if (directArchive == null) {
                null
            } else {
                "Archive downloaded"
            }
            val completedPages = if (directArchive == null) {
                fallbackPageCount
            } else {
                0
            }
            val completed = completedChapters.incrementAndGet()
            onProgress(
                MangaDownloadProgress(
                    chapterTitle = chapter.title,
                    totalChapters = totalChapters,
                    completedChapters = completed.coerceAtMost(totalChapters - 1),
                    totalPages = pageCount,
                    completedPages = completedPages,
                    detail = progressDetail,
                )
            )

            ChapterDownloadOutcome(
                downloaded = MangaDownloadedChapter(
                    chapter = chapter,
                    file = outputFile,
                ),
                historyItem = MangaChapterHistoryEntity(
                    sourceId = sourceId,
                    seriesId = series.seriesId,
                    chapterId = chapter.chapterId,
                    stableKey = chapter.stableKey,
                    seriesTitle = series.title,
                    chapterTitle = chapter.title,
                    fileName = outputFile.name,
                    fileUri = Uri.fromFile(outputFile).toString(),
                    downloadedAtMillis = System.currentTimeMillis(),
                ),
                errorMessage = null,
            )
        }.getOrElse { error ->
            ChapterDownloadOutcome(
                downloaded = null,
                historyItem = null,
                errorMessage = "${chapter.title}: ${error.message ?: error::class.java.simpleName}",
            )
        }
    }

    private suspend fun downloadPages(
        adapter: MangaSourceAdapter,
        pages: List<MangaPage>,
        pageSemaphore: Semaphore,
        onPageProgress: suspend (Int, String?) -> Unit,
    ): List<DownloadedMangaPage> = coroutineScope {
        val completedPages = AtomicInteger(0)
        pages.sortedBy { page -> page.index }
            .map { page ->
                async {
                    pageSemaphore.withPermit {
                        val downloaded = downloadPageWithRetry(
                            adapter = adapter,
                            page = page,
                            completedPages = completedPages,
                            totalPages = pages.size,
                            onPageProgress = onPageProgress,
                        )
                        onPageProgress(completedPages.incrementAndGet(), null)
                        DownloadedMangaPage(
                            index = page.index,
                            bytes = downloaded.bytes,
                            fileExtension = downloaded.fileExtension,
                        )
                    }
                }
            }
            .awaitAll()
            .sortedBy { page -> page.index }
    }

    private suspend fun downloadPageWithRetry(
        adapter: MangaSourceAdapter,
        page: MangaPage,
        completedPages: AtomicInteger,
        totalPages: Int,
        onPageProgress: suspend (Int, String?) -> Unit,
    ): MangaDownloadedPage {
        var lastError: IOException? = null
        repeat(PageDownloadAttempts) { attempt ->
            try {
                return withTimeout(PageAttemptTimeoutMillis) {
                    adapter.downloadPage(page)
                }
            } catch (error: IOException) {
                lastError = error
            } catch (error: TimeoutCancellationException) {
                lastError = IOException("Page ${page.index + 1} timed out", error)
            }

            lastError?.let { error ->
                val nextAttempt = attempt + 2
                if (nextAttempt > PageDownloadAttempts) {
                    throw error
                }
                onPageProgress(
                    completedPages.get(),
                    "Retry page ${page.index + 1}/$totalPages ($nextAttempt/$PageDownloadAttempts)",
                )
                delay(PageRetryDelayMillis * nextAttempt)
            }
        }
        throw lastError ?: IOException("Page download failed")
    }

    private suspend fun downloadChapterFromPages(
        adapter: MangaSourceAdapter,
        chapter: MangaChapter,
        outputDir: File,
        baseFileName: String,
        pageSemaphore: Semaphore,
        onPageProgress: suspend (Int, Int, String?) -> Unit,
    ): File {
        val pages = adapter.getChapterPages(chapter.chapterId)
            .ifEmpty { throw IOException("No pages found: ${chapter.title}") }
        val downloadedPages = downloadPages(
            adapter = adapter,
            pages = pages,
            pageSemaphore = pageSemaphore,
        ) { completedPages, detail ->
            onPageProgress(pages.size, completedPages, detail)
        }

        val file = synchronized(fileLock) {
            uniqueFile(outputDir, "$baseFileName.cbz")
        }
        writeCbz(downloadedPages, file)
        return file
    }

    private suspend fun downloadChapterArchive(
        adapter: MangaSourceAdapter,
        chapter: MangaChapter,
        outputDir: File,
        baseFileName: String,
        onProgress: suspend (String) -> Unit,
    ): File? {
        if (chapter.downloadUrl.isNullOrBlank()) return null

        val tempFile = synchronized(fileLock) {
            uniqueFile(outputDir, "$baseFileName.download")
        }

        try {
            onProgress("Downloading archive")
            val archive = adapter.downloadChapterArchive(chapter, tempFile) ?: run {
                tempFile.delete()
                return null
            }
            return moveTempToUnique(
                tempFile = tempFile,
                directory = outputDir,
                fileName = "$baseFileName.${archive.fileExtension}",
            )
        } catch (error: IOException) {
            tempFile.delete()
            onProgress("Archive unavailable, downloading pages")
            return null
        }
    }

    private suspend fun writeCbz(
        pages: List<DownloadedMangaPage>,
        outputFile: File,
    ) {
        ZipOutputStream(outputFile.outputStream().buffered()).use { zip ->
            pages.forEachIndexed { index, page ->
                val extension = page.fileExtension.lowercase().trimStart('.').ifBlank { "jpg" }
                val entryName = "${(index + 1).toString().padStart(4, '0')}.$extension"
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(page.bytes)
                zip.closeEntry()
            }
        }
    }

    private fun uniqueFile(directory: File, fileName: String): File {
        val base = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        var candidate = File(directory, fileName)
        var index = 2
        while (candidate.exists() || !candidate.createNewFile()) {
            val suffix = if (extension.isBlank()) "" else ".$extension"
            candidate = File(directory, "$base-$index$suffix")
            index += 1
        }
        return candidate
    }

    private fun moveTempToUnique(
        tempFile: File,
        directory: File,
        fileName: String,
    ): File = synchronized(fileLock) {
        val outputFile = uniqueFile(directory, fileName)
        if (!outputFile.delete()) {
            throw IOException("Cannot prepare output file: ${outputFile.name}")
        }
        if (!tempFile.renameTo(outputFile)) {
            tempFile.copyTo(outputFile, overwrite = true)
            tempFile.delete()
        }
        outputFile
    }

    private fun adapter(sourceId: String): HtmlMangaSourceAdapter =
        adapters.firstOrNull { adapter -> adapter.id == sourceId }
            ?: throw IllegalArgumentException("Unknown manga source: $sourceId")

    private companion object {
        const val SearchCacheTtlMillis = 5 * 60 * 1000L
        const val SeriesCacheTtlMillis = 2 * 60 * 1000L
        const val MaxParallelChapters = 2
        const val MaxParallelPages = 6
        const val PageDownloadAttempts = 3
        const val PageAttemptTimeoutMillis = 18_000L
        const val PageRetryDelayMillis = 450L
    }
}

sealed interface MangaParsedPage {
    data class SearchResults(val results: List<MangaSeriesSearchResult>) : MangaParsedPage
    data class Series(val page: MangaSeriesPage) : MangaParsedPage
    data class ChapterPages(val pages: List<MangaPage>) : MangaParsedPage
    data object Unsupported : MangaParsedPage
}

data class MangaDownloadResult(
    val downloaded: List<MangaDownloadedChapter>,
    val failedMessages: List<String>,
)

data class MangaDownloadedChapter(
    val chapter: MangaChapter,
    val file: File,
)

data class MangaDownloadProgress(
    val chapterTitle: String,
    val totalChapters: Int,
    val completedChapters: Int,
    val totalPages: Int,
    val completedPages: Int,
    val detail: String? = null,
)

private data class ChapterDownloadOutcome(
    val downloaded: MangaDownloadedChapter?,
    val historyItem: MangaChapterHistoryEntity?,
    val errorMessage: String?,
)

private data class DownloadedMangaPage(
    val index: Int,
    val bytes: ByteArray,
    val fileExtension: String,
)

private data class TimedCacheEntry<T>(
    val value: T,
    val createdAtMillis: Long = System.currentTimeMillis(),
) {
    fun isFresh(ttlMillis: Long): Boolean =
        System.currentTimeMillis() - createdAtMillis <= ttlMillis
}

private fun MangaChapterHistoryEntity.toDownload(): MangaChapterDownload =
    MangaChapterDownload(
        sourceId = sourceId,
        seriesId = seriesId,
        stableKey = stableKey,
        seriesTitle = seriesTitle,
        chapterTitle = chapterTitle,
        fileName = fileName,
        downloadedAtMillis = downloadedAtMillis,
    )

private fun MangaSeriesBookmarkEntity.toBookmark(): MangaSeriesBookmark =
    MangaSeriesBookmark(
        sourceId = sourceId,
        seriesId = seriesId,
        title = title,
        coverUrl = coverUrl,
        description = description,
        favorite = favorite,
        subscribed = subscribed,
        addedAtMillis = addedAtMillis,
        lastOpenedAtMillis = lastOpenedAtMillis,
        lastCheckedAtMillis = lastCheckedAtMillis,
    )

private fun MangaSeriesDetails.toBookmarkEntity(
    favorite: Boolean,
    subscribed: Boolean,
    now: Long,
): MangaSeriesBookmarkEntity =
    MangaSeriesBookmarkEntity(
        sourceId = sourceId,
        seriesId = seriesId,
        title = title,
        coverUrl = coverUrl,
        description = description,
        favorite = favorite,
        subscribed = subscribed,
        addedAtMillis = now,
        lastOpenedAtMillis = now,
        lastCheckedAtMillis = null,
    )

private fun String.normalizeCacheKey(): String =
    trim().trimEnd('/').lowercase()

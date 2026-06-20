package com.cybercat.ebooksender.data.manga

import android.content.Context
import android.net.Uri
import com.cybercat.ebooksender.data.database.entity.MangaChapterHistoryEntity
import com.cybercat.ebooksender.data.network.NetworkStateChecker
import com.cybercat.ebooksender.domain.FilenameSanitizer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val NETWORK_UNAVAILABLE_MESSAGE = "MANGA_NETWORK_UNAVAILABLE"

@Singleton
class MangaChapterDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sourceRegistry: MangaSourceRegistry,
    private val archiveHelper: MangaArchiveHelper,
    private val networkStateChecker: NetworkStateChecker
) {
    internal suspend fun download(
        targets: List<MangaChapterDownloadTarget>,
        onProgress: suspend (MangaDownloadProgress) -> Unit = {},
        onChapterDownloaded: suspend (MangaChapterDownloadBatch) -> Unit = {}
    ): MangaChapterDownloadBatch = withContext(Dispatchers.IO) {
        if (targets.isEmpty()) {
            throw IllegalArgumentException("No manga chapters selected")
        }

        val outputDir = File(context.cacheDir, "manga").apply { mkdirs() }
        val concurrency = targets.downloadConcurrency()
        val completedChapters = AtomicInteger(0)
        val chapterSemaphore = Semaphore(concurrency.maxParallelChapters)
        val pageSemaphore = Semaphore(concurrency.maxParallelPages)
        val completedOutcomes = Collections.synchronizedList(
            mutableListOf<ChapterDownloadOutcome>()
        )

        val outcomes = try {
            coroutineScope {
                targets.map { target ->
                    async {
                        chapterSemaphore.withPermit {
                            val sourceId = target.resolvedSourceId()
                            val adapter = sourceRegistry.adapter(sourceId)
                            downloadChapter(
                                sourceId = sourceId,
                                series = target.series,
                                chapter = target.chapter,
                                adapter = adapter,
                                outputDir = outputDir,
                                totalChapters = targets.size,
                                completedChapters = completedChapters,
                                pageSemaphore = pageSemaphore,
                                onProgress = onProgress
                            ).also { outcome ->
                                completedOutcomes.add(outcome)
                                if (outcome.downloaded != null) {
                                    onChapterDownloaded(listOf(outcome).toBatch())
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        } catch (error: CancellationException) {
            throw MangaChapterDownloadCancelledException(completedOutcomes.toBatch())
        }

        outcomes.toBatch()
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
        onProgress: suspend (MangaDownloadProgress) -> Unit
    ): ChapterDownloadOutcome = runCatching {
        onProgress(
            MangaDownloadProgress(
                chapterTitle = chapter.title,
                totalChapters = totalChapters,
                completedChapters = completedChapters.get(),
                totalPages = 0,
                completedPages = 0,
                detail = "Preparing"
            )
        )

        val baseFileName = "${FilenameSanitizer.fileTitle(series.title, "Manga")}_" +
            FilenameSanitizer.fileTitle(chapter.title, "Chapter")
        val directArchive = downloadChapterArchive(
            adapter = adapter,
            chapter = chapter,
            outputDir = outputDir,
            baseFileName = baseFileName,
            onProgress = { detail, bytesRead, totalBytes ->
                onProgress(
                    MangaDownloadProgress(
                        chapterTitle = chapter.title,
                        totalChapters = totalChapters,
                        completedChapters = completedChapters.get(),
                        totalPages = 0,
                        completedPages = 0,
                        detail = detail,
                        archiveBytesRead = bytesRead,
                        archiveTotalBytes = totalBytes
                    )
                )
            }
        )

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
                        detail = detail
                    )
                )
            }
        )

        val pageCount = fallbackPageCount
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
                completedChapters = completed.coerceAtMost(totalChapters),
                totalPages = pageCount,
                completedPages = completedPages,
                detail = null
            )
        )

        ChapterDownloadOutcome(
            downloaded = MangaDownloadedChapter(
                series = series,
                chapter = chapter,
                file = outputFile
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
                downloadedAtMillis = System.currentTimeMillis()
            ),
            errorMessage = null
        )
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        if (error is MangaBrowserSessionRefreshRequiredException) throw error
        val message = if (error is MangaNetworkUnavailableException) {
            "${chapter.title}: $NETWORK_UNAVAILABLE_MESSAGE"
        } else {
            "${chapter.title}: ${error.message ?: error::class.java.simpleName}"
        }
        ChapterDownloadOutcome(
            downloaded = null,
            historyItem = null,
            errorMessage = message
        )
    }

    private suspend fun downloadPages(
        adapter: MangaSourceAdapter,
        pages: List<MangaPage>,
        tempDir: File,
        pageSemaphore: Semaphore,
        onPageProgress: suspend (Int, String?) -> Unit
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
                            onPageProgress = onPageProgress
                        )
                        // Write to a temporary file immediately to keep memory usage minimal
                        val tempFile = File.createTempFile("page_${page.index}_", ".tmp", tempDir)
                        tempFile.writeBytes(downloaded.bytes)
                        onPageProgress(completedPages.incrementAndGet(), null)
                        DownloadedMangaPage(
                            index = page.index,
                            file = tempFile,
                            fileExtension = downloaded.fileExtension
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
        onPageProgress: suspend (Int, String?) -> Unit
    ): MangaDownloadedPage {
        var lastError: IOException? = null
        repeat(PAGE_DOWNLOAD_ATTEMPTS) { attempt ->
            ensureNetworkAvailable()
            try {
                return withTimeout(PAGE_ATTEMPT_TIMEOUT_MILLIS) {
                    adapter.downloadPage(page)
                }
            } catch (error: IOException) {
                lastError = error
            } catch (error: TimeoutCancellationException) {
                lastError = IOException("Page ${page.index + 1} timed out", error)
            }

            lastError?.let { error ->
                val nextAttempt = attempt + 2
                if (nextAttempt > PAGE_DOWNLOAD_ATTEMPTS) {
                    throw error
                }
                ensureNetworkAvailable()
                onPageProgress(
                    completedPages.get(),
                    "retry_page:${page.index + 1}:$totalPages:$nextAttempt:$PAGE_DOWNLOAD_ATTEMPTS"
                )
                delay(PAGE_RETRY_DELAY_MILLIS * (1L shl attempt))
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
        onPageProgress: suspend (Int, Int, String?) -> Unit
    ): File {
        ensureNetworkAvailable()
        val pages = adapter.getChapterPages(chapter.chapterId)
            .ifEmpty { throw IOException("No pages found: ${chapter.title}") }

        val tempPagesDir = File(
            outputDir,
            "temp_pages_${chapter.chapterId}_${System.currentTimeMillis()}"
        ).apply {
            mkdirs()
        }
        try {
            val downloadedPages = downloadPages(
                adapter = adapter,
                pages = pages,
                tempDir = tempPagesDir,
                pageSemaphore = pageSemaphore
            ) { completedPages, detail ->
                onPageProgress(pages.size, completedPages, detail)
            }

            return archiveHelper.writeCbzToUnique(
                pages = downloadedPages,
                directory = outputDir,
                fileName = "$baseFileName.cbz"
            )
        } finally {
            // Clean up temporary files and directory recursively
            tempPagesDir.deleteRecursively()
        }
    }

    private suspend fun downloadChapterArchive(
        adapter: MangaSourceAdapter,
        chapter: MangaChapter,
        outputDir: File,
        baseFileName: String,
        onProgress: suspend (detail: String, bytesRead: Long, totalBytes: Long?) -> Unit
    ): File? {
        if (chapter.downloadUrl.isNullOrBlank()) return null

        val tempFile = archiveHelper.createTempFile(outputDir, "$baseFileName.download")

        try {
            ensureNetworkAvailable()
            onProgress("Downloading archive", 0L, null)
            val archive = adapter.downloadChapterArchive(
                chapter = chapter,
                outputFile = tempFile,
                onProgress = { bytesRead, totalBytes ->
                    onProgress("Downloading archive", bytesRead, totalBytes)
                }
            ) ?: run {
                return null
            }
            return archiveHelper.moveTempToUnique(
                tempFile = tempFile,
                directory = outputDir,
                fileName = "$baseFileName.${archive.fileExtension}"
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            if (error is MangaBrowserSessionRefreshRequiredException) {
                throw error
            }
            if (error is MangaNetworkUnavailableException ||
                !networkStateChecker.hasActiveInternetConnection()
            ) {
                throw MangaNetworkUnavailableException()
            }
            onProgress("Archive unavailable, downloading pages", 0L, null)
            return null
        } finally {
            tempFile.delete()
        }
    }

    private fun ensureNetworkAvailable() {
        if (!networkStateChecker.hasActiveInternetConnection()) {
            throw MangaNetworkUnavailableException()
        }
    }

    private fun List<MangaChapterDownloadTarget>.downloadConcurrency(): MangaDownloadConcurrency {
        val sourceIds = map { target -> target.resolvedSourceId() }.distinct()
        val capabilities = sourceIds.map { sourceId ->
            sourceRegistry.adapter(sourceId).capabilities
        }
        return MangaDownloadConcurrency(
            maxParallelChapters = capabilities
                .minOfOrNull { capability -> capability.maxParallelChapters }
                ?.coerceAtLeast(1)
                ?: DEFAULT_MAX_PARALLEL_CHAPTERS,
            maxParallelPages = capabilities
                .minOfOrNull { capability -> capability.maxParallelPages }
                ?.coerceAtLeast(1)
                ?: DEFAULT_MAX_PARALLEL_PAGES
        )
    }

    private companion object {
        const val DEFAULT_MAX_PARALLEL_CHAPTERS = 2
        const val DEFAULT_MAX_PARALLEL_PAGES = 6
        const val PAGE_DOWNLOAD_ATTEMPTS = 3
        const val PAGE_ATTEMPT_TIMEOUT_MILLIS = 18_000L
        const val PAGE_RETRY_DELAY_MILLIS = 450L
    }
}

private data class MangaDownloadConcurrency(val maxParallelChapters: Int, val maxParallelPages: Int)

internal data class MangaChapterDownloadBatch(
    val downloaded: List<MangaDownloadedChapter>,
    val historyItems: List<MangaChapterHistoryEntity>,
    val failedMessages: List<String>
)

internal class MangaChapterDownloadCancelledException(val partialBatch: MangaChapterDownloadBatch) :
    CancellationException("Manga chapter download canceled")

private data class ChapterDownloadOutcome(
    val downloaded: MangaDownloadedChapter?,
    val historyItem: MangaChapterHistoryEntity?,
    val errorMessage: String?
)

private class MangaNetworkUnavailableException : IOException(NETWORK_UNAVAILABLE_MESSAGE)

private fun Collection<ChapterDownloadOutcome>.toBatch(): MangaChapterDownloadBatch =
    MangaChapterDownloadBatch(
        downloaded = mapNotNull { outcome -> outcome.downloaded },
        historyItems = mapNotNull { outcome -> outcome.historyItem },
        failedMessages = mapNotNull { outcome -> outcome.errorMessage }
    )

private fun MangaChapterDownloadTarget.resolvedSourceId(): String {
    val seriesSourceId = series.sourceId.trim()
    val chapterSourceId = chapter.sourceId.trim()
    require(seriesSourceId.isNotBlank() || chapterSourceId.isNotBlank()) {
        "Manga chapter target has no source"
    }
    require(
        seriesSourceId.isBlank() ||
            chapterSourceId.isBlank() ||
            seriesSourceId == chapterSourceId
    ) {
        "Manga chapter source does not match series source"
    }
    return seriesSourceId.ifBlank { chapterSourceId }
}

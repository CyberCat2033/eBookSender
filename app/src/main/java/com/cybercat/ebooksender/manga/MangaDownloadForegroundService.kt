package com.cybercat.ebooksender.manga

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import com.cybercat.ebooksender.data.manga.MangaDownloadCancelledException
import com.cybercat.ebooksender.data.manga.MangaDownloadCoordinator
import com.cybercat.ebooksender.data.manga.MangaDownloadEvent
import com.cybercat.ebooksender.data.manga.MangaDownloadRequest
import com.cybercat.ebooksender.data.manga.MangaDownloadedChapter
import com.cybercat.ebooksender.data.manga.MangaRepository
import com.cybercat.ebooksender.data.manga.mangaStableSelectionKey
import com.cybercat.ebooksender.data.transfer.UploadQueueManager
import com.cybercat.ebooksender.localization.LocalizationManager
import com.cybercat.ebooksender.model.BookCategory
import com.cybercat.ebooksender.model.UploadItem
import com.cybercat.ebooksender.power.ScopedWakeLock
import dagger.hilt.android.AndroidEntryPoint
import java.util.Collections
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MangaDownloadForegroundService : Service() {
    @Inject lateinit var mangaRepository: MangaRepository

    @Inject lateinit var downloadCoordinator: MangaDownloadCoordinator

    @Inject lateinit var queueManager: UploadQueueManager

    @Inject lateinit var localizationManager: LocalizationManager

    private val downloadNotifications by lazy {
        MangaDownloadNotificationManager(
            service = this,
            localizationManager = localizationManager
        )
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeRequestId: String? = null
    private var activeDownloadJob: Job? = null
    private val downloadWakeLock by lazy {
        ScopedWakeLock(
            context = this,
            tag = "MangaDownloadForegroundService",
            timeoutMillis = DOWNLOAD_WAKE_LOCK_TIMEOUT_MILLIS
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        downloadNotifications.ensureNotificationChannel()

        if (intent?.action == ACTION_CANCEL_DOWNLOAD) {
            cancelActiveDownload(intent.getStringExtra(EXTRA_REQUEST_ID), startId)
            return START_NOT_STICKY
        }

        val request = downloadCoordinator.takeRequest(intent?.getStringExtra(EXTRA_REQUEST_ID))
        if (request == null) {
            downloadNotifications.startNothingToDownload()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        downloadNotifications.startPreparing()

        downloadWakeLock.acquire()
        activeRequestId = request.id
        activeDownloadJob = serviceScope.launch {
            try {
                runDownload(request)
            } finally {
                activeRequestId = null
                activeDownloadJob = null
                downloadWakeLock.release()
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        downloadWakeLock.release()
        downloadNotifications.dispose()
        activeDownloadJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun cancelActiveDownload(requestId: String?, startId: Int) {
        val activeId = activeRequestId
        val activeJob = activeDownloadJob
        if (activeJob == null || activeId == null) {
            stopSelf(startId)
            return
        }
        if (requestId == null || requestId == activeId) {
            activeJob.cancel()
        }
    }

    private suspend fun runDownload(request: MangaDownloadRequest) {
        downloadCoordinator.emit(
            MangaDownloadEvent.Started(
                requestId = request.id,
                kind = request.kind,
                totalChapters = request.targets.size
            )
        )

        val queuedDownloads = Collections.synchronizedList(
            mutableListOf<MangaDownloadedChapter>()
        )
        try {
            val result = mangaRepository.downloadMultipleSeriesChapters(
                targets = request.targets,
                onProgress = { progress ->
                    downloadCoordinator.emit(
                        MangaDownloadEvent.Progress(
                            requestId = request.id,
                            progress = progress
                        )
                    )
                    downloadNotifications.notifyProgress(progress)
                },
                onChapterDownloaded = { downloaded ->
                    queuedDownloads.add(downloaded)
                    addDownloadedMangaFiles(listOf(downloaded))
                }
            )

            val downloaded = result.downloaded.mergeWith(queuedDownloads)
            addDownloadedMangaFiles(downloaded.missingFrom(queuedDownloads))

            downloadCoordinator.emit(
                MangaDownloadEvent.Completed(
                    requestId = request.id,
                    kind = request.kind,
                    downloadedChapterIds = downloaded.mapTo(mutableSetOf()) { item ->
                        item.chapter.chapterId
                    },
                    downloadedSubscriptionKeys = downloaded.mapTo(
                        mutableSetOf()
                    ) { item ->
                        item.chapter.mangaStableSelectionKey()
                    },
                    addedToQueueCount = downloaded.size,
                    failedMessages = result.failedMessages
                )
            )
            showFinishedNotification(
                downloaded = downloaded.size,
                failed = result.failedMessages.size
            )
        } catch (error: MangaDownloadCancelledException) {
            handleCanceledDownload(request, error.result.downloaded.mergeWith(queuedDownloads))
        } catch (error: CancellationException) {
            handleCanceledDownload(request, queuedDownloads.toList())
        } catch (error: Throwable) {
            downloadCoordinator.emit(
                MangaDownloadEvent.Failed(
                    requestId = request.id,
                    kind = request.kind,
                    message =
                        error.message
                            ?: localizationManager.currentStrings.value.mangaErrorCannotDownload
                )
            )
            showFinishedNotification(downloaded = 0, failed = request.targets.size)
        }
    }

    private fun handleCanceledDownload(
        request: MangaDownloadRequest,
        downloaded: List<MangaDownloadedChapter>
    ) {
        val completed = downloaded.distinctBy { item -> item.file.absolutePath }
        addDownloadedMangaFiles(completed)
        downloadCoordinator.emit(
            MangaDownloadEvent.Canceled(
                requestId = request.id,
                kind = request.kind,
                downloadedChapterIds = completed.mapTo(mutableSetOf()) { item ->
                    item.chapter.chapterId
                },
                downloadedSubscriptionKeys = completed.mapTo(mutableSetOf()) { item ->
                    item.chapter.mangaStableSelectionKey()
                },
                addedToQueueCount = completed.size
            )
        )
        showFinishedNotification(downloaded = completed.size, failed = 0)
    }

    private fun addDownloadedMangaFiles(downloaded: List<MangaDownloadedChapter>) {
        val items = downloaded.map { item ->
            val uri = Uri.fromFile(item.file)
            val ext = item.file.extension.lowercase().ifBlank { "cbz" }
            UploadItem(
                id = UUID.randomUUID().toString(),
                sourceUri = uri.toString(),
                originalName = item.file.name,
                extension = ext,
                category = BookCategory.Manga,
                title = item.chapter.title,
                mangaSeries = item.series.title,
                mangaVolume = com.cybercat.ebooksender.data.manga.mangaChapterQueueVolume(
                    item.chapter
                ),
                seriesIndex = com.cybercat.ebooksender.data.manga.mangaChapterQueueSeriesIndex(
                    item.chapter
                ),
                plannedPath = ""
            )
        }

        queueManager.addPreparedItems(items)
    }

    private fun showFinishedNotification(downloaded: Int, failed: Int) {
        downloadNotifications.showFinishedNotification(downloaded, failed)
    }

    companion object {
        private const val ACTION_CANCEL_DOWNLOAD =
            "com.cybercat.ebooksender.manga.CANCEL_DOWNLOAD"
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val DOWNLOAD_WAKE_LOCK_TIMEOUT_MILLIS = 90 * 60 * 1000L

        fun createIntent(context: Context, requestId: String): Intent =
            Intent(context, MangaDownloadForegroundService::class.java)
                .putExtra(EXTRA_REQUEST_ID, requestId)

        fun createCancelIntent(context: Context, requestId: String): Intent =
            Intent(context, MangaDownloadForegroundService::class.java)
                .setAction(ACTION_CANCEL_DOWNLOAD)
                .putExtra(EXTRA_REQUEST_ID, requestId)
    }
}

private fun List<MangaDownloadedChapter>.mergeWith(
    other: Collection<MangaDownloadedChapter>
): List<MangaDownloadedChapter> = (this + other).distinctBy { item -> item.file.absolutePath }

private fun List<MangaDownloadedChapter>.missingFrom(
    other: Collection<MangaDownloadedChapter>
): List<MangaDownloadedChapter> {
    val existing = other.mapTo(mutableSetOf()) { item -> item.file.absolutePath }
    return filterNot { item -> item.file.absolutePath in existing }
}

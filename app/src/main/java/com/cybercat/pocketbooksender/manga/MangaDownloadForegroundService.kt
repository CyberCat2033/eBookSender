package com.cybercat.pocketbooksender.manga

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cybercat.pocketbooksender.MainActivity
import com.cybercat.pocketbooksender.R
import com.cybercat.pocketbooksender.data.manga.MangaDownloadCancelledException
import com.cybercat.pocketbooksender.data.manga.MangaDownloadCoordinator
import com.cybercat.pocketbooksender.data.manga.MangaDownloadEvent
import com.cybercat.pocketbooksender.data.manga.MangaDownloadProgress
import com.cybercat.pocketbooksender.data.manga.MangaDownloadRequest
import com.cybercat.pocketbooksender.data.manga.MangaDownloadedChapter
import com.cybercat.pocketbooksender.data.manga.MangaRepository
import com.cybercat.pocketbooksender.data.manga.mangaStableSelectionKey
import com.cybercat.pocketbooksender.data.transfer.UploadQueueManager
import com.cybercat.pocketbooksender.lifecycle.AppVisibilityTracker
import com.cybercat.pocketbooksender.localization.LocalizationManager
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.UploadItem
import com.cybercat.pocketbooksender.power.ScopedWakeLock
import dagger.hilt.android.AndroidEntryPoint
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
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
        ensureNotificationChannel()

        if (intent?.action == ACTION_CANCEL_DOWNLOAD) {
            cancelActiveDownload(intent.getStringExtra(EXTRA_REQUEST_ID), startId)
            return START_NOT_STICKY
        }

        val request = downloadCoordinator.takeRequest(intent?.getStringExtra(EXTRA_REQUEST_ID))
        if (request == null) {
            startForeground(
                NOTIFICATION_ID,
                buildProgressNotification(
                    text = localizationManager.currentStrings.value
                        .mangaNotificationNothingToDownload,
                    progress = 0,
                    indeterminate = true
                )
            )
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            buildProgressNotification(
                text = localizationManager.currentStrings.value.mangaStatusDownloadPreparing,
                progress = 0,
                indeterminate = true
            )
        )

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
                    notifyProgress(progress)
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
                mangaVolume = com.cybercat.pocketbooksender.data.manga.mangaChapterQueueVolume(
                    item.chapter
                ),
                seriesIndex = com.cybercat.pocketbooksender.data.manga.mangaChapterQueueSeriesIndex(
                    item.chapter
                ),
                plannedPath = ""
            )
        }

        queueManager.addPreparedItems(items)
    }

    private fun notifyProgress(progress: MangaDownloadProgress) {
        val total = progress.totalChapters.coerceAtLeast(1)
        val current = (progress.completedChapters + 1).coerceIn(1, total)
        val text = localizationManager.currentStrings.value.get(
            "manga_notification_downloading_progress",
            current,
            total
        )
        notificationManager().notify(
            NOTIFICATION_ID,
            buildProgressNotification(
                text = progress.chapterTitle
                    .takeIf { it.isNotBlank() }
                    ?.let { title -> "$text - $title" }
                    ?: text,
                progress = progress.notificationPercent(),
                indeterminate = false
            )
        )
    }

    private fun showFinishedNotification(downloaded: Int, failed: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (AppVisibilityTracker.isAppVisible) return

        val strings = localizationManager.currentStrings.value
        val text = if (failed == 0) {
            strings.get("manga_notification_complete_success", downloaded)
        } else {
            strings.get("manga_notification_complete_with_failures", downloaded, failed)
        }

        notificationManager().notify(
            nextCompletionNotificationId(),
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_upload)
                .setContentTitle(strings.mangaNotificationCompleteTitle)
                .setContentText(text)
                .setContentIntent(contentIntent())
                .setAutoCancel(true)
                .setOngoing(false)
                .build()
        )
    }

    private fun buildProgressNotification(
        text: String,
        progress: Int,
        indeterminate: Boolean
    ): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_upload)
        .setContentTitle(localizationManager.currentStrings.value.mangaNotificationTitle)
        .setContentText(text)
        .setContentIntent(contentIntent())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(100, progress.coerceIn(0, 100), indeterminate)
        .build()

    private fun contentIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            flags
        )
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            localizationManager.currentStrings.value.mangaNotificationTitle,
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "manga_downloads"
        private const val NOTIFICATION_ID = 2201
        private const val COMPLETION_NOTIFICATION_ID_START = 2300
        private const val ACTION_CANCEL_DOWNLOAD =
            "com.cybercat.pocketbooksender.manga.CANCEL_DOWNLOAD"
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val DOWNLOAD_WAKE_LOCK_TIMEOUT_MILLIS = 90 * 60 * 1000L
        private val completionNotificationIds = AtomicInteger(COMPLETION_NOTIFICATION_ID_START)

        private fun nextCompletionNotificationId(): Int =
            completionNotificationIds.getAndIncrement()

        fun createIntent(context: Context, requestId: String): Intent =
            Intent(context, MangaDownloadForegroundService::class.java)
                .putExtra(EXTRA_REQUEST_ID, requestId)

        fun createCancelIntent(context: Context, requestId: String): Intent =
            Intent(context, MangaDownloadForegroundService::class.java)
                .setAction(ACTION_CANCEL_DOWNLOAD)
                .putExtra(EXTRA_REQUEST_ID, requestId)
    }
}

private fun MangaDownloadProgress.notificationPercent(): Int {
    val safeTotalChapters = totalChapters.coerceAtLeast(1)
    val completedChapterCount = completedChapters.coerceIn(0, safeTotalChapters)
    if (completedChapterCount >= safeTotalChapters) return 100

    val archiveTotal = archiveTotalBytes?.takeIf { it > 0L }
    val currentChapterFraction = when {
        archiveTotal != null -> archiveBytesRead.toFloat() / archiveTotal.toFloat()
        totalPages > 0 -> completedPages.toFloat() / totalPages.toFloat()
        else -> 0f
    }.coerceIn(0f, 1f)

    return (((completedChapterCount + currentChapterFraction) / safeTotalChapters) * 100)
        .toInt()
        .coerceIn(0, 99)
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

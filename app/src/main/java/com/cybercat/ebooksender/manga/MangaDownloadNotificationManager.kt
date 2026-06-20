package com.cybercat.ebooksender.manga

import android.app.Service
import com.cybercat.ebooksender.R
import com.cybercat.ebooksender.data.manga.MangaDownloadProgress
import com.cybercat.ebooksender.localization.LocalizationManager
import com.cybercat.ebooksender.notification.ForegroundServiceNotificationController
import com.cybercat.ebooksender.notification.NotificationProgress
import java.util.concurrent.atomic.AtomicInteger

internal class MangaDownloadNotificationManager(
    service: Service,
    private val localizationManager: LocalizationManager
) {
    private val notificationController = ForegroundServiceNotificationController(
        service = service,
        channelId = CHANNEL_ID,
        channelName = { localizationManager.currentStrings.value.mangaNotificationTitle },
        foregroundNotificationId = FOREGROUND_NOTIFICATION_ID,
        smallIconResId = R.drawable.ic_stat_upload,
        nextCompletionNotificationId = ::nextCompletionNotificationId
    )

    fun ensureNotificationChannel() {
        notificationController.ensureNotificationChannel()
    }

    fun startPreparing() {
        startProgress(
            text = localizationManager.currentStrings.value.mangaStatusDownloadPreparing,
            progress = 0,
            indeterminate = true
        )
    }

    fun startNothingToDownload() {
        startProgress(
            text = localizationManager.currentStrings.value.mangaNotificationNothingToDownload,
            progress = 0,
            indeterminate = true
        )
    }

    fun notifyProgress(progress: MangaDownloadProgress) {
        val total = progress.totalChapters.coerceAtLeast(1)
        val current = (progress.completedChapters + 1).coerceIn(1, total)
        val text = localizationManager.currentStrings.value.get(
            "manga_notification_downloading_progress",
            current,
            total
        )
        notificationController.updateProgress(
            progressNotification(
                text = text,
                progress = progress.notificationPercent(),
                indeterminate = false
            )
        )
    }

    fun showFinishedNotification(downloaded: Int, failed: Int) {
        notificationController.finishProgress()

        val strings = localizationManager.currentStrings.value
        val text = if (failed == 0) {
            strings.get("manga_notification_complete_success", downloaded)
        } else {
            strings.get("manga_notification_complete_with_failures", downloaded, failed)
        }

        notificationController.showCompletionNotification(
            title = strings.mangaNotificationCompleteTitle,
            text = text
        )
    }

    fun dispose() {
        notificationController.dispose()
    }

    private fun startProgress(text: String, progress: Int, indeterminate: Boolean) {
        notificationController.startProgress(
            progressNotification(
                text = text,
                progress = progress,
                indeterminate = indeterminate
            )
        )
    }

    private fun progressNotification(text: String, progress: Int, indeterminate: Boolean) =
        notificationController.buildProgressNotification(
            title = localizationManager.currentStrings.value.mangaNotificationTitle,
            text = text,
            progress = NotificationProgress(
                max = 100,
                current = progress.coerceIn(0, 100),
                indeterminate = indeterminate
            )
        )

    companion object {
        private const val CHANNEL_ID = "manga_downloads"
        private const val FOREGROUND_NOTIFICATION_ID = 2201
        private const val COMPLETION_NOTIFICATION_ID_START = 2300
        private val completionNotificationIds = AtomicInteger(COMPLETION_NOTIFICATION_ID_START)

        private fun nextCompletionNotificationId(): Int =
            completionNotificationIds.getAndIncrement()
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

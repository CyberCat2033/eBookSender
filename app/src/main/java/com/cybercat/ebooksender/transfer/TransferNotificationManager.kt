package com.cybercat.ebooksender.transfer

import android.app.Service
import com.cybercat.ebooksender.R
import com.cybercat.ebooksender.localization.LocalizationManager
import com.cybercat.ebooksender.notification.ForegroundServiceNotificationController
import com.cybercat.ebooksender.notification.NotificationProgress
import java.util.concurrent.atomic.AtomicInteger

internal class TransferNotificationManager(
    service: Service,
    private val localizationManager: LocalizationManager
) {
    private val notificationController = ForegroundServiceNotificationController(
        service = service,
        channelId = CHANNEL_ID,
        channelName = { "Book transfers" },
        foregroundNotificationId = FOREGROUND_NOTIFICATION_ID,
        smallIconResId = R.drawable.ic_stat_upload,
        nextCompletionNotificationId = ::nextCompletionNotificationId
    )

    fun ensureNotificationChannel() {
        notificationController.ensureNotificationChannel()
    }

    fun startProgress(text: String, completed: Int, total: Int) {
        notificationController.startProgress(progressNotification(text, completed, total))
    }

    fun notifyProgress(text: String, completed: Int, total: Int) {
        notificationController.updateProgress(progressNotification(text, completed, total))
    }

    fun showFinishedNotification(uploaded: Int, failed: Int) {
        notificationController.finishProgress()

        val strings = localizationManager.currentStrings.value
        val text = if (failed == 0) {
            strings.get("transfer_notification_complete_success", uploaded)
        } else {
            strings.get("transfer_notification_progress_summary", uploaded, failed)
        }

        notificationController.showCompletionNotification(
            title = strings.transferNotificationCompleteTitle,
            text = text
        )
    }

    fun showCanceledNotification(uploaded: Int) {
        notificationController.finishProgress()

        val strings = localizationManager.currentStrings.value
        val text = if (uploaded > 0) {
            strings.get("transfer_notification_canceled_with_uploads", uploaded)
        } else {
            strings.get("transfer_notification_canceled")
        }

        notificationController.showCompletionNotification(
            title = strings.get("transfer_notification_canceled_title"),
            text = text
        )
    }

    fun dispose() {
        notificationController.dispose()
    }

    private fun progressNotification(text: String, completed: Int, total: Int) =
        notificationController.buildProgressNotification(
            title = localizationManager.currentStrings.value.transferNotificationTitle,
            text = text,
            progress = NotificationProgress(
                max = total,
                current = completed,
                indeterminate = total == 0
            )
        )

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 2001

        private const val CHANNEL_ID = "book_transfers"
        private const val COMPLETION_NOTIFICATION_ID_START = 2100
        private val completionNotificationIds = AtomicInteger(COMPLETION_NOTIFICATION_ID_START)

        private fun nextCompletionNotificationId(): Int =
            completionNotificationIds.getAndIncrement()
    }
}

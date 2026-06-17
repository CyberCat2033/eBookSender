package com.cybercat.pocketbooksender.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.cybercat.pocketbooksender.MainActivity
import com.cybercat.pocketbooksender.R
import com.cybercat.pocketbooksender.localization.LocalizationManager
import java.util.concurrent.atomic.AtomicInteger

internal class TransferNotificationManager(
    context: Context,
    private val localizationManager: LocalizationManager
) {
    private val appContext = context.applicationContext

    fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Book transfers",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager().createNotificationChannel(channel)
    }

    fun buildProgressNotification(text: String, completed: Int, total: Int): Notification =
        NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_upload)
            .setContentTitle(localizationManager.currentStrings.value.transferNotificationTitle)
            .setContentText(text)
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, completed, total == 0)
            .build()

    fun notifyProgress(text: String, completed: Int, total: Int) {
        notificationManager().notify(
            FOREGROUND_NOTIFICATION_ID,
            buildProgressNotification(text, completed, total)
        )
    }

    fun showFinishedNotification(uploaded: Int, failed: Int) {
        val strings = localizationManager.currentStrings.value
        val text = if (failed == 0) {
            strings.get("transfer_notification_complete_success", uploaded)
        } else {
            strings.get("transfer_notification_progress_summary", uploaded, failed)
        }

        notificationManager().notify(
            nextCompletionNotificationId(),
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_upload)
                .setContentTitle(strings.transferNotificationCompleteTitle)
                .setContentText(text)
                .setContentIntent(contentIntent())
                .setAutoCancel(true)
                .setOngoing(false)
                .build()
        )
    }

    private fun contentIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java),
            flags
        )
    }

    private fun notificationManager(): NotificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 2001

        private const val CHANNEL_ID = "book_transfers"
        private const val COMPLETION_NOTIFICATION_ID_START = 2100
        private val completionNotificationIds = AtomicInteger(COMPLETION_NOTIFICATION_ID_START)

        private fun nextCompletionNotificationId(): Int =
            completionNotificationIds.getAndIncrement()
    }
}

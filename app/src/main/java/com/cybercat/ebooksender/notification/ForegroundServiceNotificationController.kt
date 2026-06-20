package com.cybercat.ebooksender.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.cybercat.ebooksender.MainActivity
import com.cybercat.ebooksender.lifecycle.AppVisibilityTracker

internal class ForegroundServiceNotificationController(
    private val service: Service,
    private val channelId: String,
    private val channelName: () -> String,
    private val foregroundNotificationId: Int,
    private val smallIconResId: Int,
    private val nextCompletionNotificationId: () -> Int
) {
    private var active = false
    private var foregroundStarted = false
    private var latestProgressNotification: Notification? = null

    private val visibilityListener: (Boolean) -> Unit = { isVisible ->
        if (isVisible) {
            hideProgressNotification()
        } else {
            showLatestProgressNotification()
        }
    }

    fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            channelName(),
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager().createNotificationChannel(channel)
    }

    fun buildProgressNotification(
        title: String,
        text: String,
        progress: NotificationProgress
    ): Notification = NotificationCompat.Builder(service, channelId)
        .setSmallIcon(smallIconResId)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(contentIntent())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(progress.max, progress.current, progress.indeterminate)
        .build()

    fun startProgress(notification: Notification) {
        if (!active) {
            active = true
            AppVisibilityTracker.addVisibilityListener(visibilityListener)
        }
        latestProgressNotification = notification
        service.startForeground(foregroundNotificationId, notification)
        foregroundStarted = true
        AppNotificationVisibilityCleaner.track(foregroundNotificationId)
        if (AppVisibilityTracker.isAppVisible) {
            hideProgressNotification()
        }
    }

    fun updateProgress(notification: Notification) {
        latestProgressNotification = notification
        if (!active) return
        if (AppVisibilityTracker.isAppVisible) {
            hideProgressNotification()
        } else {
            showLatestProgressNotification()
        }
    }

    fun finishProgress() {
        active = false
        latestProgressNotification = null
        AppVisibilityTracker.removeVisibilityListener(visibilityListener)
        hideProgressNotification()
    }

    fun showCompletionNotification(title: String, text: String) {
        if (AppVisibilityTracker.isAppVisible) return

        val notificationId = nextCompletionNotificationId()
        notificationManager().notify(
            notificationId,
            NotificationCompat.Builder(service, channelId)
                .setSmallIcon(smallIconResId)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent())
                .setAutoCancel(true)
                .setOngoing(false)
                .build()
        )
        AppNotificationVisibilityCleaner.track(notificationId)
    }

    fun dispose() {
        finishProgress()
    }

    private fun showLatestProgressNotification() {
        val notification = latestProgressNotification ?: return
        if (foregroundStarted) {
            notificationManager().notify(foregroundNotificationId, notification)
        } else {
            service.startForeground(foregroundNotificationId, notification)
            foregroundStarted = true
        }
        AppNotificationVisibilityCleaner.track(foregroundNotificationId)
    }

    private fun hideProgressNotification() {
        if (foregroundStarted) {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        notificationManager().cancel(foregroundNotificationId)
        AppNotificationVisibilityCleaner.untrack(foregroundNotificationId)
    }

    private fun contentIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags
        )
    }

    private fun notificationManager(): NotificationManager =
        service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}

internal data class NotificationProgress(val max: Int, val current: Int, val indeterminate: Boolean)

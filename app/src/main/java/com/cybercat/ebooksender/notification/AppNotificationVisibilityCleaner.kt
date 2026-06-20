package com.cybercat.ebooksender.notification

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import com.cybercat.ebooksender.lifecycle.AppVisibilityTracker
import java.util.concurrent.CopyOnWriteArraySet

internal object AppNotificationVisibilityCleaner {
    private val notificationIds = CopyOnWriteArraySet<Int>()
    private var appContext: Context? = null
    private var registered = false

    private val visibilityListener: (Boolean) -> Unit = { isVisible ->
        if (isVisible) {
            cancelTrackedNotifications()
        }
    }

    fun register(application: Application) {
        if (registered) return
        registered = true
        appContext = application.applicationContext
        AppVisibilityTracker.addVisibilityListener(visibilityListener)
    }

    fun track(notificationId: Int) {
        notificationIds += notificationId
        if (AppVisibilityTracker.isAppVisible) {
            cancel(notificationId)
        }
    }

    fun untrack(notificationId: Int) {
        notificationIds -= notificationId
    }

    private fun cancelTrackedNotifications() {
        notificationIds.forEach { notificationId ->
            cancel(notificationId)
        }
    }

    private fun cancel(notificationId: Int) {
        val context = appContext ?: return
        notificationManager(context).cancel(notificationId)
        notificationIds -= notificationId
    }

    private fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}

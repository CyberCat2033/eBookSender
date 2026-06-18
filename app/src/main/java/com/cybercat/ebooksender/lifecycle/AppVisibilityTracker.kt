package com.cybercat.ebooksender.lifecycle

import android.app.ActivityManager
import android.app.Application
import android.content.Context

internal object AppVisibilityTracker {
    private var appContext: Context? = null

    val isAppVisible: Boolean
        get() {
            val context = appContext ?: return false
            val info = ActivityManager.RunningAppProcessInfo()
            runCatching {
                ActivityManager.getMyMemoryState(info)
            }
            return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        }

    fun register(application: Application) {
        appContext = application.applicationContext
    }
}

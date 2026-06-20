package com.cybercat.ebooksender.lifecycle

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.util.concurrent.CopyOnWriteArraySet

internal object AppVisibilityTracker {
    private var appContext: Context? = null
    private val listeners = CopyOnWriteArraySet<(Boolean) -> Unit>()

    @Volatile
    private var visibleActivityCount: Int = 0

    @Volatile
    private var registered = false

    @Volatile
    private var visible = false

    val isAppVisible: Boolean
        get() {
            if (visible) return true
            val context = appContext ?: return false
            val info = ActivityManager.RunningAppProcessInfo()
            runCatching {
                ActivityManager.getMyMemoryState(info)
            }
            return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        }

    fun register(application: Application) {
        if (registered) return
        registered = true
        appContext = application.applicationContext
        visible = readProcessVisibility()
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    visibleActivityCount += 1
                    updateVisible(true)
                }

                override fun onActivityStopped(activity: Activity) {
                    visibleActivityCount = (visibleActivityCount - 1).coerceAtLeast(0)
                    updateVisible(visibleActivityCount > 0)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
                    Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) =
                    Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }

    fun addVisibilityListener(listener: (Boolean) -> Unit) {
        listeners += listener
    }

    fun removeVisibilityListener(listener: (Boolean) -> Unit) {
        listeners -= listener
    }

    private fun updateVisible(isVisible: Boolean) {
        if (visible == isVisible) return
        visible = isVisible
        listeners.forEach { listener -> listener(isVisible) }
    }

    private fun readProcessVisibility(): Boolean {
        val context = appContext ?: return false
        val info = ActivityManager.RunningAppProcessInfo()
        runCatching {
            ActivityManager.getMyMemoryState(info)
        }
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }
}

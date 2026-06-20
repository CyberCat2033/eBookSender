package com.cybercat.ebooksender

import android.app.Application
import android.net.http.HttpResponseCache
import com.cybercat.ebooksender.lifecycle.AppVisibilityTracker
import com.cybercat.ebooksender.notification.AppNotificationVisibilityCleaner
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.IOException

@HiltAndroidApp
class EBookSenderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppVisibilityTracker.register(this)
        AppNotificationVisibilityCleaner.register(this)
        installHttpCache()
    }

    private fun installHttpCache() {
        try {
            HttpResponseCache.install(
                File(cacheDir, HTTP_CACHE_DIRECTORY),
                HTTP_CACHE_SIZE_BYTES
            )
        } catch (_: IOException) {
            // Network caching is an optimization; the app must work without it.
        }
    }

    private companion object {
        const val HTTP_CACHE_DIRECTORY = "http-cache"
        const val HTTP_CACHE_SIZE_BYTES = 10L * 1024L * 1024L
    }
}

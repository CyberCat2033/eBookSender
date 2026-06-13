package com.cybercat.pocketbooksender

import android.app.Application
import android.net.http.HttpResponseCache
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.IOException

@HiltAndroidApp
class PocketBookSenderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installHttpCache()
    }

    private fun installHttpCache() {
        try {
            HttpResponseCache.install(
                File(cacheDir, HttpCacheDirectory),
                HttpCacheSizeBytes,
            )
        } catch (_: IOException) {
            // Network caching is an optimization; the app must work without it.
        }
    }

    private companion object {
        const val HttpCacheDirectory = "http-cache"
        const val HttpCacheSizeBytes = 10L * 1024L * 1024L
    }
}

package com.cybercat.pocketbooksender

import android.app.Application
import android.net.http.HttpResponseCache
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.cybercat.pocketbooksender.transfer.ConnectionManager
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.IOException
import javax.inject.Inject

@HiltAndroidApp
class PocketBookSenderApplication : Application() {
    @Inject lateinit var connectionManager: ConnectionManager

    override fun onCreate() {
        super.onCreate()
        installHttpCache()
        observeProcessLifecycle()
    }

    private fun observeProcessLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    connectionManager.onAppForegrounded()
                }

                override fun onStop(owner: LifecycleOwner) {
                    connectionManager.onAppBackgrounded()
                }
            },
        )
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

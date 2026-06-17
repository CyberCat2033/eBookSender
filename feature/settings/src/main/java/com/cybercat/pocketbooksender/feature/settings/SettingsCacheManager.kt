package com.cybercat.pocketbooksender.feature.settings

import android.content.Context
import com.cybercat.pocketbooksender.ui.BitmapCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class SettingsCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun clearDownloadCache(): Long = withContext(Dispatchers.IO) {
        val cacheDirs = listOf(
            File(context.cacheDir, "previews"),
            File(context.cacheDir, "opds"),
            File(context.cacheDir, "manga"),
            File(context.cacheDir, "pocketbook-catalog")
        )

        var totalBytes = 0L
        for (dir in cacheDirs) {
            totalBytes += getFolderSize(dir)
        }

        if (totalBytes > 0L) {
            BitmapCache.clear(context)
            runCatching { File(context.cacheDir, "opds").deleteRecursively() }
            runCatching { File(context.cacheDir, "manga").deleteRecursively() }
            runCatching { File(context.cacheDir, "pocketbook-catalog").deleteRecursively() }
        }

        totalBytes
    }

    private fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        val files = file.listFiles()
        if (files != null) {
            for (f in files) {
                size += getFolderSize(f)
            }
        }
        return size
    }
}

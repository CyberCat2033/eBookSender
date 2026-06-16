package com.cybercat.pocketbooksender.transfer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun load(itemId: String): Bitmap? {
        val file = coverCacheFile(itemId)
        if (!file.isFile) return null

        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()
    }

    fun save(itemId: String, preview: Bitmap) {
        runCatching {
            val file = coverCacheFile(itemId)
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                preview.compress(Bitmap.CompressFormat.JPEG, JpegQuality, out)
            }
        }
    }

    fun cleanup(activeItemIds: Set<String>) {
        cleanupLegacyCaches()

        val dir = coverCacheDir()
        if (!dir.isDirectory) return

        runCatching {
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.nameWithoutExtension !in activeItemIds) {
                    file.delete()
                }
            }
        }
    }

    private fun coverCacheFile(itemId: String): File =
        File(coverCacheDir(), "$itemId.jpg")

    private fun coverCacheDir(): File =
        File(context.filesDir, CoverCacheDirName).apply { mkdirs() }

    private fun cleanupLegacyCaches() {
        LegacyCoverCacheDirNames.forEach { dirName ->
            runCatching {
                File(context.filesDir, dirName).deleteRecursively()
            }
        }
    }

    private companion object {
        const val CoverCacheDirName = "covers-v2"
        const val JpegQuality = 80
        val LegacyCoverCacheDirNames = listOf("covers")
    }
}

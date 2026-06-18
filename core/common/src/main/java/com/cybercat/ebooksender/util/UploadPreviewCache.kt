package com.cybercat.ebooksender.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object UploadPreviewCache {
    const val DEFAULT_REQUEST_WIDTH = 160
    const val DEFAULT_REQUEST_HEIGHT = 220
    @Volatile
    private var legacyCachesCleaned = false

    fun memoryKey(itemId: String): String = "upload-preview:$itemId"

    fun load(
        context: Context,
        itemId: String,
        reqWidth: Int = DEFAULT_REQUEST_WIDTH,
        reqHeight: Int = DEFAULT_REQUEST_HEIGHT
    ): Bitmap? {
        cleanupLegacyCaches(context)

        val file = coverCacheFile(context, itemId)
        if (!file.isFile) return null

        return runCatching {
            decodeSampledFile(file, reqWidth, reqHeight)
                ?: BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                )
        }.getOrNull()
    }

    fun save(context: Context, itemId: String, preview: Bitmap) {
        cleanupLegacyCaches(context)

        runCatching {
            val file = coverCacheFile(context, itemId)
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                preview.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        }
    }

    fun cleanup(context: Context, activeItemIds: Set<String>) {
        cleanupLegacyCaches(context)

        val dir = coverCacheDir(context)
        if (!dir.isDirectory) return

        runCatching {
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.nameWithoutExtension !in activeItemIds) {
                    file.delete()
                }
            }
        }
    }

    private fun decodeSampledFile(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    width = options.outWidth,
                    height = options.outHeight,
                    reqWidth = reqWidth,
                    reqHeight = reqHeight
                )
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        )
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        if (width <= reqWidth && height <= reqHeight) return 1

        var inSampleSize = 1
        var sampledWidth = width
        var sampledHeight = height

        while (sampledWidth / 2 >= reqWidth || sampledHeight / 2 >= reqHeight) {
            inSampleSize *= 2
            sampledWidth /= 2
            sampledHeight /= 2
        }

        return inSampleSize
    }

    private fun coverCacheFile(context: Context, itemId: String): File =
        File(coverCacheDir(context), "$itemId.jpg")

    private fun coverCacheDir(context: Context): File =
        File(context.filesDir, COVER_CACHE_DIR_NAME).apply { mkdirs() }

    private fun cleanupLegacyCaches(context: Context) {
        if (legacyCachesCleaned) return

        synchronized(this) {
            if (legacyCachesCleaned) return

            LEGACY_COVER_CACHE_DIR_NAMES.forEach { dirName ->
                runCatching {
                    File(context.filesDir, dirName).deleteRecursively()
                }
            }
            legacyCachesCleaned = true
        }
    }

    private const val COVER_CACHE_DIR_NAME = "covers-v2"
    private const val JPEG_QUALITY = 80
    private val LEGACY_COVER_CACHE_DIR_NAMES = listOf("covers")
}

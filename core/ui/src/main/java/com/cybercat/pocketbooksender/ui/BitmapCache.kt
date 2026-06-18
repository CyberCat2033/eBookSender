package com.cybercat.pocketbooksender.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.cybercat.pocketbooksender.util.AppConstants
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

object BitmapCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // Use 1/8th of available memory for cache
    private const val MEMORY_ENTRY_TTL_MILLIS = 15 * 60 * 1000L
    private val memoryCacheLock = Any()

    private val memoryCache = object : LruCache<String, BitmapMemoryEntry>(cacheSize) {
        override fun sizeOf(key: String, value: BitmapMemoryEntry): Int =
            value.bitmap.byteCount / 1024
    }

    private fun getCacheDir(context: Context): File = File(context.cacheDir, "previews").apply {
        mkdirs()
    }

    private fun getCacheFile(context: Context, key: String): File {
        val hash = md5(key)
        return File(getCacheDir(context), hash)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun getFromMemory(key: String): Bitmap? = synchronized(memoryCacheLock) {
        trimExpiredMemoryEntriesLocked()
        memoryCache.get(key)?.bitmap
    }

    suspend fun get(context: Context, key: String): Bitmap? = withContext(Dispatchers.IO) {
        // 1. Check memory cache
        getFromMemory(key)?.let { return@withContext it }

        // 2. Check disk cache
        val diskFile = getCacheFile(context, key)
        if (diskFile.exists()) {
            runCatching {
                val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null) {
                    putInMemory(key, bitmap)
                    return@withContext bitmap
                }
            }
        }
        null
    }

    suspend fun put(context: Context, key: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        putInMemory(key, bitmap)

        val diskFile = getCacheFile(context, key)
        runCatching {
            FileOutputStream(diskFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, DISK_JPEG_QUALITY, out)
            }
        }
    }

    fun clear(context: Context) {
        synchronized(memoryCacheLock) {
            memoryCache.evictAll()
        }
        runCatching {
            val dir = getCacheDir(context)
            dir.deleteRecursively()
        }
    }

    fun putInMemory(key: String, bitmap: Bitmap) {
        synchronized(memoryCacheLock) {
            trimExpiredMemoryEntriesLocked()
            memoryCache.put(key, BitmapMemoryEntry(bitmap = bitmap))
        }
    }

    private fun trimExpiredMemoryEntriesLocked(nowMillis: Long = System.currentTimeMillis()) {
        memoryCache.snapshot().forEach { (key, entry) ->
            if (entry.isExpired(nowMillis)) {
                memoryCache.remove(key)
            }
        }
    }

    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun createTempFile(context: Context): File =
        File.createTempFile("preview-", ".tmp", getCacheDir(context))

    fun decodeSampledFile(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private data class BitmapMemoryEntry(
        val bitmap: Bitmap,
        val cachedAtMillis: Long = System.currentTimeMillis()
    ) {
        fun isExpired(nowMillis: Long): Boolean =
            nowMillis - cachedAtMillis > MEMORY_ENTRY_TTL_MILLIS
    }
}

// Global semaphore to limit concurrent image downloads. Only 3 downloads can run in parallel.
private val downloadSemaphore = Semaphore(3)

suspend fun loadCachedRemoteBitmap(
    context: Context,
    url: String,
    cookie: String? = null,
    reqWidth: Int = 200,
    reqHeight: Int = 300
): Bitmap? = withContext(Dispatchers.IO) {
    // 1. Fast check cache without blocking or acquiring permit
    BitmapCache.get(context, url)?.let { return@withContext it }

    // 2. Not in cache, acquire permit to download.
    // If the coroutine is cancelled while waiting, it releases the slot immediately.
    downloadSemaphore.withPermit {
        // Double-check cache in case another task finished downloading this exact URL while we waited
        BitmapCache.get(context, url)?.let { return@withPermit it }

        if (!isActive) return@withPermit null

        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 15_000
                setRequestProperty("Accept", "image/*")
                setRequestProperty("User-Agent", AppConstants.UserAgent)
                if (cookie != null) {
                    setRequestProperty("Cookie", cookie)
                }
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null

                val tempFile = BitmapCache.createTempFile(context)
                try {
                    connection.inputStream.use { stream ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (stream.read(buffer).also { read = it } >= 0) {
                                if (!isActive) return@runCatching null
                                output.write(buffer, 0, read)
                            }
                        }
                    }

                    if (tempFile.length() == 0L || !isActive) return@runCatching null

                    val bitmap = BitmapCache.decodeSampledFile(tempFile, reqWidth, reqHeight)
                    if (bitmap != null) {
                        BitmapCache.put(context, url, bitmap)
                    }
                    bitmap
                } finally {
                    tempFile.delete()
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}

private const val DISK_JPEG_QUALITY = 80

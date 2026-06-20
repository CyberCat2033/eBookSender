package com.cybercat.ebooksender.data.update

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun File.updateFileSha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

suspend fun clearUpdateCacheDirectories(dirs: List<File>): Long = withContext(Dispatchers.IO) {
    val size = dirs.sumOf { dir -> dir.updateFolderSize() }
    dirs.forEach { dir ->
        if (dir.exists()) {
            runCatching { dir.deleteRecursively() }
        }
    }
    size
}

fun String.toSafeUpdateFileName(): String = substringAfterLast('/')
    .replace(Regex("[^A-Za-z0-9._-]"), "_")

private fun File.updateFolderSize(): Long {
    if (!exists()) return 0L
    if (isFile) return length()
    return listFiles().orEmpty().sumOf { it.updateFolderSize() }
}

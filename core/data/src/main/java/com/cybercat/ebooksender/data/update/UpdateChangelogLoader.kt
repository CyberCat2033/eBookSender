package com.cybercat.ebooksender.data.update

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateChangelogLoader {
    suspend fun load(
        changelogUrl: String,
        cacheDir: File,
        versionCode: Long,
        versionName: String,
        languageCode: String,
        userAgent: String
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val cacheFile = cachedChangelogFile(cacheDir, versionCode, languageCode)
            val markdown = if (cacheFile.isFile) {
                cacheFile.readText()
            } else {
                fetchChangelog(changelogUrl, userAgent).also { markdown ->
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.writeText(markdown)
                    cleanupStaleChangelogs(cacheDir, keep = cacheFile)
                }
            }
            UpdateChangelogFormatter.extractVersionChangelog(
                markdown = markdown,
                versionName = versionName
            )
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun fetchChangelog(changelogUrl: String, userAgent: String): String {
        val url = URL(changelogUrl)
        if (url.protocol != "https") {
            throw UpdateChangelogException()
        }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Accept", "text/markdown, text/plain, */*")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw UpdateChangelogException()
            val contentType = connection.contentType.orEmpty().lowercase()
            if (contentType.contains("text/html")) throw UpdateChangelogException()
            return connection.inputStream.use { it.readLimitedText(MAX_CHANGELOG_BYTES) }
        } catch (exception: IOException) {
            throw UpdateChangelogException(exception)
        } finally {
            connection.disconnect()
        }
    }

    private fun cachedChangelogFile(cacheDir: File, versionCode: Long, languageCode: String): File {
        val safeLanguage = languageCode
            .lowercase()
            .replace(Regex("[^a-z0-9._-]"), "_")
            .ifBlank { "en" }
        return File(cacheDir, "$versionCode-$safeLanguage.md")
    }

    private fun cleanupStaleChangelogs(cacheDir: File, keep: File?) {
        cacheDir.listFiles().orEmpty().forEach { file ->
            if (file != keep) runCatching { file.deleteRecursively() }
        }
    }

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val MAX_CHANGELOG_BYTES = 96 * 1024
}

private class UpdateChangelogException(cause: Throwable? = null) : Exception(cause)

private fun java.io.InputStream.readLimitedText(maxBytes: Int): String {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = ByteArrayOutputStream()
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        if (output.size() + read > maxBytes) {
            throw UpdateChangelogException()
        }
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}

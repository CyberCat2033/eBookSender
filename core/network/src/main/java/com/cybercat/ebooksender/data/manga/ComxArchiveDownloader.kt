package com.cybercat.ebooksender.data.manga

import com.cybercat.ebooksender.data.network.runDisconnectingOnCancellation
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Singleton
class ComxArchiveDownloader @Inject constructor(
    private val connectionFactory: ComxHttpConnectionFactory,
    private val sessionManager: ComxMangaSessionManager
) {
    suspend fun downloadChapterArchive(
        chapter: MangaChapter,
        outputFile: File,
        onProgress: suspend (bytesRead: Long, totalBytes: Long?) -> Unit
    ): MangaDownloadedArchive? = withContext(Dispatchers.IO) {
        val downloadUrl = chapter.resolveComxArchiveUrl { newsId, chapterId ->
            requestAuthorizedArchiveUrl(chapter.seriesId, newsId, chapterId)
        } ?: return@withContext null
        val connection = connectionFactory.openConnection(
            url = downloadUrl,
            accept = ARCHIVE_ACCEPT_HEADER,
            referer = chapter.seriesId,
            connectTimeout = ARCHIVE_CONNECT_TIMEOUT_MILLIS,
            readTimeout = ARCHIVE_READ_TIMEOUT_MILLIS
        )
        try {
            connection.runDisconnectingOnCancellation {
                connection.setRequestProperty("Sec-Fetch-Dest", "document")
                connection.setRequestProperty("Sec-Fetch-Mode", "navigate")
                connection.setRequestProperty("Sec-Fetch-Site", "same-site")
                connection.setRequestProperty("Sec-Fetch-User", "?1")
                connection.setRequestProperty("Upgrade-Insecure-Requests", "1")

                val code = connection.responseCode
                sessionManager.captureCookies(connection, downloadUrl)
                if (code == HttpURLConnection.HTTP_FORBIDDEN) {
                    throw MangaBrowserSessionRefreshRequiredException(chapter.seriesId)
                }
                if (code !in 200..299) {
                    throw IOException(
                        "Archive HTTP $code${connection.readErrorSnippet().messageSuffix()}"
                    )
                }

                outputFile.parentFile?.mkdirs()
                val totalBytes = connection.contentLengthLong
                    .takeIf { length -> length > 0L }
                connection.inputStream.use { input ->
                    outputFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_ARCHIVE_BUFFER_SIZE)
                        var bytesRead = 0L
                        var lastReportedBytes = -ARCHIVE_PROGRESS_REPORT_BYTES
                        onProgress(0L, totalBytes)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (
                                bytesRead - lastReportedBytes >= ARCHIVE_PROGRESS_REPORT_BYTES ||
                                bytesRead == totalBytes
                            ) {
                                lastReportedBytes = bytesRead
                                onProgress(bytesRead, totalBytes)
                            }
                        }
                        onProgress(bytesRead, totalBytes)
                    }
                }

                if (outputFile.length() < MIN_ARCHIVE_BYTES) {
                    throw IOException(
                        "Archive response is too small${outputFile.readSmallText().messageSuffix()}"
                    )
                }

                val extension = archiveExtensionFromMagic(outputFile)
                    ?: archiveExtensionFromDisposition(
                        connection.getHeaderField("Content-Disposition")
                    )
                    ?: archiveExtensionFromContentType(connection.contentType)
                    ?: archiveExtensionFromUrl(downloadUrl)
                    ?: chapter.downloadUrl?.let(::archiveExtensionFromUrl)
                    ?: throw IOException(
                        "Archive format is unknown${outputFile.readSmallText().messageSuffix()}"
                    )

                MangaDownloadedArchive(fileExtension = extension)
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun requestAuthorizedArchiveUrl(
        seriesUrl: String,
        newsId: Long,
        chapterId: Long
    ): String {
        val ajaxUrl = ComxMangaAdapter.HOME_URL +
            "engine/ajax/controller.php?mod=api&action=chapters/download"
        val body = listOf(
            "news_id" to newsId.toString(),
            "chapter_id" to chapterId.toString()
        ).toFormEncodedUtf8Body()
        val hadAuthenticatedCookies = sessionManager.hasAuthenticatedCookiesFor(ajaxUrl)

        val connection = connectionFactory.openConnection(
            url = ajaxUrl,
            accept = "application/json, text/javascript, */*; q=0.01",
            referer = seriesUrl,
            connectTimeout = ARCHIVE_AUTH_CONNECT_TIMEOUT_MILLIS,
            readTimeout = ARCHIVE_AUTH_READ_TIMEOUT_MILLIS
        ).apply {
            requestMethod = "POST"
            doOutput = true
            instanceFollowRedirects = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Origin", ComxMangaAdapter.HOME_URL.trimEnd('/'))
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
        }

        return try {
            connection.runDisconnectingOnCancellation {
                connection.outputStream.use { output ->
                    output.write(body)
                }

                val code = connection.responseCode
                sessionManager.captureCookies(connection, ajaxUrl)
                val response = connection.readTextBody()
                if (
                    sessionManager.isExpiredAuthenticatedSession(
                        code = code,
                        url = ajaxUrl,
                        hadAuthenticatedCookies = hadAuthenticatedCookies,
                        html = response
                    )
                ) {
                    sessionManager.clearAuthenticatedCookies()
                    throw MangaAuthenticationExpiredException()
                }
                if (code == HttpURLConnection.HTTP_FORBIDDEN) {
                    throw MangaBrowserSessionRefreshRequiredException(seriesUrl)
                }
                if (code !in 200..299) {
                    throw IOException(
                        "Archive auth HTTP $code${response.errorSnippet().messageSuffix()}"
                    )
                }

                val json = runCatching { Json.parseToJsonElement(response).jsonObject }
                    .getOrElse {
                        throw IOException(
                            "Archive auth response is invalid${response.errorSnippet().messageSuffix()}"
                        )
                    }

                if (!json.booleanValue("success")) {
                    val message = json.firstString("error", "message")
                        .ifBlank { "Com-X login is required for archive download" }
                    throw IOException(message)
                }

                json.firstString("data", "url", "link")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun JsonObject.firstString(vararg keys: String): String {
        keys.forEach { key ->
            get(key).asCleanString().takeIf { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    private fun JsonObject.booleanValue(key: String): Boolean = when (val value = get(key)) {
        is JsonPrimitive ->
            value.booleanOrNull
                ?: value.contentOrNull.equals("true", ignoreCase = true)

        else -> false
    }

    private fun JsonElement?.asCleanString(): String = when (this) {
        is JsonPrimitive -> contentOrNull?.cleanWhitespace().orEmpty()
        is JsonObject -> firstString("url", "src", "href")
        is JsonArray -> firstOrNull().asCleanString()
        else -> ""
    }

    private fun archiveExtensionFromContentType(contentType: String?): String? {
        val normalized = contentType.orEmpty().substringBefore(';').trim().lowercase()
        return when {
            normalized.contains("comicbook-rar") -> "cbr"
            normalized.contains("x-rar") || normalized.contains("rar") -> "cbr"
            normalized.contains("comicbook+zip") -> "cbz"
            normalized.contains("zip") -> "cbz"
            else -> null
        }
    }

    private fun archiveExtensionFromDisposition(disposition: String?): String? {
        val fileName = disposition
            ?.let { value ->
                Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
                    .find(value)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
                    ?: Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
                        .find(value)
                        ?.groupValues
                        ?.getOrNull(1)
            }
            ?.trim()
            .orEmpty()

        return archiveExtensionFromUrl(fileName)
    }

    private fun archiveExtensionFromMagic(file: File): String? {
        val header = ByteArray(8)
        val read = file.inputStream().use { input -> input.read(header) }
        return when {
            read >= 4 &&
                header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] in ZIP_MAGIC_THIRD_BYTES -> "cbz"

            read >= 7 &&
                header[0] == 0x52.toByte() &&
                header[1] == 0x61.toByte() &&
                header[2] == 0x72.toByte() &&
                header[3] == 0x21.toByte() &&
                header[4] == 0x1A.toByte() &&
                header[5] == 0x07.toByte() -> "cbr"

            else -> null
        }
    }

    private fun archiveExtensionFromUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        return when (path.substringAfterLast('.', "").lowercase()) {
            "cbr", "rar" -> "cbr"
            "cbz", "zip" -> "cbz"
            else -> null
        }
    }

    private companion object {
        private const val ARCHIVE_AUTH_CONNECT_TIMEOUT_MILLIS = 10_000
        private const val ARCHIVE_AUTH_READ_TIMEOUT_MILLIS = 20_000
        private const val ARCHIVE_CONNECT_TIMEOUT_MILLIS = 12_000
        private const val ARCHIVE_READ_TIMEOUT_MILLIS = 45_000
        private const val DEFAULT_ARCHIVE_BUFFER_SIZE = 64 * 1024
        private const val ARCHIVE_PROGRESS_REPORT_BYTES = 256L * 1024L
        private const val MIN_ARCHIVE_BYTES = 512L
        private const val ARCHIVE_ACCEPT_HEADER =
            "application/vnd.comicbook-rar,application/vnd.comicbook+zip," +
                "application/x-rar-compressed,application/zip,application/octet-stream,*/*"

        private val ZIP_MAGIC_THIRD_BYTES = setOf(
            0x03.toByte(),
            0x05.toByte(),
            0x07.toByte()
        )
    }
}

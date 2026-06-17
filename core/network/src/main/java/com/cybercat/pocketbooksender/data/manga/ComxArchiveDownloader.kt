package com.cybercat.pocketbooksender.data.manga

import java.io.File
import java.io.IOException
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
        val originalDownloadUrl =
            chapter.downloadUrl?.takeIf { it.isNotBlank() } ?: return@withContext null
        val downloadUrl = requestAuthorizedArchiveUrl(chapter, originalDownloadUrl)
        val connection = connectionFactory.openConnection(
            url = downloadUrl,
            accept = ARCHIVE_ACCEPT_HEADER,
            referer = chapter.seriesId,
            connectTimeout = ARCHIVE_CONNECT_TIMEOUT_MILLIS,
            readTimeout = ARCHIVE_READ_TIMEOUT_MILLIS
        )
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                connection.disconnect()
            }
        }
        try {
            connection.setRequestProperty("Sec-Fetch-Dest", "document")
            connection.setRequestProperty("Sec-Fetch-Mode", "navigate")
            connection.setRequestProperty("Sec-Fetch-Site", "same-site")
            connection.setRequestProperty("Sec-Fetch-User", "?1")
            connection.setRequestProperty("Upgrade-Insecure-Requests", "1")

            val code = connection.responseCode
            sessionManager.captureCookies(connection, downloadUrl)
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
                ?: archiveExtensionFromDisposition(connection.getHeaderField("Content-Disposition"))
                ?: archiveExtensionFromContentType(connection.contentType)
                ?: archiveExtensionFromUrl(downloadUrl)
                ?: archiveExtensionFromUrl(originalDownloadUrl)
                ?: throw IOException(
                    "Archive format is unknown${outputFile.readSmallText().messageSuffix()}"
                )

            MangaDownloadedArchive(fileExtension = extension)
        } catch (error: Throwable) {
            if (
                error !is CancellationException &&
                currentCoroutineContext()[Job]?.isCancelled == true
            ) {
                throw CancellationException(
                    "Manga archive download canceled"
                ).also { cancellation ->
                    cancellation.initCause(error)
                }
            }
            throw error
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }

    private fun requestAuthorizedArchiveUrl(chapter: MangaChapter, fallbackUrl: String): String {
        val newsId = chapter.seriesId.extractNewsId()
            ?: chapter.chapterId.extractReaderNewsId()
            ?: fallbackUrl.extractDownloadNewsId()
            ?: return fallbackUrl
        val chapterId = chapter.chapterId.extractReaderChapterId()
            ?: fallbackUrl.extractDownloadChapterId()
            ?: return fallbackUrl
        val ajaxUrl = ComxMangaAdapter.HomeUrl +
            "engine/ajax/controller.php?mod=api&action=chapters/download"
        val body = listOf(
            "news_id" to newsId.toString(),
            "chapter_id" to chapterId.toString()
        ).toFormEncodedUtf8Body()
        val hadAuthenticatedCookies = sessionManager.hasAuthenticatedCookiesFor(ajaxUrl)

        val connection = connectionFactory.openConnection(
            url = ajaxUrl,
            accept = "application/json, text/javascript, */*; q=0.01",
            referer = chapter.seriesId,
            connectTimeout = ARCHIVE_AUTH_CONNECT_TIMEOUT_MILLIS,
            readTimeout = ARCHIVE_AUTH_READ_TIMEOUT_MILLIS
        ).apply {
            requestMethod = "POST"
            doOutput = true
            instanceFollowRedirects = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Origin", ComxMangaAdapter.HomeUrl.trimEnd('/'))
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
        }

        try {
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

            return json.firstString("data", "url", "link")
                .resolveAgainst(fallbackUrl)
                .takeIf { it.isNotBlank() }
                ?: fallbackUrl
        } finally {
            connection.disconnect()
        }
    }

    private fun String.extractReaderNewsId(): Long? = Regex("""/reader/(\d+)/\d+""").find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()

    private fun String.extractReaderChapterId(): Long? = Regex("""/reader/\d+/(\d+)""").find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()

    private fun String.extractDownloadNewsId(): Long? = Regex("""/download/(\d+)-\d+""").find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()

    private fun String.extractDownloadChapterId(): Long? =
        Regex("""/download/\d+-(\d+)""").find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

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

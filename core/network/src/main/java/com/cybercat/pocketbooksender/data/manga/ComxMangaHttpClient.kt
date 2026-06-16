package com.cybercat.pocketbooksender.data.manga

import android.webkit.CookieManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
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
class ComxMangaHttpClient @Inject constructor(
    private val parser: ComxHtmlParser,
) {
    suspend fun hasAuthenticatedSession(): Boolean = withContext(Dispatchers.IO) {
        cookiesFor(ComxMangaAdapter.HomeUrl)?.hasAuthenticatedCookies() == true
    }

    suspend fun fetchText(
        url: String,
        referer: String,
    ): String = withContext(Dispatchers.IO) {
        fetchText(url, referer, retryGuard = true)
    }

    suspend fun downloadPage(page: MangaPage): MangaDownloadedPage = withContext(Dispatchers.IO) {
        val connection = openConnection(
            url = page.imageUrl,
            accept = "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
            referer = page.refererUrl ?: ComxMangaAdapter.HomeUrl,
            connectTimeout = ImageConnectTimeoutMillis,
            readTimeout = ImageReadTimeoutMillis,
        )
        try {
            val code = connection.responseCode
            captureCookies(connection, page.imageUrl)
            if (code !in 200..299) {
                throw IOException("Image HTTP $code")
            }
            val bytes = connection.readImageBytes()
            val extension = page.fileExtension
                ?: extensionFromContentType(connection.contentType)
                ?: parser.imageExtensionFromUrl(page.imageUrl)
                ?: "jpg"
            MangaDownloadedPage(bytes = bytes, fileExtension = extension)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadChapterArchive(
        chapter: MangaChapter,
        outputFile: File,
        onProgress: suspend (bytesRead: Long, totalBytes: Long?) -> Unit,
    ): MangaDownloadedArchive? = withContext(Dispatchers.IO) {
        val originalDownloadUrl = chapter.downloadUrl?.takeIf { it.isNotBlank() } ?: return@withContext null
        val downloadUrl = requestAuthorizedArchiveUrl(chapter, originalDownloadUrl)
        val connection = openConnection(
            url = downloadUrl,
            accept = "application/vnd.comicbook-rar,application/vnd.comicbook+zip,application/x-rar-compressed,application/zip,application/octet-stream,*/*",
            referer = chapter.seriesId,
            connectTimeout = ArchiveConnectTimeoutMillis,
            readTimeout = ArchiveReadTimeoutMillis,
        )
        try {
            connection.setRequestProperty("Sec-Fetch-Dest", "document")
            connection.setRequestProperty("Sec-Fetch-Mode", "navigate")
            connection.setRequestProperty("Sec-Fetch-Site", "same-site")
            connection.setRequestProperty("Sec-Fetch-User", "?1")
            connection.setRequestProperty("Upgrade-Insecure-Requests", "1")

            val code = connection.responseCode
            captureCookies(connection, downloadUrl)
            if (code !in 200..299) {
                throw IOException("Archive HTTP $code${connection.readErrorSnippet().messageSuffix()}")
            }

            outputFile.parentFile?.mkdirs()
            val totalBytes = connection.contentLengthLong
                .takeIf { length -> length > 0L }
            connection.inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(DefaultArchiveBufferSize)
                    var bytesRead = 0L
                    var lastReportedBytes = -ArchiveProgressReportBytes
                    onProgress(0L, totalBytes)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (
                            bytesRead - lastReportedBytes >= ArchiveProgressReportBytes ||
                            bytesRead == totalBytes
                        ) {
                            lastReportedBytes = bytesRead
                            onProgress(bytesRead, totalBytes)
                        }
                    }
                    onProgress(bytesRead, totalBytes)
                }
            }

            if (outputFile.length() < MinArchiveBytes) {
                throw IOException("Archive response is too small${outputFile.readSmallText().messageSuffix()}")
            }

            val extension = archiveExtensionFromMagic(outputFile)
                ?: archiveExtensionFromDisposition(connection.getHeaderField("Content-Disposition"))
                ?: archiveExtensionFromContentType(connection.contentType)
                ?: archiveExtensionFromUrl(downloadUrl)
                ?: archiveExtensionFromUrl(originalDownloadUrl)
                ?: throw IOException("Archive format is unknown${outputFile.readSmallText().messageSuffix()}")

            MangaDownloadedArchive(fileExtension = extension)
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchText(
        url: String,
        referer: String,
        retryGuard: Boolean,
    ): String {
        val connection = openConnection(
            url = url,
            accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            referer = referer,
        )
        try {
            val code = connection.responseCode
            captureCookies(connection, url)
            val html = connection.readTextBody()
            if (code !in 200..299) {
                throw IOException("HTTP $code")
            }

            if (parser.isGuardChallenge(html)) {
                if (retryGuard && solveGuardChallenge(html, url)) {
                    return fetchText(url, referer, retryGuard = false)
                }
                throw IOException("Com-X session is not ready. Open Login, let the site load, then retry.")
            }

            parser.ensureReadableHtml(html)
            return html
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        url: String,
        accept: String,
        referer: String,
        connectTimeout: Int = ConnectTimeoutMillis,
        readTimeout: Int = ReadTimeoutMillis,
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            this.connectTimeout = connectTimeout
            this.readTimeout = readTimeout
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Language", "ru,en;q=0.8")
            setRequestProperty("Referer", referer)
            setRequestProperty("User-Agent", ComxMangaAdapter.UserAgent)
            cookiesFor(url)?.let { cookie ->
                setRequestProperty("Cookie", cookie)
            }
        }

    private fun requestAuthorizedArchiveUrl(
        chapter: MangaChapter,
        fallbackUrl: String,
    ): String {
        val newsId = chapter.seriesId.extractNewsId()
            ?: chapter.chapterId.extractReaderNewsId()
            ?: fallbackUrl.extractDownloadNewsId()
            ?: return fallbackUrl
        val chapterId = chapter.chapterId.extractReaderChapterId()
            ?: fallbackUrl.extractDownloadChapterId()
            ?: return fallbackUrl
        val ajaxUrl = "${ComxMangaAdapter.HomeUrl}engine/ajax/controller.php?mod=api&action=chapters/download"
        val body = listOf(
            "news_id" to newsId.toString(),
            "chapter_id" to chapterId.toString(),
        ).toFormEncodedUtf8Body()

        val connection = (URL(ajaxUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = ArchiveAuthConnectTimeoutMillis
            readTimeout = ArchiveAuthReadTimeoutMillis
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
            setRequestProperty("Accept-Language", "ru,en;q=0.8")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Origin", ComxMangaAdapter.HomeUrl.trimEnd('/'))
            setRequestProperty("Referer", chapter.seriesId)
            setRequestProperty("User-Agent", ComxMangaAdapter.UserAgent)
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            cookiesFor(ajaxUrl)?.let { cookie ->
                setRequestProperty("Cookie", cookie)
            }
        }

        try {
            connection.outputStream.use { output ->
                output.write(body)
            }

            val code = connection.responseCode
            captureCookies(connection, ajaxUrl)
            val response = connection.readTextBody()
            if (code !in 200..299) {
                throw IOException("Archive auth HTTP $code${response.errorSnippet().messageSuffix()}")
            }

            val json = runCatching { Json.parseToJsonElement(response).jsonObject }
                .getOrElse {
                    throw IOException("Archive auth response is invalid${response.errorSnippet().messageSuffix()}")
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

    private fun solveGuardChallenge(html: String, url: String): Boolean {
        val token = GuardTokenRegex.find(html)?.groupValues?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: return false

        val target = runCatching { URI(url).resolve("/_v").toString() }
            .getOrDefault("${ComxMangaAdapter.HomeUrl}_v")
        val body = listOf(
            "token" to token,
            "mode" to "legacy",
            "workTime" to "601",
            "iterations" to "120000",
            "hasCrypto" to "0",
            "webdriver" to "0",
            "touch" to "1",
            "screen_w" to "1440",
            "screen_h" to "3120",
            "screen_cd" to "24",
            "tz" to "-180",
            "dpr" to "3",
            "cdp" to "0",
            "cdpf" to "",
        ).toFormEncodedUtf8Body()

        val connection = (URL(target).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = GuardConnectTimeoutMillis
            readTimeout = GuardReadTimeoutMillis
            instanceFollowRedirects = true
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Language", "ru,en;q=0.8")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Origin", ComxMangaAdapter.HomeUrl.trimEnd('/'))
            setRequestProperty("Referer", url)
            setRequestProperty("User-Agent", ComxMangaAdapter.UserAgent)
            cookiesFor(url)?.let { cookie ->
                setRequestProperty("Cookie", cookie)
            }
        }

        return try {
            connection.outputStream.use { output ->
                output.write(body)
            }
            val code = connection.responseCode
            captureCookies(connection, target)
            val response = connection.readTextBody()
            code in 200..299 && response.contains("OK", ignoreCase = true)
        } catch (_: IOException) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun captureCookies(connection: HttpURLConnection, url: String) {
        connection.headerFields
            .filterKeys { key -> key != null && key.equals("Set-Cookie", ignoreCase = true) }
            .values
            .flatten()
            .forEach { cookie ->
                CookieManager.getInstance().setCookie(url, cookie)
                CookieManager.getInstance().setCookie(ComxMangaAdapter.HomeUrl, cookie)
            }
        CookieManager.getInstance().flush()
    }

    private fun cookiesFor(url: String): String? {
        val cookies = listOfNotNull(
            CookieManager.getInstance().getCookie(url),
            CookieManager.getInstance().getCookie(ComxMangaAdapter.HomeUrl),
        )
            .flatMap { cookieHeader -> cookieHeader.split(';') }
            .map { cookie -> cookie.trim() }
            .filter { cookie -> cookie.isNotBlank() && cookie.contains('=') }
            .distinctBy { cookie -> cookie.substringBefore('=').trim() }

        return cookies.joinToString("; ").takeIf { it.isNotBlank() }
    }

    private fun String.hasAuthenticatedCookies(): Boolean {
        val cookieNames = split(';')
            .map { cookie -> cookie.substringBefore('=').trim().lowercase() }
            .filter { name -> name.isNotBlank() }
            .toSet()

        return DleUserIdCookieName in cookieNames &&
            DlePasswordCookieName in cookieNames
    }

    private fun HttpURLConnection.readTextBody(): String {
        val stream = if (responseCode in 200..399) {
            inputStream
        } else {
            errorStream ?: inputStream
        }
        return stream.bufferedReader().use { reader -> reader.readText() }
    }

    private fun HttpURLConnection.readErrorSnippet(): String =
        runCatching {
            (errorStream ?: inputStream)
                .bufferedReader()
                .use { reader -> reader.readText() }
                .errorSnippet()
        }.getOrDefault("")

    private fun File.readSmallText(): String =
        takeIf { it.exists() && it.length() in 1..MaxSmallTextBytes }
            ?.let { file ->
                runCatching { file.readText(Charsets.UTF_8).errorSnippet() }
                    .getOrDefault("")
            }
            .orEmpty()

    private fun String.messageSuffix(): String =
        takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()

    private fun HttpURLConnection.readImageBytes(): ByteArray {
        val expectedLength = contentLengthLong
        if (expectedLength > MaxImageBytes) {
            throw IOException("Image is too large: $expectedLength bytes")
        }

        val initialSize = when {
            expectedLength in 1..MaxImageBytes -> expectedLength.toInt()
            else -> DefaultImageBufferSize
        }
        val deadline = System.nanoTime() + ImageTotalReadTimeoutNanos
        val output = ByteArrayOutputStream(initialSize)
        val buffer = ByteArray(DefaultImageBufferSize)

        inputStream.use { input ->
            while (true) {
                if (System.nanoTime() > deadline) {
                    throw IOException("Image read timeout")
                }

                val read = input.read(buffer)
                if (read < 0) break

                output.write(buffer, 0, read)
                if (output.size() > MaxImageBytes) {
                    throw IOException("Image is too large")
                }
            }
        }

        val bytes = output.toByteArray()
        if (bytes.isEmpty()) {
            throw IOException("Image is empty")
        }
        return bytes
    }

    private fun String.extractNewsId(): Long? =
        Regex("""/(\d+)-""").find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

    private fun String.extractReaderNewsId(): Long? =
        Regex("""/reader/(\d+)/\d+""").find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

    private fun String.extractReaderChapterId(): Long? =
        Regex("""/reader/\d+/(\d+)""").find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

    private fun String.extractDownloadNewsId(): Long? =
        Regex("""/download/(\d+)-\d+""").find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

    private fun String.extractDownloadChapterId(): Long? =
        Regex("""/download/\d+-(\d+)""").find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

    private fun String.cleanWhitespace(): String =
        replace('\u00A0', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun String.errorSnippet(): String =
        cleanWhitespace().take(MaxErrorSnippetLength)

    private fun String.resolveAgainst(baseUrl: String): String {
        val raw = replace("\\/", "/").trim()
        if (raw.isBlank()) return ""
        return runCatching { URI(baseUrl).resolve(raw).toString() }.getOrDefault(raw)
    }

    private fun JsonObject.firstString(vararg keys: String): String {
        keys.forEach { key ->
            get(key).asCleanString().takeIf { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    private fun JsonObject.booleanValue(key: String): Boolean =
        when (val value = get(key)) {
            is JsonPrimitive -> value.booleanOrNull ?: value.contentOrNull.equals("true", ignoreCase = true)
            else -> false
        }

    private fun JsonElement?.asCleanString(): String =
        when (this) {
            is JsonPrimitive -> contentOrNull?.cleanWhitespace().orEmpty()
            is JsonObject -> firstString("url", "src", "href")
            is JsonArray -> firstOrNull().asCleanString()
            else -> ""
        }

    private fun extensionFromContentType(contentType: String?): String? {
        val normalized = contentType.orEmpty().substringBefore(';').trim().lowercase()
        return when (normalized) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> null
        }
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
                header[2] in ZipMagicThirdBytes -> "cbz"
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
        private const val ConnectTimeoutMillis = 15_000
        private const val ReadTimeoutMillis = 30_000
        private const val GuardConnectTimeoutMillis = 15_000
        private const val GuardReadTimeoutMillis = 30_000
        private const val ImageConnectTimeoutMillis = 8_000
        private const val ImageReadTimeoutMillis = 8_000
        private const val ImageTotalReadTimeoutMillis = 18_000L
        private const val ImageTotalReadTimeoutNanos = ImageTotalReadTimeoutMillis * 1_000_000L
        private const val ArchiveAuthConnectTimeoutMillis = 10_000
        private const val ArchiveAuthReadTimeoutMillis = 20_000
        private const val ArchiveConnectTimeoutMillis = 12_000
        private const val ArchiveReadTimeoutMillis = 45_000
        private const val DefaultArchiveBufferSize = 64 * 1024
        private const val ArchiveProgressReportBytes = 256L * 1024L
        private const val DefaultImageBufferSize = 32 * 1024
        private const val MaxImageBytes = 50L * 1024L * 1024L
        private const val MinArchiveBytes = 512L
        private const val MaxSmallTextBytes = 4096L
        private const val MaxErrorSnippetLength = 180

        private val ZipMagicThirdBytes = setOf(
            0x03.toByte(),
            0x05.toByte(),
            0x07.toByte(),
        )
        private val GuardTokenRegex = Regex("""token:\s*["']([^"']+)["']""")
        private const val DleUserIdCookieName = "dle_user_id"
        private const val DlePasswordCookieName = "dle_password"
    }
}

package com.cybercat.ebooksender.data.manga

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

@Singleton
class ComxMangaHttpClient @Inject constructor(
    private val parser: ComxHtmlParser,
    private val connectionFactory: ComxHttpConnectionFactory,
    private val sessionManager: ComxMangaSessionManager,
    private val guardChallengeClient: ComxGuardChallengeClient,
    private val archiveDownloader: ComxArchiveDownloader
) {
    suspend fun hasAuthenticatedSession(): Boolean = withContext(Dispatchers.IO) {
        sessionManager.hasAuthenticatedSession()
    }

    suspend fun fetchText(url: String, referer: String): String = withContext(Dispatchers.IO) {
        fetchText(url, referer, retryGuard = true)
    }

    suspend fun downloadPage(page: MangaPage): MangaDownloadedPage = withContext(Dispatchers.IO) {
        val connection = connectionFactory.openConnection(
            url = page.imageUrl,
            accept = "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
            referer = page.refererUrl ?: ComxMangaAdapter.HOME_URL,
            connectTimeout = IMAGE_CONNECT_TIMEOUT_MILLIS,
            readTimeout = IMAGE_READ_TIMEOUT_MILLIS
        )
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                connection.disconnect()
            }
        }
        try {
            val code = connection.responseCode
            sessionManager.captureCookies(connection, page.imageUrl)
            if (code == HttpURLConnection.HTTP_FORBIDDEN) {
                throw MangaBrowserSessionRefreshRequiredException(
                    page.refererUrl ?: ComxMangaAdapter.HOME_URL
                )
            }
            if (code !in 200..299) {
                throw IOException("Image HTTP $code")
            }
            val bytes = connection.readImageBytes()
            val extension = page.fileExtension
                ?: extensionFromContentType(connection.contentType)
                ?: parser.imageExtensionFromUrl(page.imageUrl)
                ?: "jpg"
            MangaDownloadedPage(bytes = bytes, fileExtension = extension)
        } catch (error: Throwable) {
            if (
                error !is CancellationException &&
                currentCoroutineContext()[Job]?.isCancelled == true
            ) {
                throw CancellationException("Manga page download canceled").also { cancellation ->
                    cancellation.initCause(error)
                }
            }
            throw error
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }

    suspend fun downloadChapterArchive(
        chapter: MangaChapter,
        outputFile: File,
        onProgress: suspend (bytesRead: Long, totalBytes: Long?) -> Unit
    ): MangaDownloadedArchive? =
        archiveDownloader.downloadChapterArchive(chapter, outputFile, onProgress)

    private fun fetchText(url: String, referer: String, retryGuard: Boolean): String {
        var currentUrl = url
        repeat(MAX_TEXT_REDIRECTS + 1) { redirectIndex ->
            val hadAuthenticatedCookies = sessionManager.hasAuthenticatedCookiesFor(currentUrl)
            val connection = connectionFactory.openConnection(
                url = currentUrl,
                accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                referer = referer,
                followRedirects = false
            )
            try {
                val code = connection.responseCode
                sessionManager.captureCookies(connection, currentUrl)

                if (code in HTTP_REDIRECT_CODES) {
                    val location = connection.getHeaderField("Location")
                        ?.takeIf { it.isNotBlank() }
                        ?: throw IOException("HTTP $code")
                    currentUrl = location.resolveAgainst(currentUrl)
                    if (redirectIndex >= MAX_TEXT_REDIRECTS) {
                        throw IOException("Too many HTTP redirects")
                    }
                    return@repeat
                }

                val html = connection.readTextBody()
                if (
                    sessionManager.isExpiredAuthenticatedSession(
                        code = code,
                        url = currentUrl,
                        hadAuthenticatedCookies = hadAuthenticatedCookies,
                        html = html
                    )
                ) {
                    sessionManager.clearAuthenticatedCookies()
                    throw MangaAuthenticationExpiredException()
                }

                if (code !in 200..299) {
                    throw IOException("HTTP $code")
                }

                if (parser.isGuardChallenge(html) || currentUrl.isComxBrowserChallengeUrl()) {
                    if (retryGuard && guardChallengeClient.solveGuardChallenge(html, currentUrl)) {
                        return fetchText(url, referer, retryGuard = false)
                    }
                    throw MangaBrowserSessionRefreshRequiredException(currentUrl)
                }

                parser.ensureReadableHtml(html)
                return html
            } finally {
                connection.disconnect()
            }
        }

        throw IOException("Too many HTTP redirects")
    }

    private fun HttpURLConnection.readImageBytes(): ByteArray {
        val expectedLength = contentLengthLong
        if (expectedLength > MAX_IMAGE_BYTES) {
            throw IOException("Image is too large: $expectedLength bytes")
        }

        val initialSize = when {
            expectedLength in 1..MAX_IMAGE_BYTES -> expectedLength.toInt()
            else -> DEFAULT_IMAGE_BUFFER_SIZE
        }
        val deadline = System.nanoTime() + IMAGE_TOTAL_READ_TIMEOUT_NANOS
        val output = ByteArrayOutputStream(initialSize)
        val buffer = ByteArray(DEFAULT_IMAGE_BUFFER_SIZE)

        inputStream.use { input ->
            while (true) {
                if (System.nanoTime() > deadline) {
                    throw IOException("Image read timeout")
                }

                val read = input.read(buffer)
                if (read < 0) break

                output.write(buffer, 0, read)
                if (output.size() > MAX_IMAGE_BYTES) {
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

    private companion object {
        private const val MAX_TEXT_REDIRECTS = 5
        private const val IMAGE_CONNECT_TIMEOUT_MILLIS = 8_000
        private const val IMAGE_READ_TIMEOUT_MILLIS = 8_000
        private const val IMAGE_TOTAL_READ_TIMEOUT_MILLIS = 18_000L
        private const val IMAGE_TOTAL_READ_TIMEOUT_NANOS =
            IMAGE_TOTAL_READ_TIMEOUT_MILLIS * 1_000_000L
        private const val DEFAULT_IMAGE_BUFFER_SIZE = 32 * 1024
        private const val MAX_IMAGE_BYTES = 50L * 1024L * 1024L

        private val HTTP_REDIRECT_CODES = setOf(
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_SEE_OTHER,
            307,
            308
        )
    }
}

private fun String.isComxBrowserChallengeUrl(): Boolean =
    contains("://com-x.life/_c", ignoreCase = true) ||
        contains("://www.com-x.life/_c", ignoreCase = true) ||
        contains("://com-x.life/_v", ignoreCase = true) ||
        contains("://www.com-x.life/_v", ignoreCase = true)

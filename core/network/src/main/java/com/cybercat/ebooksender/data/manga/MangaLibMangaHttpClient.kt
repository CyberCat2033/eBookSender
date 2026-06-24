package com.cybercat.ebooksender.data.manga

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class MangaLibMangaHttpClient @Inject constructor(
    private val connectionFactory: MangaLibHttpConnectionFactory,
    private val sessionManager: MangaLibMangaSessionManager,
    private val parser: MangaLibParser
) {
    suspend fun fetchJson(url: String, referer: String): JSONObject = withContext(Dispatchers.IO) {
        sessionManager.restorePersistedCookies()
        val connection = connectionFactory.openConnection(
            url = url,
            accept = "application/json,text/plain,*/*",
            referer = referer
        )
        try {
            val code = connection.responseCode
            sessionManager.captureCookies(connection, url)
            val body = connection.readTextBody()
            when {
                code == HttpURLConnection.HTTP_FORBIDDEN || isMangaLibGuardResponse(body) ->
                    throw MangaBrowserSessionRefreshRequiredException(MangaLibMangaAdapter.HOME_URL)

                code == HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    sessionManager.clear()
                    throw MangaAuthenticationExpiredException()
                }

                code == HttpURLConnection.HTTP_NOT_FOUND ->
                    throw MangaNotFoundException(code, "HTTP $code")

                code !in 200..299 ->
                    throw IOException("MangaLib API HTTP $code")
            }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadPage(page: MangaPage): MangaDownloadedPage = withContext(Dispatchers.IO) {
        sessionManager.restorePersistedCookies()
        val connection = connectionFactory.openConnection(
            url = page.imageUrl,
            accept = "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
            referer = page.refererUrl ?: MangaLibMangaAdapter.HOME_URL,
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
                    page.refererUrl ?: MangaLibMangaAdapter.HOME_URL
                )
            }
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                sessionManager.clear()
                throw MangaAuthenticationExpiredException()
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
        private const val IMAGE_CONNECT_TIMEOUT_MILLIS = 8_000
        private const val IMAGE_READ_TIMEOUT_MILLIS = 8_000
        private const val IMAGE_TOTAL_READ_TIMEOUT_MILLIS = 18_000L
        private const val IMAGE_TOTAL_READ_TIMEOUT_NANOS =
            IMAGE_TOTAL_READ_TIMEOUT_MILLIS * 1_000_000L
        private const val DEFAULT_IMAGE_BUFFER_SIZE = 32 * 1024
        private const val MAX_IMAGE_BYTES = 50L * 1024L * 1024L
    }
}

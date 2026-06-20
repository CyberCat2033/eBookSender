package com.cybercat.ebooksender.data.update

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class UpdateManifestLoader(private val json: Json) {
    fun <T> load(request: UpdateManifestRequest<T>): T {
        val url = URL(request.url)
        if (url.protocol != HTTPS_PROTOCOL) {
            throw request.invalidManifestException(null)
        }

        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = request.connectTimeoutMs
            readTimeout = request.readTimeoutMs
            instanceFollowRedirects = true
            requestMethod = GET_METHOD
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("User-Agent", request.userAgent)
        }

        try {
            val code = connection.responseCode
            if (code !in SUCCESS_CODES) {
                throw request.networkException(null)
            }
            val body = connection.inputStream.use { input ->
                input.readLimitedText(
                    maxBytes = request.maxBytes,
                    tooLargeException = request.invalidManifestException
                )
            }
            return json.decodeFromString(request.serializer, body)
        } catch (exception: SerializationException) {
            throw request.invalidManifestException(exception)
        } catch (exception: IOException) {
            throw request.networkException(exception)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val HTTPS_PROTOCOL = "https"
        const val GET_METHOD = "GET"
        val SUCCESS_CODES = 200..299
    }
}

class UpdateManifestRequest<T>(
    val url: String,
    val serializer: KSerializer<T>,
    val userAgent: String,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val maxBytes: Int,
    val invalidManifestException: (Throwable?) -> Exception,
    val networkException: (Throwable?) -> Exception
)

private fun java.io.InputStream.readLimitedText(
    maxBytes: Int,
    tooLargeException: (Throwable?) -> Exception
): String {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = ByteArrayOutputStream()
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        if (output.size() + read > maxBytes) {
            throw tooLargeException(null)
        }
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}

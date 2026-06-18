package com.cybercat.ebooksender.data.opds

import com.cybercat.ebooksender.util.AppConstants
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Singleton
class OpdsHttpClient @Inject constructor(private val credentialsProvider: OpdsCredentialsProvider) {

    suspend fun openConnection(
        url: String,
        accept: String,
        redirectsLeft: Int = MAX_REDIRECTS
    ): HttpURLConnection {
        val credentials =
            getCredentialsFromUrl(url) ?: credentialsProvider.getCredentialsForUrl(url)
        val cleanedUrl = if (url.contains("@")) {
            runCatching {
                val uri = URI(url)
                val cleanUri =
                    URI(uri.scheme, null, uri.host, uri.port, uri.path, uri.query, uri.fragment)
                cleanUri.toString()
            }.getOrDefault(url)
        } else {
            url
        }

        val connection = (URL(cleanedUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", AppConstants.UserAgent)
            if (credentials != null) {
                val authString = "${credentials.first}:${credentials.second}"
                val authHeaderValue = "Basic " + android.util.Base64.encodeToString(
                    authString.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )
                setRequestProperty("Authorization", authHeaderValue)
            }
        }

        val code = try {
            connection.runDisconnectingOnCancellation { ensureActive ->
                ensureActive()
                connection.responseCode
            }
        } catch (error: IOException) {
            try {
                currentCoroutineContext().ensureActive()
            } catch (cancellation: CancellationException) {
                throw CancellationException("OPDS request canceled").also { cancellation ->
                    cancellation.initCause(error)
                }
            }
            throw error
        }
        if (code in 300..399 && redirectsLeft > 0) {
            val location = connection.getHeaderField("Location")
            connection.disconnect()

            if (location.isNullOrBlank()) {
                throw IOException("HTTP redirect without Location")
            }

            return openConnection(
                url = OpdsUrlResolver.resolveUrl(url, location),
                accept = accept,
                redirectsLeft = redirectsLeft - 1
            )
        }

        if (code == HttpURLConnection.HTTP_UNAUTHORIZED ||
            code == HttpURLConnection.HTTP_FORBIDDEN
        ) {
            connection.disconnect()
            throw OpdsAuthenticationRequiredException(cleanedUrl)
        }
        if (code !in 200..299) {
            val message = connection.responseMessage?.takeIf { it.isNotBlank() }
            connection.disconnect()
            throw IOException("HTTP $code${message?.let { ": $it" }.orEmpty()}")
        }

        return connection
    }

    private fun getCredentialsFromUrl(urlStr: String): Pair<String, String>? = runCatching {
        val uri = URI(urlStr)
        val userInfo = uri.userInfo
        if (!userInfo.isNullOrBlank()) {
            val parts = userInfo.split(':', limit = 2)
            if (parts.size == 2) {
                Pair(parts[0], parts[1])
            } else {
                null
            }
        } else {
            null
        }
    }.getOrNull()

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 45_000
        const val MAX_REDIRECTS = 5
    }
}

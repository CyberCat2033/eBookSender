package com.cybercat.ebooksender.data.manga

import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComxHttpConnectionFactory @Inject constructor(
    private val sessionManager: ComxMangaSessionManager
) {
    fun openConnection(
        url: String,
        accept: String,
        referer: String,
        connectTimeout: Int = CONNECT_TIMEOUT_MILLIS,
        readTimeout: Int = READ_TIMEOUT_MILLIS,
        followRedirects: Boolean = true
    ): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        this.connectTimeout = connectTimeout
        this.readTimeout = readTimeout
        instanceFollowRedirects = followRedirects
        setRequestProperty("Accept", accept)
        setRequestProperty("Accept-Language", "ru,en;q=0.8")
        setRequestProperty("Referer", referer)
        setRequestProperty("User-Agent", ComxMangaAdapter.UserAgent)
        sessionManager.cookiesFor(url)?.let { cookie ->
            setRequestProperty("Cookie", cookie)
        }
    }

    private companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 15_000
        private const val READ_TIMEOUT_MILLIS = 30_000
    }
}

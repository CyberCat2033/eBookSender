package com.cybercat.ebooksender.data.manga

import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaLibHttpConnectionFactory @Inject constructor(
    private val sessionManager: MangaLibMangaSessionManager,
    private val userAgentProvider: ComxUserAgentProvider
) {
    fun openConnection(
        url: String,
        accept: String,
        referer: String,
        connectTimeout: Int = CONNECT_TIMEOUT_MILLIS,
        readTimeout: Int = READ_TIMEOUT_MILLIS
    ): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        this.connectTimeout = connectTimeout
        this.readTimeout = readTimeout
        instanceFollowRedirects = true
        setRequestProperty("Accept", accept)
        setRequestProperty("Accept-Language", "ru,en;q=0.8")
        setRequestProperty("Referer", referer)
        setRequestProperty("User-Agent", userAgentProvider.userAgent)
        setRequestProperty("Site-Id", MangaLibMangaAdapter.SITE_ID)
        setRequestProperty("Client-Time-Zone", "Europe/Moscow")
        sessionManager.cookiesFor(url)?.let { cookie ->
            setRequestProperty("Cookie", cookie)
        }
    }

    private companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 15_000
        private const val READ_TIMEOUT_MILLIS = 30_000
    }
}

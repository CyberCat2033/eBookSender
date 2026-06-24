package com.cybercat.ebooksender.data.manga

import android.webkit.CookieManager
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaLibMangaSessionManager @Inject constructor(
    private val secretStore: MangaLibSessionSecretStore
) {
    suspend fun restorePersistedCookies() {
        val cookieHeader = secretStore.readCookieHeader() ?: return
        cookieHeader.split(';')
            .map { cookie -> cookie.trim() }
            .filter { cookie -> cookie.isNotBlank() && cookie.contains('=') }
            .forEach { cookie ->
                MangaLibCookieUrls.forEach { url ->
                    CookieManager.getInstance().setCookie(url, cookie)
                }
            }
        CookieManager.getInstance().flush()
    }

    fun cookiesFor(url: String): String? {
        val cookies = (MangaLibCookieUrls + url)
            .flatMap { cookieUrl ->
                CookieManager.getInstance().getCookie(cookieUrl)
                    .orEmpty()
                    .split(';')
            }
            .map { cookie -> cookie.trim() }
            .filter { cookie -> cookie.isNotBlank() && cookie.contains('=') }
            .distinctBy { cookie -> cookie.substringBefore('=').trim() }

        return cookies.joinToString("; ").takeIf { it.isNotBlank() }
    }

    suspend fun captureCookies(connection: HttpURLConnection, url: String) {
        val setCookies = connection.headerFields
            .filterKeys { key -> key != null && key.equals("Set-Cookie", ignoreCase = true) }
            .values
            .flatten()
        if (setCookies.isEmpty()) return

        setCookies.forEach { cookie ->
            CookieManager.getInstance().setCookie(url, cookie)
            MangaLibCookieUrls.forEach { cookieUrl ->
                CookieManager.getInstance().setCookie(cookieUrl, cookie)
            }
        }
        CookieManager.getInstance().flush()
        cookiesFor(url)?.let { cookieHeader -> secretStore.saveCookieHeader(cookieHeader) }
    }

    suspend fun clear() {
        MangaLibCookieUrls.forEach { url ->
            MANGA_LIB_COOKIE_NAMES.forEach { name ->
                CookieManager.getInstance().setCookie(
                    url,
                    "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
                )
            }
        }
        CookieManager.getInstance().flush()
        secretStore.clear()
    }

    private companion object {
        val MangaLibCookieUrls = listOf(
            MangaLibMangaAdapter.HOME_URL,
            MangaLibMangaAdapter.API_BASE_URL,
            MangaLibMangaAdapter.IMAGE_BASE_URL
        )

        val MANGA_LIB_COOKIE_NAMES = listOf(
            "__ddg1_",
            "__ddg8_",
            "__ddg9_",
            "__ddg10_",
            "XSRF-TOKEN",
            "mangalib_session"
        )
    }
}

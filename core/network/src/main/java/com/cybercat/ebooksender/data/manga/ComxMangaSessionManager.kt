package com.cybercat.ebooksender.data.manga

import android.webkit.CookieManager
import java.net.HttpURLConnection
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComxMangaSessionManager @Inject constructor() {
    fun hasAuthenticatedSession(): Boolean =
        cookiesFor(ComxMangaAdapter.HOME_URL)?.hasAuthenticatedCookies() == true

    fun cookiesFor(url: String): String? {
        val cookies = listOfNotNull(
            CookieManager.getInstance().getCookie(url),
            CookieManager.getInstance().getCookie(ComxMangaAdapter.HOME_URL)
        )
            .flatMap { cookieHeader -> cookieHeader.split(';') }
            .map { cookie -> cookie.trim() }
            .filter { cookie -> cookie.isNotBlank() && cookie.contains('=') }
            .distinctBy { cookie -> cookie.substringBefore('=').trim() }

        return cookies.joinToString("; ").takeIf { it.isNotBlank() }
    }

    fun captureCookies(connection: HttpURLConnection, url: String) {
        connection.headerFields
            .filterKeys { key -> key != null && key.equals("Set-Cookie", ignoreCase = true) }
            .values
            .flatten()
            .forEach { cookie ->
                CookieManager.getInstance().setCookie(url, cookie)
                CookieManager.getInstance().setCookie(ComxMangaAdapter.HOME_URL, cookie)
            }
        CookieManager.getInstance().flush()
    }

    fun clearAuthenticatedCookies() {
        AUTH_COOKIE_EXPIRE_HEADERS.forEach { cookie ->
            CookieManager.getInstance().setCookie(ComxMangaAdapter.HOME_URL, cookie)
        }
        CookieManager.getInstance().flush()
    }

    fun hasAuthenticatedCookiesFor(url: String): Boolean =
        cookiesFor(url)?.hasAuthenticatedCookies() == true

    fun isExpiredAuthenticatedSession(
        code: Int,
        url: String,
        hadAuthenticatedCookies: Boolean,
        html: String
    ): Boolean {
        if (!hadAuthenticatedCookies) return false
        if (
            code == HttpURLConnection.HTTP_UNAUTHORIZED ||
            code == HttpURLConnection.HTTP_FORBIDDEN
        ) {
            return true
        }
        return code == HttpURLConnection.HTTP_NOT_FOUND &&
            (url.isComxAuthSensitiveEndpoint() || html.looksLikeComxErrorPage())
    }

    private fun String.hasAuthenticatedCookies(): Boolean {
        val cookieNames = split(';')
            .map { cookie -> cookie.substringBefore('=').trim().lowercase() }
            .filter { name -> name.isNotBlank() }
            .toSet()

        return DLE_USER_ID_COOKIE_NAME in cookieNames &&
            DLE_PASSWORD_COOKIE_NAME in cookieNames
    }

    private fun String.isComxAuthSensitiveEndpoint(): Boolean {
        val path = runCatching { URI(this).path.orEmpty() }.getOrDefault(this)
        return path.startsWith("/search/") ||
            contains("engine/ajax/controller.php", ignoreCase = true)
    }

    private fun String.looksLikeComxErrorPage(): Boolean {
        val normalized = cleanWhitespace()
        return normalized.contains("HTTP 404", ignoreCase = true) ||
            normalized.contains("Ошибка 404", ignoreCase = true) ||
            normalized.contains("страница не найдена", ignoreCase = true) ||
            normalized.contains("page not found", ignoreCase = true)
    }

    private companion object {
        private const val DLE_USER_ID_COOKIE_NAME = "dle_user_id"
        private const val DLE_PASSWORD_COOKIE_NAME = "dle_password"

        private val AUTH_COOKIE_EXPIRE_HEADERS = listOf(
            "$DLE_USER_ID_COOKIE_NAME=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/",
            "$DLE_PASSWORD_COOKIE_NAME=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/",
            "$DLE_USER_ID_COOKIE_NAME=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Domain=.com-x.life; Path=/",
            "$DLE_PASSWORD_COOKIE_NAME=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Domain=.com-x.life; Path=/"
        )
    }
}

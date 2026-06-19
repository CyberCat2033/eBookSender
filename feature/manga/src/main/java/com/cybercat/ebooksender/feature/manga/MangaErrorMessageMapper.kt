package com.cybercat.ebooksender.feature.manga

import com.cybercat.ebooksender.data.manga.MANGA_AUTHENTICATION_EXPIRED_MESSAGE
import com.cybercat.ebooksender.data.manga.MANGA_BROWSER_SESSION_REFRESH_REQUIRED_MESSAGE
import com.cybercat.ebooksender.data.manga.MangaAuthenticationExpiredException
import com.cybercat.ebooksender.data.manga.MangaBrowserSessionRefreshRequiredException
import com.cybercat.ebooksender.localization.AppStrings

internal object MangaErrorMessageMapper {
    fun isAuthenticationExpired(error: Throwable): Boolean =
        error is MangaAuthenticationExpiredException ||
            error.message == MANGA_AUTHENTICATION_EXPIRED_MESSAGE

    fun isAuthenticationExpired(message: String): Boolean =
        message == MANGA_AUTHENTICATION_EXPIRED_MESSAGE

    fun errorMessage(error: Throwable, fallback: String, strings: AppStrings): String = when {
        isAuthenticationExpired(error) -> strings.mangaErrorLoginExpired

        error is MangaBrowserSessionRefreshRequiredException ->
            strings.get("manga_error_browser_session_required")

        else -> error.message ?: fallback
    }

    fun errorMessage(message: String, strings: AppStrings): String = message
        .replace(
            MANGA_NETWORK_UNAVAILABLE_MESSAGE,
            strings.get("manga_error_network_unavailable")
        )
        .replace(MANGA_AUTHENTICATION_EXPIRED_MESSAGE, strings.mangaErrorLoginExpired)
        .replace(
            MANGA_BROWSER_SESSION_REFRESH_REQUIRED_MESSAGE,
            strings.get("manga_error_browser_session_required")
        )

    fun formatFailures(failedMessages: List<String>, strings: AppStrings): String? {
        if (failedMessages.isEmpty()) return null
        val visible = failedMessages.take(VISIBLE_FAILURE_LIMIT).joinToString("\n") { message ->
            errorMessage(message, strings)
        }
        val hiddenCount = failedMessages.size - VISIBLE_FAILURE_LIMIT
        return if (hiddenCount > 0) {
            strings.get("manga_error_failures_summary", visible, hiddenCount)
        } else {
            visible
        }
    }

    private const val MANGA_NETWORK_UNAVAILABLE_MESSAGE = "MANGA_NETWORK_UNAVAILABLE"
    private const val VISIBLE_FAILURE_LIMIT = 3
}

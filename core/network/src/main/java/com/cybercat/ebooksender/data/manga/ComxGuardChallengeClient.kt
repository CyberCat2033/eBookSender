package com.cybercat.ebooksender.data.manga

import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComxGuardChallengeClient @Inject constructor(
    private val connectionFactory: ComxHttpConnectionFactory,
    private val sessionManager: ComxMangaSessionManager
) {
    fun solveGuardChallenge(html: String, url: String): Boolean {
        val token = GUARD_TOKEN_REGEX.find(html)?.groupValues?.getOrNull(1)
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
            "cdpf" to ""
        ).toFormEncodedUtf8Body()

        val connection = connectionFactory.openConnection(
            url = target,
            accept = "*/*",
            referer = url,
            connectTimeout = GUARD_CONNECT_TIMEOUT_MILLIS,
            readTimeout = GUARD_READ_TIMEOUT_MILLIS
        ).apply {
            requestMethod = "POST"
            doOutput = true
            instanceFollowRedirects = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Origin", ComxMangaAdapter.HomeUrl.trimEnd('/'))
        }

        return try {
            connection.outputStream.use { output ->
                output.write(body)
            }
            val code = connection.responseCode
            sessionManager.captureCookies(connection, target)
            val response = connection.readTextBody()
            code in 200..299 && response.contains("OK", ignoreCase = true)
        } catch (_: IOException) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        private const val GUARD_CONNECT_TIMEOUT_MILLIS = 15_000
        private const val GUARD_READ_TIMEOUT_MILLIS = 30_000
        private val GUARD_TOKEN_REGEX = Regex("""token:\s*["']([^"']+)["']""")
    }
}

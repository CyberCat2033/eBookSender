package com.cybercat.ebooksender.domain

import android.net.Uri
import com.cybercat.ebooksender.model.RemoteDevice
import com.cybercat.ebooksender.model.normalizeFtpRootPath

object FtpUrlParser {
    fun parse(input: String): Result<RemoteDevice> = runCatching {
        val raw = input.trim()
        require(raw.isNotBlank()) { "FTP URL is empty" }

        val normalized = if (raw.startsWith("ftp://", ignoreCase = true)) {
            raw
        } else {
            normalizeBareFtpAddress(raw)
        }
        val uri = Uri.parse(normalized)
        require(uri.scheme.equals("ftp", ignoreCase = true)) { "Only ftp:// links are supported" }

        val host = requireNotNull(uri.host) { "FTP host is missing" }
        val port = if (uri.port > 0) uri.port else 2121
        val username = normalizeUsername(uri.userInfo)
        val rootPath = normalizeFtpRootPath(uri.path.orEmpty())

        RemoteDevice(
            host = host,
            port = port,
            username = username,
            rootPath = rootPath
        )
    }

    private fun normalizeBareFtpAddress(raw: String): String {
        val authority = raw.substringBefore('/')
        val path = raw.substringAfter('/', missingDelimiterValue = "")
        val hasUserInfo = authority.contains('@')
        val authorityWithUser = if (hasUserInfo) authority else "anonymous@$authority"
        val hostAndPort = authorityWithUser.substringAfterLast('@')
        val authorityWithPort = if (hostAndPort.contains(':')) {
            authorityWithUser
        } else {
            "$authorityWithUser:2121"
        }
        val normalizedPath = path.takeIf { it.isNotBlank() }?.let { "/$it" } ?: "/"
        return "ftp://$authorityWithPort$normalizedPath"
    }

    private fun normalizeUsername(userInfo: String?): String {
        val username = userInfo?.substringBefore(':')?.trim().orEmpty()
        return if (username.isBlank() || username.equals("<user>", ignoreCase = true)) {
            "anonymous"
        } else {
            username
        }
    }
}

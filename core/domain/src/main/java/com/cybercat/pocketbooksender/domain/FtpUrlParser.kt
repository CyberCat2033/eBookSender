package com.cybercat.pocketbooksender.domain

import android.net.Uri
import com.cybercat.pocketbooksender.model.RemoteDevice
import com.cybercat.pocketbooksender.model.normalizeFtpRootPath

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
        val username = uri.userInfo?.substringBefore(':')?.ifBlank { "anonymous" } ?: "anonymous"
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
}

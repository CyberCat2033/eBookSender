package com.cybercat.pocketbooksender.domain

import android.net.Uri
import com.cybercat.pocketbooksender.model.PocketBookDevice

object FtpUrlParser {
    fun parse(input: String): Result<PocketBookDevice> = runCatching {
        val raw = input.trim()
        require(raw.isNotBlank()) { "FTP URL is empty" }

        val normalized = if (raw.startsWith("ftp://")) {
            raw
        } else if (raw.substringAfterLast('@').contains(':')) {
            "ftp://anonymous@$raw/"
        } else {
            "ftp://anonymous@$raw:2121/"
        }
        val uri = Uri.parse(normalized)
        require(uri.scheme == "ftp") { "Only ftp:// links are supported" }

        val host = requireNotNull(uri.host) { "FTP host is missing" }
        val port = if (uri.port > 0) uri.port else 2121
        val username = uri.userInfo?.substringBefore(':')?.ifBlank { "anonymous" } ?: "anonymous"

        PocketBookDevice(
            host = host,
            port = port,
            username = username,
        )
    }
}

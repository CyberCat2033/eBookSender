package com.cybercat.pocketbooksender.data.opds

import java.net.URI

object OpdsUrlResolver {
    fun resolveUrl(baseUrl: String, href: String): String = runCatching {
        URI(baseUrl).resolve(href).toString()
    }.getOrElse {
        href
    }
}

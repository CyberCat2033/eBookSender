package com.cybercat.ebooksender.util

import java.net.URI
import java.net.URL

object UrlHostMatcher {
    fun normalizedHost(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return null

        return parseHost(trimmed)
            ?.trim()
            ?.trimEnd('.')
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
    }

    fun hostsMatch(firstUrl: String, secondUrl: String): Boolean {
        val firstHost = normalizedHost(firstUrl) ?: return false
        return firstHost == normalizedHost(secondUrl)
    }

    fun displayHostWithoutWww(url: String): String? = normalizedHost(url)?.removePrefix("www.")

    fun hostContainsAny(url: String, fragments: Collection<String>): Boolean {
        val host = normalizedHost(url) ?: return false
        return fragments.any { fragment -> fragment.lowercase() in host }
    }

    private fun parseHost(url: String): String? = runCatching { URI(url).host }.getOrNull()
        ?: runCatching { URL(url).host }.getOrNull()
}

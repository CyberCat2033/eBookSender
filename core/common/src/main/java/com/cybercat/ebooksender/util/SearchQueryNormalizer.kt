package com.cybercat.ebooksender.util

object SearchQueryNormalizer {
    private val unsupportedSearchChars = Regex("[^\\p{L}\\p{N}\\s-]+")
    private val whitespace = Regex("\\s+")

    fun normalize(query: String): String = query.trim()
        .replace(unsupportedSearchChars, " ")
        .replace(whitespace, " ")
        .trim()
        .lowercase()

    fun tokens(query: String): List<String> = normalize(query)
        .split(' ')
        .map { token -> token.trim('-', ' ') }
        .filter { token -> token.length >= 2 }

    fun comparableText(query: String): String = tokens(query).joinToString(" ")
}

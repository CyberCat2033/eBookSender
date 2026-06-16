package com.cybercat.pocketbooksender.domain

import javax.inject.Inject
import javax.inject.Singleton

data class MangaTitleParts(
    val series: String?,
    val volume: String?,
)

@Singleton
class MangaTitleParser @Inject constructor() {
    fun parse(displayName: String): MangaTitleParts {
        val title = displayName.bookTitleWithoutExtension().trim()
        for (pattern in MangaTitlePatterns) {
            val match = pattern.matchEntire(title) ?: continue
            val series = match.groupValues[1].trim().replace('_', ' ')
            val volume = match.groupValues[2].trim()
            if (series.isNotBlank() && volume.isNotBlank()) {
                return MangaTitleParts(series = series, volume = volume)
            }
        }
        return MangaTitleParts(series = null, volume = null)
    }
}

private val MangaTitlePatterns = listOf(
    Regex("""^(.*?)\s+-\s+(.+)$"""), // "Naruto - 01", "Naruto - Chapter 1"
    Regex("""^(.*?)\s*_\s*(\d+.*)$"""), // "Naruto_01", "Naruto_ch1"
    Regex("""^(.*?)\s+([vV]?\d+.*)$"""), // "Naruto 01", "Naruto v01", "Naruto ch01"
)

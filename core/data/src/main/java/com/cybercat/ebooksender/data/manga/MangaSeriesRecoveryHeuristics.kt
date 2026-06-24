package com.cybercat.ebooksender.data.manga

internal fun rankRecoveredSeriesCandidates(
    savedTitle: String,
    originalSeriesId: String,
    results: List<MangaSeriesSearchResult>,
    maxCandidates: Int
): List<MangaSeriesSearchResult> {
    val savedKey = savedTitle.mangaTitleMatchKey()
    if (savedKey.isBlank()) return emptyList()

    return results
        .asSequence()
        .map { result -> result to result.title.mangaTitleMatchScore(savedKey) }
        .filter { (_, score) -> score > 0 }
        .sortedWith(
            compareByDescending<Pair<MangaSeriesSearchResult, Int>> { (_, score) -> score }
                .thenBy { (result, _) -> result.title.length }
        )
        .map { (result, _) -> result }
        .filterNot { result ->
            result.seriesId.mangaSeriesCacheKey() ==
                originalSeriesId.mangaSeriesCacheKey()
        }
        .distinctBy { result -> result.seriesId.mangaSeriesCacheKey() }
        .take(maxCandidates)
        .toList()
}

internal fun String.mangaSeriesCacheKey(): String = trim().trimEnd('/').lowercase()

internal fun Throwable.isMangaSeriesNotFound(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is MangaNotFoundException) return true
        current = current.cause
    }
    return false
}

internal fun Throwable.mangaSessionStateFailureOrNull(): Throwable? {
    var current: Throwable? = this
    while (current != null) {
        if (
            current is MangaAuthenticationExpiredException ||
            current is MangaBrowserSessionRefreshRequiredException
        ) {
            return current
        }
        current = current.cause
    }
    return null
}

private fun String.mangaTitleMatchScore(savedKey: String): Int {
    val resultKey = mangaTitleMatchKey()
    if (resultKey.isBlank()) return 0
    return when {
        resultKey == savedKey -> 4
        resultKey.contains(savedKey) -> 3
        savedKey.contains(resultKey) -> 2
        else -> 0
    }
}

private fun String.mangaTitleMatchKey(): String = lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")

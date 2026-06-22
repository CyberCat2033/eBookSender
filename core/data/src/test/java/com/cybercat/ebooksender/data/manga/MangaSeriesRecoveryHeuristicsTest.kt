package com.cybercat.ebooksender.data.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaSeriesRecoveryHeuristicsTest {

    @Test
    fun mangaSeriesCacheKeyNormalizesString() {
        assertEquals("test-series", "Test-Series/".mangaSeriesCacheKey())
        assertEquals("test series", "  test series  ".mangaSeriesCacheKey())
    }

    @Test
    fun isMangaSeriesNotFoundTraversesCauses() {
        val rootCause = MangaNotFoundException(404, "not found")
        val wrapper1 = RuntimeException("error", rootCause)
        val wrapper2 = IllegalStateException("boom", wrapper1)

        assertTrue(rootCause.isMangaSeriesNotFound())
        assertTrue(wrapper1.isMangaSeriesNotFound())
        assertTrue(wrapper2.isMangaSeriesNotFound())
        assertFalse(IllegalArgumentException("other").isMangaSeriesNotFound())
    }

    @Test
    fun rankRecoveredSeriesCandidatesRanksCorrectly() {
        val results = listOf(
            MangaSeriesSearchResult(
                sourceId = "src",
                seriesId = "id-longer-match",
                title = "Naruto: Shippuden",
                coverUrl = null
            ),
            MangaSeriesSearchResult(
                sourceId = "src",
                seriesId = "id-exact-match",
                title = "Naruto",
                coverUrl = null
            ),
            MangaSeriesSearchResult(
                sourceId = "src",
                seriesId = "id-partial-match",
                title = "Naruto Story",
                coverUrl = null
            ),
            MangaSeriesSearchResult(
                sourceId = "src",
                seriesId = "id-no-match",
                title = "Bleach",
                coverUrl = null
            ),
            MangaSeriesSearchResult(
                sourceId = "src",
                seriesId = "original-id",
                title = "Naruto",
                coverUrl = null
            )
        )

        val ranked = rankRecoveredSeriesCandidates(
            savedTitle = "Naruto",
            originalSeriesId = "original-id",
            results = results,
            maxCandidates = 3
        )

        assertEquals(3, ranked.size)
        assertEquals("id-exact-match", ranked[0].seriesId)
        assertEquals("id-partial-match", ranked[1].seriesId)
        assertEquals("id-longer-match", ranked[2].seriesId)
    }

    @Test
    fun rankRecoveredSeriesCandidatesReturnsEmptyOnBlankTitle() {
        val results = listOf(
            MangaSeriesSearchResult(
                sourceId = "src",
                seriesId = "some-id",
                title = "Naruto",
                coverUrl = null
            )
        )
        val ranked = rankRecoveredSeriesCandidates(
            savedTitle = "   ",
            originalSeriesId = "original-id",
            results = results,
            maxCandidates = 5
        )
        assertTrue(ranked.isEmpty())
    }
}

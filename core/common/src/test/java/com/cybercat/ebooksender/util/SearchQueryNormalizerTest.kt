package com.cybercat.ebooksender.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchQueryNormalizerTest {

    @Test
    fun testNormalize() {
        assertEquals("war and peace", SearchQueryNormalizer.normalize("  War and Peace!  "))
        assertEquals("naruto 10", SearchQueryNormalizer.normalize("Naruto #10"))
        // Keep letters, digits, spaces, and hyphens
        assertEquals("one-piece", SearchQueryNormalizer.normalize("One-Piece"))
        assertEquals("a b c", SearchQueryNormalizer.normalize("a @#$ b %^&* c"))
    }

    @Test
    fun testTokens() {
        // Words less than 2 characters are filtered out
        val tokens = SearchQueryNormalizer.tokens("a war and peace-story")
        assertEquals(listOf("war", "and", "peace-story"), tokens)

        // Trims hyphens and spaces from tokens
        val tokensWithHyphens = SearchQueryNormalizer.tokens(" -naruto- ")
        assertEquals(listOf("naruto"), tokensWithHyphens)
    }

    @Test
    fun testComparableText() {
        assertEquals(
            "war and peace-story",
            SearchQueryNormalizer.comparableText("a war and peace-story")
        )
        assertEquals("naruto", SearchQueryNormalizer.comparableText(" -naruto- "))
    }
}

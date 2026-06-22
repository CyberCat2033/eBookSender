package com.cybercat.ebooksender.data.opds

import org.junit.Assert.assertEquals
import org.junit.Test

class OpdsUrlResolverTest {

    @Test
    fun resolvesAbsoluteUrl() {
        val baseUrl = "https://example.com/catalog/opds"
        val href = "https://another.com/book"
        val resolved = OpdsUrlResolver.resolveUrl(baseUrl, href)
        assertEquals("https://another.com/book", resolved)
    }

    @Test
    fun resolvesRelativePath() {
        val baseUrl = "https://example.com/catalog/opds"
        val href = "books/1.epub"
        val resolved = OpdsUrlResolver.resolveUrl(baseUrl, href)
        assertEquals("https://example.com/catalog/books/1.epub", resolved)
    }

    @Test
    fun resolvesRelativePathWithLeadingSlash() {
        val baseUrl = "https://example.com/catalog/opds"
        val href = "/root-books/1.epub"
        val resolved = OpdsUrlResolver.resolveUrl(baseUrl, href)
        assertEquals("https://example.com/root-books/1.epub", resolved)
    }

    @Test
    fun resolvesRelativePathWithDotSegments() {
        val baseUrl = "https://example.com/catalog/opds/"
        val href = "../images/cover.jpg"
        val resolved = OpdsUrlResolver.resolveUrl(baseUrl, href)
        assertEquals("https://example.com/catalog/images/cover.jpg", resolved)
    }

    @Test
    fun resolvesUrlWithQueryParamsAndHash() {
        val baseUrl = "https://example.com/catalog/opds?lang=en"
        val href = "books/1.epub?user=123#page=2"
        val resolved = OpdsUrlResolver.resolveUrl(baseUrl, href)
        assertEquals("https://example.com/catalog/books/1.epub?user=123#page=2", resolved)
    }

    @Test
    fun fallsBackToOriginalHrefOnInvalidBaseUrl() {
        val baseUrl = "invalid-url"
        val href = "books/1.epub"
        val resolved = OpdsUrlResolver.resolveUrl(baseUrl, href)
        assertEquals("books/1.epub", resolved)
    }
}

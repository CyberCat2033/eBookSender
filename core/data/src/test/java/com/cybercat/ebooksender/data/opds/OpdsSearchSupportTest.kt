package com.cybercat.ebooksender.data.opds

import org.junit.Assert.assertEquals
import org.junit.Test

class OpdsSearchSupportTest {

    @Test
    fun normalizeOpdsSearchTemplateOrigin_rewritesFlibustaHttpTemplateToSourceHttpsMirror() {
        val normalized = normalizeOpdsSearchTemplateOrigin(
            sourceBaseUrl = "https://flub.flibusta.is/opds",
            templateUrl = "http://flibusta.is/opds/opensearch" +
                "?searchTerm={searchTerms}&searchType=books&pageNumber={startPage?}"
        )

        assertEquals(
            "https://flub.flibusta.is/opds/opensearch" +
                "?searchTerm={searchTerms}&searchType=books&pageNumber={startPage?}",
            normalized
        )
    }

    @Test
    fun normalizeOpdsSearchTemplateOrigin_keepsUnrelatedHttpTemplate() {
        val normalized = normalizeOpdsSearchTemplateOrigin(
            sourceBaseUrl = "https://catalog.example/opds",
            templateUrl = "http://other.example/opds/search?query={searchTerms}"
        )

        assertEquals("http://other.example/opds/search?query={searchTerms}", normalized)
    }

    @Test
    fun rankOpdsSearchLinks_prioritizesOpenSearchThenDirectTemplate() {
        val direct = OpdsLink(
            href = "/opds/search?searchTerm={searchTerms}",
            rel = "search",
            type = "application/atom+xml",
            title = null
        )
        val openSearch = OpdsLink(
            href = "/opds-opensearch.xml",
            rel = "search",
            type = "application/opensearchdescription+xml",
            title = null
        )
        val fallback = OpdsLink(
            href = "/opds/search",
            rel = "search",
            type = "application/xml",
            title = null
        )

        assertEquals(
            listOf(openSearch, direct, fallback),
            listOf(fallback, direct, openSearch).rankOpdsSearchLinks()
        )
    }

    @Test
    fun expandOpdsSearchTemplate_removesUnsupportedOpenSearchParameters() {
        val expanded = expandOpdsSearchTemplate(
            template = "https://flub.flibusta.is/opds/opensearch" +
                "?searchTerm={searchTerms}&searchType=books&pageNumber={startPage?}",
            query = "метро 2033"
        )

        assertEquals(
            "https://flub.flibusta.is/opds/opensearch?searchTerm=%D0%BC%D0%B5%D1%82%D1%80%D0%BE+2033&searchType=books",
            expanded
        )
    }
}

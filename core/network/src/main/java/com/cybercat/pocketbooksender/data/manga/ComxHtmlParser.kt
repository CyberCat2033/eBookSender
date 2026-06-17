package com.cybercat.pocketbooksender.data.manga

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComxHtmlParser @Inject constructor(
    private val searchParser: ComxSearchParser,
    private val seriesPageParser: ComxSeriesPageParser,
    private val readerPageParser: ComxReaderPageParser
) {
    fun ownsUrl(url: String): Boolean = ownsComxUrl(url)

    fun parseSearchResults(url: String, html: String): List<MangaSeriesSearchResult> {
        ensureReadableHtml(html)
        return searchParser.parseSearchResults(url, html)
    }

    fun parseSeriesPage(url: String, html: String): MangaSeriesPage? {
        ensureReadableHtml(html)
        return seriesPageParser.parseSeriesPage(url, html)
    }

    fun parseSeriesDetails(url: String, html: String): MangaSeriesDetails {
        ensureReadableHtml(html)
        return seriesPageParser.parseSeriesDetails(url, html)
    }

    fun parseChapterPages(url: String, html: String): List<MangaPage> {
        ensureReadableHtml(html)
        return readerPageParser.parseChapterPages(url, html)
    }

    fun imageExtensionFromUrl(url: String): String? = comxImageExtensionFromUrl(url)

    fun ensureReadableHtml(html: String) {
        if (isGuardChallenge(html)) {
            throw IOException(
                "Com-X session is not ready. Open Login, let the site load, then retry."
            )
        }
    }

    fun isGuardChallenge(html: String): Boolean {
        val javascriptGate = html.contains("/_v") &&
            html.contains("Для доступа к сайту необходимо включить JavaScript")
        val tokenGate = html.contains("targetUrl") &&
            html.contains("token:") &&
            html.contains("x.open(\"POST\", \"/_v\"")

        return javascriptGate || tokenGate
    }
}

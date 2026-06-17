package com.cybercat.pocketbooksender.data.manga

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Singleton
class ComxHtmlParser @Inject constructor(
    private val searchParser: ComxSearchParser,
    private val readerPageParser: ComxReaderPageParser
) {
    fun ownsUrl(url: String): Boolean = ownsComxUrl(url)

    fun parseSearchResults(url: String, html: String): List<MangaSeriesSearchResult> {
        ensureReadableHtml(html)
        return searchParser.parseSearchResults(url, html)
    }

    fun parseSeriesPage(url: String, html: String): MangaSeriesPage? {
        ensureReadableHtml(html)
        val document = Jsoup.parse(html, url)
        val data = extractComxWindowData(html)
        val details = parseSeriesDetails(url, document, data)
        val chapters = parseDataChapters(details.seriesId, details.title, data)
            .ifEmpty { parseReaderLinks(details.seriesId, details.title, document) }

        return if (chapters.isNotEmpty() || document.selectFirst("h1") != null || data != null) {
            MangaSeriesPage(details = details, chapters = chapters)
        } else {
            null
        }
    }

    fun parseSeriesDetails(url: String, html: String): MangaSeriesDetails {
        ensureReadableHtml(html)
        return parseSeriesDetails(url, Jsoup.parse(html, url), extractComxWindowData(html))
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

    private fun parseSeriesDetails(
        url: String,
        document: Document,
        data: JSONObject?
    ): MangaSeriesDetails {
        val title = firstNonBlank(
            data?.firstString("title", "name", "original_title"),
            document.selectFirst("h1")?.text(),
            document.selectFirst("meta[property=og:title]")?.attr("content"),
            document.title(),
            titleFromUrl(url)
        ).cleanTitle()

        val cover = firstNonBlank(
            data?.firstString("poster", "cover", "image", "img", "cover_url")?.resolveAgainst(url),
            document.selectFirst("meta[property=og:image]")?.attr("content")?.resolveAgainst(url),
            document.select("img[alt]").firstOrNull { image ->
                image.attr("alt").contains(title.take(12), ignoreCase = true)
            }?.absImageUrl(),
            document.selectFirst(
                ".story img, .fullstory img, article img, .poster img"
            )?.absImageUrl()
        )

        val description = firstNonBlank(
            data?.firstString("description", "descr", "annotation"),
            document.selectFirst("[itemprop=description]")?.text(),
            document.selectFirst(".description, .desc, .story, .fullstory, .full-text")?.text(),
            document.selectFirst("meta[name=description]")?.attr("content")
        ).ifBlank { null }

        return MangaSeriesDetails(
            sourceId = ComxMangaAdapter.SourceId,
            seriesId = url,
            title = title,
            coverUrl = cover.ifBlank { null },
            description = description
        )
    }

    private fun parseDataChapters(
        seriesId: String,
        seriesTitle: String,
        data: JSONObject?
    ): List<MangaChapter> {
        if (data == null) return emptyList()

        val newsId = data.optLong("news_id", -1L).takeIf { it > 0L }
            ?: seriesId.extractNewsId()
            ?: return emptyList()
        val chapters = data.optJSONArray("chapters") ?: return emptyList()

        return (0 until chapters.length())
            .asSequence()
            .mapNotNull { index ->
                val item = chapters.optJSONObject(index) ?: return@mapNotNull null
                val chapterId = item.optLong("id", -1L).takeIf { it > 0L } ?: return@mapNotNull null
                val chapterUrl = "${ComxMangaAdapter.HomeUrl}reader/$newsId/$chapterId"
                val title = firstNonBlank(
                    item.firstString("title", "name"),
                    "Chapter ${index + 1}"
                ).cleanTitle().stripSeriesPrefix(seriesTitle)
                val sortNumber = item.optFiniteDouble("posi")
                    ?: item.optFiniteDouble("number")
                    ?: title.extractChapterNumber()

                MangaChapter(
                    sourceId = ComxMangaAdapter.SourceId,
                    seriesId = seriesId,
                    chapterId = chapterUrl,
                    stableKey = chapterUrl.normalizeUrlKey(),
                    title = title,
                    numberForSort = sortNumber,
                    publishedAtMillis = null,
                    downloadUrl = item.firstString("download_link")
                        .resolveAgainst(seriesId)
                        .takeIf { it.isNotBlank() }
                )
            }
            .distinctBy { chapter -> chapter.stableKey }
            .sortedWith(
                compareBy<MangaChapter> { it.numberForSort ?: Double.MAX_VALUE }
                    .thenBy { it.title }
            )
            .toList()
    }

    private fun parseReaderLinks(
        seriesId: String,
        seriesTitle: String,
        document: Document
    ): List<MangaChapter> {
        return document.select("a[href*=/reader/]")
            .asSequence()
            .mapNotNull { anchor ->
                val href = anchor.absUrl("href").ifBlank { return@mapNotNull null }
                if (!ownsUrl(href)) return@mapNotNull null
                val text = anchor.text().cleanWhitespace()
                val number = text.extractChapterNumber() ?: href.extractChapterNumber()
                val title = text.ifBlank {
                    "Chapter ${number ?: ""}".trim()
                }.stripSeriesPrefix(seriesTitle)

                MangaChapter(
                    sourceId = ComxMangaAdapter.SourceId,
                    seriesId = seriesId,
                    chapterId = href,
                    stableKey = href.normalizeUrlKey(),
                    title = title,
                    numberForSort = number,
                    publishedAtMillis = null
                )
            }
            .distinctBy { chapter -> chapter.stableKey }
            .sortedWith(
                compareBy<MangaChapter> { it.numberForSort ?: Double.MAX_VALUE }
                    .thenBy { it.title }
            )
            .toList()
    }
}

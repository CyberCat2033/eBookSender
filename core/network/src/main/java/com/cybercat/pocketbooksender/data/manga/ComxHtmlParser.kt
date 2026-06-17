package com.cybercat.pocketbooksender.data.manga

import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Singleton
class ComxHtmlParser @Inject constructor(private val searchParser: ComxSearchParser) {
    fun ownsUrl(url: String): Boolean = ownsComxUrl(url)

    fun parseSearchResults(url: String, html: String): List<MangaSeriesSearchResult> {
        ensureReadableHtml(html)
        return searchParser.parseSearchResults(url, html)
    }

    fun parseSeriesPage(url: String, html: String): MangaSeriesPage? {
        ensureReadableHtml(html)
        val document = Jsoup.parse(html, url)
        val data = extractWindowData(html)
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
        return parseSeriesDetails(url, Jsoup.parse(html, url), extractWindowData(html))
    }

    fun parseChapterPages(url: String, html: String): List<MangaPage> {
        ensureReadableHtml(html)

        val dataPages = parseReaderDataPages(url, extractWindowData(html))
        if (dataPages.isNotEmpty()) {
            return dataPages
        }

        val document = Jsoup.parse(html, url)
        val urls = linkedImageUrls(document)
            .distinctBy { it.normalizeUrlKey() }
            .filter(::isReaderImageUrl)

        return urls.mapIndexed { index, imageUrl ->
            MangaPage(
                index = index,
                imageUrl = imageUrl,
                refererUrl = url,
                fileExtension = imageExtensionFromUrl(imageUrl)
            )
        }
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

    private fun parseReaderDataPages(url: String, data: JSONObject?): List<MangaPage> {
        if (data == null) return emptyList()
        val images = data.optJSONArray("images") ?: return emptyList()
        val host = data.optString("host", "img.com-x.life")
            .removePrefix("https://")
            .removePrefix("http://")
            .trim('/')
            .ifBlank { "img.com-x.life" }

        return (0 until images.length())
            .asSequence()
            .mapNotNull { index ->
                val raw = images.optString(index)
                    .replace("\\/", "/")
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                val imageUrl = when {
                    raw.startsWith("http://") || raw.startsWith("https://") -> raw
                    raw.trimStart('/').startsWith("comix/") -> "https://$host/${raw.trimStart('/')}"
                    else -> "https://$host/comix/${raw.trimStart('/')}"
                }

                MangaPage(
                    index = index,
                    imageUrl = imageUrl,
                    refererUrl = url,
                    fileExtension = imageExtensionFromUrl(imageUrl)
                )
            }
            .toList()
    }

    private fun linkedImageUrls(document: Document): List<String> {
        val urls = mutableListOf<String>()
        document.select("img, source").forEach { element ->
            listOf("src", "data-src", "data-original", "data-lazy-src", "data-full")
                .mapNotNullTo(urls) { attr ->
                    element.absUrl(attr).takeIf { it.isNotBlank() }
                }

            element.attr("srcset")
                .takeIf { it.isNotBlank() }
                ?.split(',')
                ?.mapNotNullTo(urls) { candidate ->
                    candidate.trim().substringBefore(' ').takeIf { it.isNotBlank() }?.let { raw ->
                        runCatching { URI(document.baseUri()).resolve(raw).toString() }.getOrNull()
                    }
                }
        }

        document.select("script").forEach { script ->
            ComxImageUrlRegex.findAll(script.data()).forEach { match ->
                urls += match.value
                    .replace("\\/", "/")
                    .replace("\\u0026", "&")
                    .replace("&amp;", "&")
            }
        }

        return urls
    }

    private fun extractWindowData(html: String): JSONObject? {
        val markerIndex = html.indexOf("window.__DATA__")
        if (markerIndex < 0) return null

        val start = html.indexOf('{', markerIndex)
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escape = false

        for (index in start until html.length) {
            val char = html[index]
            if (inString) {
                if (escape) {
                    escape = false
                } else if (char == '\\') {
                    escape = true
                } else if (char == '"') {
                    inString = false
                }
                continue
            }

            when (char) {
                '"' -> inString = true

                '{' -> depth += 1

                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return runCatching {
                            JSONObject(html.substring(start, index + 1))
                        }.getOrNull()
                    }
                }
            }
        }

        return null
    }

    private fun isReaderImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (imageExtensionFromUrl(lower) == null) return false
        return ComxIgnoredImageMarkers.none { marker -> lower.contains(marker) }
    }
}

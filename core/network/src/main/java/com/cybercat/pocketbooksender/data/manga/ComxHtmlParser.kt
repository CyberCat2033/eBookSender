package com.cybercat.pocketbooksender.data.manga

import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Singleton
class ComxHtmlParser @Inject constructor() {
    fun ownsUrl(url: String): Boolean =
        runCatching {
            URI(url).host.orEmpty().removePrefix("www.") == "com-x.life"
        }.getOrDefault(false)

    fun parseSearchResults(url: String, html: String): List<MangaSeriesSearchResult> {
        ensureReadableHtml(html)
        val document = Jsoup.parse(html, url)

        val searchPageResults = document
            .select("#dle-content .readed")
            .asSequence()
            .mapNotNull { block -> block.toReadedSearchResult() }
            .distinctBy { result -> result.seriesId.normalizeUrlKey() }
            .take(MaxSearchResults)
            .toList()

        if (searchPageResults.isNotEmpty()) {
            return searchPageResults
        }

        val posterResults = document
            .select("main a.poster[href], #dle-content a.poster[href], .sect__content a.poster[href], a.poster[href]")
            .asSequence()
            .mapNotNull { anchor -> anchor.toSeriesSearchResult() }
            .distinctBy { result -> result.seriesId.normalizeUrlKey() }
            .take(MaxSearchResults)
            .toList()

        if (posterResults.isNotEmpty()) {
            return posterResults
        }

        return parseJsonLdSearchResults(document)
            .distinctBy { result -> result.seriesId.normalizeUrlKey() }
            .take(MaxSearchResults)
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
                fileExtension = imageExtensionFromUrl(imageUrl),
            )
        }
    }

    fun imageExtensionFromUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        return path.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it in ImageExtensions }
    }

    fun ensureReadableHtml(html: String) {
        if (isGuardChallenge(html)) {
            throw IOException("Com-X session is not ready. Open Login, let the site load, then retry.")
        }
    }

    fun isGuardChallenge(html: String): Boolean =
        (html.contains("/_v") && html.contains("Для доступа к сайту необходимо включить JavaScript")) ||
            (html.contains("targetUrl") && html.contains("token:") && html.contains("x.open(\"POST\", \"/_v\""))

    private fun parseSeriesDetails(
        url: String,
        document: Document,
        data: JSONObject?,
    ): MangaSeriesDetails {
        val title = firstNonBlank(
            data?.firstString("title", "name", "original_title"),
            document.selectFirst("h1")?.text(),
            document.selectFirst("meta[property=og:title]")?.attr("content"),
            document.title(),
            titleFromUrl(url),
        ).cleanTitle()

        val cover = firstNonBlank(
            data?.firstString("poster", "cover", "image", "img", "cover_url")?.resolveAgainst(url),
            document.selectFirst("meta[property=og:image]")?.attr("content")?.resolveAgainst(url),
            document.select("img[alt]").firstOrNull { image ->
                image.attr("alt").contains(title.take(12), ignoreCase = true)
            }?.absImageUrl(),
            document.selectFirst(".story img, .fullstory img, article img, .poster img")?.absImageUrl(),
        )

        val description = firstNonBlank(
            data?.firstString("description", "descr", "annotation"),
            document.selectFirst("[itemprop=description]")?.text(),
            document.selectFirst(".description, .desc, .story, .fullstory, .full-text")?.text(),
            document.selectFirst("meta[name=description]")?.attr("content"),
        ).ifBlank { null }

        return MangaSeriesDetails(
            sourceId = ComxMangaAdapter.SourceId,
            seriesId = url,
            title = title,
            coverUrl = cover.ifBlank { null },
            description = description,
        )
    }

    private fun parseDataChapters(seriesId: String, seriesTitle: String, data: JSONObject?): List<MangaChapter> {
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
                    "Chapter ${index + 1}",
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
                        .takeIf { it.isNotBlank() },
                )
            }
            .distinctBy { chapter -> chapter.stableKey }
            .sortedWith(
                compareBy<MangaChapter> { it.numberForSort ?: Double.MAX_VALUE }
                    .thenBy { it.title },
            )
            .toList()
    }

    private fun parseReaderLinks(seriesId: String, seriesTitle: String, document: Document): List<MangaChapter> {
        return document.select("a[href*=/reader/]")
            .asSequence()
            .mapNotNull { anchor ->
                val href = anchor.absUrl("href").ifBlank { return@mapNotNull null }
                if (!ownsUrl(href)) return@mapNotNull null
                val text = anchor.text().cleanWhitespace()
                val number = text.extractChapterNumber() ?: href.extractChapterNumber()
                val title = text.ifBlank { "Chapter ${number ?: ""}".trim() }.stripSeriesPrefix(seriesTitle)

                MangaChapter(
                    sourceId = ComxMangaAdapter.SourceId,
                    seriesId = seriesId,
                    chapterId = href,
                    stableKey = href.normalizeUrlKey(),
                    title = title,
                    numberForSort = number,
                    publishedAtMillis = null,
                )
            }
            .distinctBy { chapter -> chapter.stableKey }
            .sortedWith(
                compareBy<MangaChapter> { it.numberForSort ?: Double.MAX_VALUE }
                    .thenBy { it.title },
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
                    fileExtension = imageExtensionFromUrl(imageUrl),
                )
            }
            .toList()
    }

    private fun Element.toReadedSearchResult(): MangaSeriesSearchResult? {
        val titleLink = selectFirst(".readed__title a[href]")
        val imageLink = selectFirst(".readed__img[href]")
        val href = firstNonBlank(
            titleLink?.absUrl("href"),
            imageLink?.absUrl("href"),
        ).ifBlank { return null }

        if (!ownsUrl(href) || !href.isLikelySeriesUrl()) {
            return null
        }

        val title = firstNonBlank(
            titleLink?.text(),
            selectFirst("img[alt]")?.attr("alt"),
            titleFromUrl(href),
        ).cleanPosterTitle()

        if (!title.isLikelySeriesTitle()) {
            return null
        }

        val meta = select(".readed__meta-item")
            .map { item -> item.text().cleanWhitespace() }
            .filter { item -> item.isNotBlank() }
            .take(2)
            .joinToString(" · ")
            .ifBlank { null }

        return MangaSeriesSearchResult(
            sourceId = ComxMangaAdapter.SourceId,
            seriesId = href,
            title = title,
            coverUrl = selectFirst(".readed__img img, img")?.absImageUrl(),
            subtitle = meta,
        )
    }

    private fun Element.toSeriesSearchResult(): MangaSeriesSearchResult? {
        val href = absUrl("href").ifBlank { return null }
        if (!ownsUrl(href) || !href.isLikelySeriesUrl()) {
            return null
        }

        val title = firstNonBlank(
            selectFirst(".poster__title, .poster__name, .title")?.text(),
            attr("title"),
            selectFirst("img[alt]")?.attr("alt"),
            text(),
            titleFromUrl(href),
        ).cleanPosterTitle()

        if (!title.isLikelySeriesTitle()) {
            return null
        }

        val subtitle = firstNonBlank(
            selectFirst(".poster__subtitle, .poster__desc, .poster__meta")?.text(),
            parent()?.ownText(),
        ).cleanWhitespace().takeIf { it.length in 4..120 }

        return MangaSeriesSearchResult(
            sourceId = ComxMangaAdapter.SourceId,
            seriesId = href,
            title = title,
            coverUrl = selectFirst("img")?.absImageUrl(),
            subtitle = subtitle,
        )
    }

    private fun parseJsonLdSearchResults(document: Document): List<MangaSeriesSearchResult> {
        return document.select("script[type=application/ld+json]")
            .flatMap { script ->
                parseJsonLdNode(script.data().ifBlank { script.html() })
            }
            .filter { result -> result.title.isLikelySeriesTitle() }
    }

    private fun parseJsonLdNode(rawJson: String): List<MangaSeriesSearchResult> {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank()) return emptyList()

        val node = runCatching {
            if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
        }.getOrNull() ?: return emptyList()

        return when (node) {
            is JSONArray -> (0 until node.length()).flatMap { index ->
                val item = node.optJSONObject(index) ?: return@flatMap emptyList()
                parseJsonLdObject(item)
            }
            is JSONObject -> parseJsonLdObject(node)
            else -> emptyList()
        }
    }

    private fun parseJsonLdObject(json: JSONObject): List<MangaSeriesSearchResult> {
        json.optJSONArray("@graph")?.let { graph ->
            return (0 until graph.length()).flatMap { index ->
                val item = graph.optJSONObject(index) ?: return@flatMap emptyList()
                parseJsonLdObject(item)
            }
        }

        if (json.optString("@type").equals("ItemList", ignoreCase = true)) {
            val items = json.optJSONArray("itemListElement") ?: return emptyList()
            return (0 until items.length()).mapNotNull { index ->
                val listItem = items.optJSONObject(index) ?: return@mapNotNull null
                val item = listItem.optJSONObject("item") ?: listItem
                jsonObjectToSearchResult(item)
            }
        }

        return listOfNotNull(jsonObjectToSearchResult(json))
    }

    private fun jsonObjectToSearchResult(json: JSONObject): MangaSeriesSearchResult? {
        val url = json.firstString("url", "@id").resolveAgainst(ComxMangaAdapter.HomeUrl)
        if (!ownsUrl(url) || !url.isLikelySeriesUrl()) return null

        val title = firstNonBlank(
            json.firstString("name", "title"),
            titleFromUrl(url),
        ).cleanPosterTitle()

        return MangaSeriesSearchResult(
            sourceId = ComxMangaAdapter.SourceId,
            seriesId = url,
            title = title,
            coverUrl = json.firstString("image", "thumbnailUrl").resolveAgainst(ComxMangaAdapter.HomeUrl)
                .takeIf { it.isNotBlank() },
        )
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
            ImageUrlRegex.findAll(script.data()).forEach { match ->
                urls += match.value
                    .replace("\\/", "/")
                    .replace("\\u0026", "&")
                    .replace("&amp;", "&")
            }
        }

        return urls
    }

    private fun Element.absImageUrl(): String =
        firstNonBlank(
            absUrl("data-src"),
            absUrl("data-original"),
            absUrl("data-lazy-src"),
            absUrl("src"),
        )

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
                        return runCatching { JSONObject(html.substring(start, index + 1)) }.getOrNull()
                    }
                }
            }
        }

        return null
    }

    private fun String.isLikelySeriesUrl(): Boolean {
        val lower = lowercase()
        return lower.startsWith(ComxMangaAdapter.HomeUrl) &&
            lower.substringBefore('?').endsWith(".html") &&
            !lower.contains("/user/") &&
            !lower.contains("/index.php") &&
            !lower.contains("/reader/")
    }

    private fun String.isLikelySeriesTitle(): Boolean {
        val value = cleanWhitespace()
        if (value.length !in 2..160) return false
        if (value in IgnoredTitles) return false
        if (value.matches(Regex("""^\d+([.,]\d+)?$"""))) return false
        return value.any { it.isLetter() }
    }

    private fun String.extractChapterNumber(): Double? =
        NumberRegex.findAll(this)
            .lastOrNull()
            ?.value
            ?.replace(',', '.')
            ?.toDoubleOrNull()

    private fun String.extractNewsId(): Long? =
        Regex("""/(\d+)-""").find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

    private fun String.normalizeUrlKey(): String =
        runCatching {
            val uri = URI(this)
            URI(
                uri.scheme?.lowercase(),
                uri.authority?.lowercase(),
                uri.path?.trimEnd('/'),
                uri.query,
                null,
            ).toString()
        }.getOrDefault(this.trim().trimEnd('/'))

    private fun String.cleanPosterTitle(): String =
        cleanTitle()
            .replace(Regex("""^(Картинка|Image)\s+""", RegexOption.IGNORE_CASE), "")
            .cleanTitle()

    private fun String.cleanTitle(): String =
        cleanWhitespace()
            .replace(Regex("""^\d+([.,]\d+)?\s+"""), "")
            .trim(' ', '-', '—', '|')

    private fun String.cleanWhitespace(): String =
        replace('\u00A0', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun String.stripSeriesPrefix(seriesTitle: String): String {
        val trimmed = this.trim()
        val cleanSeries = seriesTitle.replace(Regex("""[«»"'"“”]"""), "").cleanWhitespace().trim().lowercase()
        if (cleanSeries.isEmpty()) return trimmed

        val cleanTitle = trimmed.replace(Regex("""[«»"'"“”]"""), "").cleanWhitespace().trim().lowercase()
        if (cleanTitle.startsWith(cleanSeries)) {
            var seriesIndex = 0
            var titleIndex = 0
            while (seriesIndex < cleanSeries.length && titleIndex < trimmed.length) {
                val cTitle = trimmed[titleIndex].lowercaseChar()
                val cSeries = cleanSeries[seriesIndex]

                if (cTitle == cSeries) {
                    seriesIndex++
                    titleIndex++
                } else if (cTitle in listOf('«', '»', '"', '\'', '“', '”', ' ')) {
                    titleIndex++
                } else if (cSeries == ' ') {
                    seriesIndex++
                } else {
                    break
                }
            }
            if (seriesIndex >= cleanSeries.length) {
                val remaining = trimmed.substring(titleIndex)
                val stripped = remaining.trimStart { it == ' ' || it == '-' || it == '—' || it == '.' || it == ':' || it == '_' || it == '/' || it == '\\' }
                return stripped.ifBlank { trimmed }
            }
        }
        return trimmed
    }

    private fun String.resolveAgainst(baseUrl: String): String {
        val raw = replace("\\/", "/").trim()
        if (raw.isBlank()) return ""
        return runCatching { URI(baseUrl).resolve(raw).toString() }.getOrDefault(raw)
    }

    private fun JSONObject.firstString(vararg keys: String): String {
        keys.forEach { key ->
            when (val value = opt(key)) {
                is String -> if (value.isNotBlank()) return value.cleanWhitespace()
                is JSONObject -> value.firstString("url", "src", "href").takeIf { it.isNotBlank() }?.let { return it }
                is JSONArray -> value.optString(0).takeIf { it.isNotBlank() }?.let { return it.cleanWhitespace() }
            }
        }
        return ""
    }

    private fun JSONObject.optFiniteDouble(key: String): Double? {
        val value = optDouble(key, Double.NaN)
        return value.takeIf { !it.isNaN() && it.isFinite() }
    }

    private fun titleFromUrl(url: String): String =
        URLDecoder.decode(url.substringBefore('?').substringAfterLast('/'), Charsets.UTF_8.name())
            .removeSuffix(".html")
            .replace('-', ' ')
            .cleanTitle()

    private fun firstNonBlank(vararg values: String?): String =
        values.firstOrNull { !it.isNullOrBlank() }.orEmpty().trim()

    private fun isReaderImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (imageExtensionFromUrl(lower) == null) return false
        return IgnoredImageMarkers.none { marker -> lower.contains(marker) }
    }

    private companion object {
        private const val MaxSearchResults = 60
        private val ImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")
        private val IgnoredTitles = setOf(
            "Com-X .life",
            "Войти",
            "Регистрация",
            "Каталог",
            "Манга",
            "Комиксы",
            "Загрузить еще",
            "Открыть FAQ",
        )
        private val IgnoredImageMarkers = listOf(
            "avatar",
            "favicon",
            "logo",
            "sprite",
            "/templates/",
            "/static/",
            "/emoji",
            "smiles",
            "rating",
        )
        private val NumberRegex = Regex("""\d+([.,]\d+)?""")
        private val ImageUrlRegex =
            Regex("""https?:\\?/\\?/[^\s"'<>]+?\.(?:jpe?g|png|webp|gif)(?:\?[^\s"'<>]*)?""")
    }
}

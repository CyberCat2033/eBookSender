package com.cybercat.pocketbooksender.data.manga

import android.webkit.CookieManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Singleton
class ComxMangaAdapter @Inject constructor() : HtmlMangaSourceAdapter {
    override val id: String = SourceId
    override val title: String = "Com-X"
    override val homeUrl: String = HomeUrl
    override val browserUserAgent: String = UserAgent
    override val capabilities: MangaSourceCapabilities =
        MangaSourceCapabilities(authMode = MangaAuthMode.WebLogin)
    override val nativeLoginConfig: MangaNativeLoginConfig =
        MangaNativeLoginConfig(
            loginUrl = HomeUrl,
            showDoNotRemember = true,
        )

    override fun buildLoginPostBody(
        username: String,
        password: String,
        doNotRemember: Boolean
    ): ByteArray = buildComxLoginPostBody(username, password, doNotRemember)

    override suspend fun authState(): MangaAuthState = withContext(Dispatchers.IO) {
        val cookies = cookiesFor(HomeUrl)
        if (cookies?.hasAuthenticatedCookies() == true) {
            MangaAuthState.Authenticated(accountLabel = "WebView session")
        } else {
            MangaAuthState.Required
        }
    }

    override suspend fun searchSeries(query: String): List<MangaSeriesSearchResult> =
        withContext(Dispatchers.IO) {
            val searchUrl = buildSearchUrl(query)
            parseSearchResults(searchUrl, fetchText(searchUrl, HomeUrl))
        }

    override suspend fun getSeries(seriesId: String): MangaSeriesDetails =
        withContext(Dispatchers.IO) {
            val html = fetchText(seriesId, HomeUrl)
            parseSeriesPage(seriesId, html)?.details
                ?: parseSeriesDetails(seriesId, Jsoup.parse(html, seriesId), extractWindowData(html))
        }

    override suspend fun listChapters(seriesId: String): List<MangaChapter> =
        withContext(Dispatchers.IO) {
            parseSeriesPage(seriesId, fetchText(seriesId, HomeUrl))?.chapters.orEmpty()
        }

    override suspend fun getSeriesPage(seriesId: String): MangaSeriesPage =
        withContext(Dispatchers.IO) {
            parseSeriesPage(seriesId, fetchText(seriesId, HomeUrl))
                ?: throw IOException("Cannot parse manga series")
        }

    override suspend fun getChapterPages(chapterId: String): List<MangaPage> =
        withContext(Dispatchers.IO) {
            parseChapterPages(chapterId, fetchText(chapterId, HomeUrl))
        }

    override suspend fun downloadPage(page: MangaPage): MangaDownloadedPage =
        withContext(Dispatchers.IO) {
            val connection = openConnection(
                url = page.imageUrl,
                accept = "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
                referer = page.refererUrl ?: HomeUrl,
                connectTimeout = ImageConnectTimeoutMillis,
                readTimeout = ImageReadTimeoutMillis,
            )
            try {
                val code = connection.responseCode
                captureCookies(connection, page.imageUrl)
                if (code !in 200..299) {
                    throw IOException("Image HTTP $code")
                }
                val bytes = connection.readImageBytes()
                val extension = page.fileExtension
                    ?: extensionFromContentType(connection.contentType)
                    ?: extensionFromUrl(page.imageUrl)
                    ?: "jpg"
                MangaDownloadedPage(bytes = bytes, fileExtension = extension)
            } finally {
                connection.disconnect()
            }
        }

    override suspend fun downloadChapterArchive(
        chapter: MangaChapter,
        outputFile: File,
        onProgress: suspend (bytesRead: Long, totalBytes: Long?) -> Unit,
    ): MangaDownloadedArchive? = withContext(Dispatchers.IO) {
        val originalDownloadUrl = chapter.downloadUrl?.takeIf { it.isNotBlank() } ?: return@withContext null
        val downloadUrl = requestAuthorizedArchiveUrl(chapter, originalDownloadUrl)
        val connection = openConnection(
            url = downloadUrl,
            accept = "application/vnd.comicbook-rar,application/vnd.comicbook+zip,application/x-rar-compressed,application/zip,application/octet-stream,*/*",
            referer = chapter.seriesId,
            connectTimeout = ArchiveConnectTimeoutMillis,
            readTimeout = ArchiveReadTimeoutMillis,
        )
        try {
            connection.setRequestProperty("Sec-Fetch-Dest", "document")
            connection.setRequestProperty("Sec-Fetch-Mode", "navigate")
            connection.setRequestProperty("Sec-Fetch-Site", "same-site")
            connection.setRequestProperty("Sec-Fetch-User", "?1")
            connection.setRequestProperty("Upgrade-Insecure-Requests", "1")

            val code = connection.responseCode
            captureCookies(connection, downloadUrl)
            if (code !in 200..299) {
                throw IOException("Archive HTTP $code${connection.readErrorSnippet().messageSuffix()}")
            }

            outputFile.parentFile?.mkdirs()
            val totalBytes = connection.contentLengthLong
                .takeIf { length -> length > 0L }
            connection.inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(DefaultArchiveBufferSize)
                    var bytesRead = 0L
                    var lastReportedBytes = -ArchiveProgressReportBytes
                    onProgress(0L, totalBytes)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (
                            bytesRead - lastReportedBytes >= ArchiveProgressReportBytes ||
                            bytesRead == totalBytes
                        ) {
                            lastReportedBytes = bytesRead
                            onProgress(bytesRead, totalBytes)
                        }
                    }
                    onProgress(bytesRead, totalBytes)
                }
            }

            if (outputFile.length() < MinArchiveBytes) {
                throw IOException("Archive response is too small${outputFile.readSmallText().messageSuffix()}")
            }

            val extension = archiveExtensionFromMagic(outputFile)
                ?: archiveExtensionFromDisposition(connection.getHeaderField("Content-Disposition"))
                ?: archiveExtensionFromContentType(connection.contentType)
                ?: archiveExtensionFromUrl(downloadUrl)
                ?: archiveExtensionFromUrl(originalDownloadUrl)
                ?: throw IOException("Archive format is unknown${outputFile.readSmallText().messageSuffix()}")

            MangaDownloadedArchive(fileExtension = extension)
        } finally {
            connection.disconnect()
        }
    }

    override fun buildSearchUrl(query: String): String {
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
            .replace("+", "%20")
        return "${HomeUrl}search/$encoded"
    }

    override fun ownsUrl(url: String): Boolean =
        runCatching {
            URI(url).host.orEmpty().removePrefix("www.") == "com-x.life"
        }.getOrDefault(false)

    override fun parseSearchResults(url: String, html: String): List<MangaSeriesSearchResult> {
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

    override fun parseSeriesPage(url: String, html: String): MangaSeriesPage? {
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

    override fun parseChapterPages(url: String, html: String): List<MangaPage> {
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
                fileExtension = extensionFromUrl(imageUrl),
            )
        }
    }

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
            sourceId = id,
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
                val chapterUrl = "${HomeUrl}reader/$newsId/$chapterId"
                val title = firstNonBlank(
                    item.firstString("title", "name"),
                    "Chapter ${index + 1}",
                ).cleanTitle().stripSeriesPrefix(seriesTitle)
                val sortNumber = item.optFiniteDouble("posi")
                    ?: item.optFiniteDouble("number")
                    ?: title.extractChapterNumber()

                MangaChapter(
                    sourceId = id,
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
                    sourceId = id,
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
                    fileExtension = extensionFromUrl(imageUrl),
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
            sourceId = id,
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
            sourceId = id,
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
        val url = json.firstString("url", "@id").resolveAgainst(HomeUrl)
        if (!ownsUrl(url) || !url.isLikelySeriesUrl()) return null

        val title = firstNonBlank(
            json.firstString("name", "title"),
            titleFromUrl(url),
        ).cleanPosterTitle()

        return MangaSeriesSearchResult(
            sourceId = id,
            seriesId = url,
            title = title,
            coverUrl = json.firstString("image", "thumbnailUrl").resolveAgainst(HomeUrl)
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

    private fun fetchText(
        url: String,
        referer: String,
        retryGuard: Boolean = true,
    ): String {
        val connection = openConnection(
            url = url,
            accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            referer = referer,
        )
        try {
            val code = connection.responseCode
            captureCookies(connection, url)
            val html = connection.readTextBody()
            if (code !in 200..299) {
                throw IOException("HTTP $code")
            }

            if (html.isGuardChallenge()) {
                if (retryGuard && solveGuardChallenge(html, url)) {
                    return fetchText(url, referer, retryGuard = false)
                }
                throw IOException("Com-X session is not ready. Open Login, let the site load, then retry.")
            }

            ensureReadableHtml(html)
            return html
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        url: String,
        accept: String,
        referer: String,
        connectTimeout: Int = ConnectTimeoutMillis,
        readTimeout: Int = ReadTimeoutMillis,
    ): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            this.connectTimeout = connectTimeout
            this.readTimeout = readTimeout
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Language", "ru,en;q=0.8")
            setRequestProperty("Referer", referer)
            setRequestProperty("User-Agent", UserAgent)
            cookiesFor(url)?.let { cookie ->
                setRequestProperty("Cookie", cookie)
            }
        }
        return connection
    }

    private suspend fun requestAuthorizedArchiveUrl(
        chapter: MangaChapter,
        fallbackUrl: String,
    ): String = withContext(Dispatchers.IO) {
        val newsId = chapter.seriesId.extractNewsId()
            ?: chapter.chapterId.extractReaderNewsId()
            ?: fallbackUrl.extractDownloadNewsId()
            ?: return@withContext fallbackUrl
        val chapterId = chapter.chapterId.extractReaderChapterId()
            ?: fallbackUrl.extractDownloadChapterId()
            ?: return@withContext fallbackUrl
        val ajaxUrl = "${HomeUrl}engine/ajax/controller.php?mod=api&action=chapters/download"
        val body = listOf(
            "news_id" to newsId.toString(),
            "chapter_id" to chapterId.toString(),
        ).joinToString("&") { (key, value) ->
            "${key.formEncode()}=${value.formEncode()}"
        }

        val connection = (URL(ajaxUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = ArchiveAuthConnectTimeoutMillis
            readTimeout = ArchiveAuthReadTimeoutMillis
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
            setRequestProperty("Accept-Language", "ru,en;q=0.8")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Origin", HomeUrl.trimEnd('/'))
            setRequestProperty("Referer", chapter.seriesId)
            setRequestProperty("User-Agent", UserAgent)
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            cookiesFor(ajaxUrl)?.let { cookie ->
                setRequestProperty("Cookie", cookie)
            }
        }

        try {
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            captureCookies(connection, ajaxUrl)
            val response = connection.readTextBody()
            if (code !in 200..299) {
                throw IOException("Archive auth HTTP $code${response.cleanWhitespace().take(MaxErrorSnippetLength).messageSuffix()}")
            }

            val json = runCatching { JSONObject(response) }
                .getOrElse {
                    throw IOException("Archive auth response is invalid${response.cleanWhitespace().take(MaxErrorSnippetLength).messageSuffix()}")
                }

            if (!json.optBoolean("success", false)) {
                val message = json.firstString("error", "message")
                    .ifBlank { "Com-X login is required for archive download" }
                throw IOException(message)
            }

            return@withContext json.firstString("data", "url", "link")
                .resolveAgainst(fallbackUrl)
                .takeIf { it.isNotBlank() }
                ?: fallbackUrl
        } finally {
            connection.disconnect()
        }
    }

    private fun solveGuardChallenge(html: String, url: String): Boolean {
        val token = GuardTokenRegex.find(html)?.groupValues?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: return false

        val target = runCatching { URI(url).resolve("/_v").toString() }
            .getOrDefault("${HomeUrl}_v")
        val body = listOf(
            "token" to token,
            "mode" to "legacy",
            "workTime" to "601",
            "iterations" to "120000",
            "hasCrypto" to "0",
            "webdriver" to "0",
            "touch" to "1",
            "screen_w" to "1440",
            "screen_h" to "3120",
            "screen_cd" to "24",
            "tz" to "-180",
            "dpr" to "3",
            "cdp" to "0",
            "cdpf" to "",
        ).joinToString("&") { (key, value) ->
            "${key.formEncode()}=${value.formEncode()}"
        }

        val connection = (URL(target).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Language", "ru,en;q=0.8")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Origin", HomeUrl.trimEnd('/'))
            setRequestProperty("Referer", url)
            setRequestProperty("User-Agent", UserAgent)
            cookiesFor(url)?.let { cookie ->
                setRequestProperty("Cookie", cookie)
            }
        }

        return try {
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            captureCookies(connection, target)
            val response = connection.readTextBody()
            code in 200..299 && response.contains("OK", ignoreCase = true)
        } catch (_: IOException) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun captureCookies(connection: HttpURLConnection, url: String) {
        connection.headerFields
            .filterKeys { key -> key != null && key.equals("Set-Cookie", ignoreCase = true) }
            .values
            .flatten()
            .forEach { cookie ->
                CookieManager.getInstance().setCookie(url, cookie)
                CookieManager.getInstance().setCookie(HomeUrl, cookie)
            }
        CookieManager.getInstance().flush()
    }

    private fun cookiesFor(url: String): String? {
        val cookies = listOfNotNull(
            CookieManager.getInstance().getCookie(url),
            CookieManager.getInstance().getCookie(HomeUrl),
        )
            .flatMap { cookieHeader -> cookieHeader.split(';') }
            .map { cookie -> cookie.trim() }
            .filter { cookie -> cookie.isNotBlank() && cookie.contains('=') }
            .distinctBy { cookie -> cookie.substringBefore('=').trim() }

        return cookies.joinToString("; ").takeIf { it.isNotBlank() }
    }

    private fun String.hasAuthenticatedCookies(): Boolean {
        val cookieNames = split(';')
            .map { cookie -> cookie.substringBefore('=').trim().lowercase() }
            .filter { name -> name.isNotBlank() }
            .toSet()

        return DleUserIdCookieName in cookieNames &&
            DlePasswordCookieName in cookieNames
    }

    private fun HttpURLConnection.readTextBody(): String {
        val stream = if (responseCode in 200..399) {
            inputStream
        } else {
            errorStream ?: inputStream
        }
        return stream.bufferedReader().use { reader -> reader.readText() }
    }

    private fun HttpURLConnection.readErrorSnippet(): String =
        runCatching {
            (errorStream ?: inputStream)
                .bufferedReader()
                .use { reader -> reader.readText() }
                .cleanWhitespace()
                .take(MaxErrorSnippetLength)
        }.getOrDefault("")

    private fun File.readSmallText(): String =
        takeIf { it.exists() && it.length() in 1..MaxSmallTextBytes }
            ?.let { file ->
                runCatching { file.readText(Charsets.UTF_8).cleanWhitespace().take(MaxErrorSnippetLength) }
                    .getOrDefault("")
            }
            .orEmpty()

    private fun String.messageSuffix(): String =
        takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()

    private fun HttpURLConnection.readImageBytes(): ByteArray {
        val expectedLength = contentLengthLong
        if (expectedLength > MaxImageBytes) {
            throw IOException("Image is too large: $expectedLength bytes")
        }

        val initialSize = when {
            expectedLength in 1..MaxImageBytes -> expectedLength.toInt()
            else -> DefaultImageBufferSize
        }
        val deadline = System.nanoTime() + ImageTotalReadTimeoutNanos
        val output = ByteArrayOutputStream(initialSize)
        val buffer = ByteArray(DefaultImageBufferSize)

        inputStream.use { input ->
            while (true) {
                if (System.nanoTime() > deadline) {
                    throw IOException("Image read timeout")
                }

                val read = input.read(buffer)
                if (read < 0) break

                output.write(buffer, 0, read)
                if (output.size() > MaxImageBytes) {
                    throw IOException("Image is too large")
                }
            }
        }

        val bytes = output.toByteArray()
        if (bytes.isEmpty()) {
            throw IOException("Image is empty")
        }
        return bytes
    }

    private fun ensureReadableHtml(html: String) {
        if (html.isGuardChallenge()) {
            throw IOException("Com-X session is not ready. Open Login, let the site load, then retry.")
        }
    }

    private fun String.isGuardChallenge(): Boolean =
        (contains("/_v") && contains("Для доступа к сайту необходимо включить JavaScript")) ||
            (contains("targetUrl") && contains("token:") && contains("x.open(\"POST\", \"/_v\""))

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
        return lower.startsWith(HomeUrl) &&
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

    private fun String.extractReaderNewsId(): Long? =
        Regex("""/reader/(\d+)/\d+""").find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

    private fun String.extractReaderChapterId(): Long? =
        Regex("""/reader/\d+/(\d+)""").find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

    private fun String.extractDownloadNewsId(): Long? =
        Regex("""/download/(\d+)-\d+""").find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

    private fun String.extractDownloadChapterId(): Long? =
        Regex("""/download/\d+-(\d+)""").find(this)
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

    private fun extensionFromContentType(contentType: String?): String? {
        val normalized = contentType.orEmpty().substringBefore(';').trim().lowercase()
        return when (normalized) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> null
        }
    }

    private fun archiveExtensionFromContentType(contentType: String?): String? {
        val normalized = contentType.orEmpty().substringBefore(';').trim().lowercase()
        return when {
            normalized.contains("comicbook-rar") -> "cbr"
            normalized.contains("x-rar") || normalized.contains("rar") -> "cbr"
            normalized.contains("comicbook+zip") -> "cbz"
            normalized.contains("zip") -> "cbz"
            else -> null
        }
    }

    private fun archiveExtensionFromDisposition(disposition: String?): String? {
        val fileName = disposition
            ?.let { value ->
                Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
                    .find(value)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
                    ?: Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
                        .find(value)
                        ?.groupValues
                        ?.getOrNull(1)
            }
            ?.trim()
            .orEmpty()

        return archiveExtensionFromUrl(fileName)
    }

    private fun archiveExtensionFromMagic(file: File): String? {
        val header = ByteArray(8)
        val read = file.inputStream().use { input -> input.read(header) }
        return when {
            read >= 4 &&
                header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] in ZipMagicThirdBytes -> "cbz"
            read >= 7 &&
                header[0] == 0x52.toByte() &&
                header[1] == 0x61.toByte() &&
                header[2] == 0x72.toByte() &&
                header[3] == 0x21.toByte() &&
                header[4] == 0x1A.toByte() &&
                header[5] == 0x07.toByte() -> "cbr"
            else -> null
        }
    }

    private fun archiveExtensionFromUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        return when (path.substringAfterLast('.', "").lowercase()) {
            "cbr", "rar" -> "cbr"
            "cbz", "zip" -> "cbz"
            else -> null
        }
    }

    private fun extensionFromUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        return path.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it in ImageExtensions }
    }

    private fun isReaderImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (extensionFromUrl(lower) == null) return false
        return IgnoredImageMarkers.none { marker -> lower.contains(marker) }
    }

    companion object {
        const val SourceId = "comx"
        const val HomeUrl = "https://com-x.life/"
        private const val ConnectTimeoutMillis = 15_000
        private const val ReadTimeoutMillis = 30_000
        private const val ImageConnectTimeoutMillis = 8_000
        private const val ImageReadTimeoutMillis = 8_000
        private const val ImageTotalReadTimeoutMillis = 18_000L
        private const val ImageTotalReadTimeoutNanos = ImageTotalReadTimeoutMillis * 1_000_000L
        private const val ArchiveAuthConnectTimeoutMillis = 10_000
        private const val ArchiveAuthReadTimeoutMillis = 20_000
        private const val ArchiveConnectTimeoutMillis = 12_000
        private const val ArchiveReadTimeoutMillis = 45_000
        private const val DefaultArchiveBufferSize = 64 * 1024
        private const val ArchiveProgressReportBytes = 256L * 1024L
        private const val DefaultImageBufferSize = 32 * 1024
        private const val MaxImageBytes = 50L * 1024L * 1024L
        private const val MinArchiveBytes = 512L
        private const val MaxSmallTextBytes = 4096L
        private const val MaxErrorSnippetLength = 180

        const val UserAgent =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

        private const val MaxSearchResults = 60

        private val ZipMagicThirdBytes = setOf(
            0x03.toByte(),
            0x05.toByte(),
            0x07.toByte(),
        )
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
        private val GuardTokenRegex = Regex("""token:\s*["']([^"']+)["']""")
        private val ImageUrlRegex =
            Regex("""https?:\\?/\\?/[^\s"'<>]+?\.(?:jpe?g|png|webp|gif)(?:\?[^\s"'<>]*)?""")
        private const val DleUserIdCookieName = "dle_user_id"
        private const val DlePasswordCookieName = "dle_password"
    }
}

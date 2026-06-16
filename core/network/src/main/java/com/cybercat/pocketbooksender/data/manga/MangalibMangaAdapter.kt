package com.cybercat.pocketbooksender.data.manga

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangalibMangaAdapter @Inject constructor() : HtmlMangaSourceAdapter {
    override val id: String = SourceId
    override val title: String = "MangaLib"
    override val homeUrl: String = HomeUrl
    override val browserUserAgent: String = UserAgent

    override val capabilities: MangaSourceCapabilities =
        MangaSourceCapabilities(
            authMode = MangaAuthMode.None,
            supportsSearch = true,
            supportsChapterRanges = true,
        )

    override suspend fun authState(): MangaAuthState =
        MangaAuthState.NotRequired

    override suspend fun searchSeries(query: String): List<MangaSeriesSearchResult> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
                .replace("+", "%20")
            val apiUrl = "https://api2.mangalib.me/api/manga?site_id[]=1&q=$encoded"
            val response = fetchText(apiUrl, HomeUrl)
            parseSearchJson(response)
        }

    private fun parseSearchJson(jsonStr: String): List<MangaSeriesSearchResult> {
        val root = JSONObject(jsonStr)
        val data = root.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val obj = data.optJSONObject(i) ?: return@mapNotNull null
            val slug = obj.optString("slug") ?: return@mapNotNull null
            val name = obj.optString("rus_name").takeIf { it.isNotBlank() }
                ?: obj.optString("name").takeIf { it.isNotBlank() }
                ?: slug
            
            val coverObj = obj.optJSONObject("cover")
            val coverUrl = coverObj?.optString("default")
                ?: coverObj?.optString("thumbnail")
                
            val absoluteCoverUrl = coverUrl?.let { url ->
                if (url.startsWith("http")) url else "https://cover.imglib.info$url"
            }

            val seriesUrl = "${HomeUrl}manga/$slug"
            MangaSeriesSearchResult(
                sourceId = id,
                seriesId = seriesUrl,
                title = name,
                coverUrl = absoluteCoverUrl,
            )
        }
    }

    override suspend fun getSeries(seriesId: String): MangaSeriesDetails =
        withContext(Dispatchers.IO) {
            val html = fetchText(seriesId, HomeUrl)
            val seriesPage = parseSeriesPage(seriesId, html) ?: throw IOException("Failed to parse series page details")
            seriesPage.details
        }

    override suspend fun listChapters(seriesId: String): List<MangaChapter> =
        withContext(Dispatchers.IO) {
            val slug = extractSlug(seriesId) ?: throw IOException("Invalid series ID: $seriesId")
            val apiUrl = "https://api2.mangalib.me/api/manga/$slug/chapters"
            val response = fetchText(apiUrl, seriesId)
            parseChaptersJson(seriesId, response)
        }

    private fun parseChaptersJson(seriesId: String, jsonStr: String): List<MangaChapter> {
        val root = JSONObject(jsonStr)
        val data = root.optJSONArray("data") ?: return emptyList()
        val slug = extractSlug(seriesId) ?: return emptyList()
        
        return (0 until data.length()).mapNotNull { i ->
            val obj = data.optJSONObject(i) ?: return@mapNotNull null
            val volume = obj.optString("volume", "1")
            val number = obj.optString("number", "1")
            val name = obj.optString("name", "")
            val sortNumber = number.toDoubleOrNull()
            
            val branches = obj.optJSONArray("branches")
            val branchId = if (branches != null && branches.length() > 0) {
                val b = branches.getJSONObject(0)
                b.optLong("id", -1L).takeIf { it > 0L }
            } else null
            
            val chapterUrl = buildString {
                append(HomeUrl)
                append("manga/")
                append(slug)
                append("/v")
                append(volume)
                append("/c")
                append(number)
                if (branchId != null) {
                    append("?branch_id=")
                    append(branchId)
                }
            }
            
            val title = if (name.isNotBlank()) "Vol. $volume Ch. $number - $name" else "Vol. $volume Ch. $number"
            
            MangaChapter(
                sourceId = id,
                seriesId = seriesId,
                chapterId = chapterUrl,
                stableKey = chapterUrl.normalizeUrlKey(),
                title = title,
                numberForSort = sortNumber,
                publishedAtMillis = null,
            )
        }
        .distinctBy { it.stableKey }
        .sortedBy { it.numberForSort ?: Double.MAX_VALUE }
    }

    override suspend fun getChapterPages(chapterId: String): List<MangaPage> =
        withContext(Dispatchers.IO) {
            val html = fetchText(chapterId, HomeUrl)
            parseChapterPages(chapterId, html)
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

    override fun buildSearchUrl(query: String): String {
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
            .replace("+", "%20")
        return "${HomeUrl}manga-list?name=$encoded"
    }

    override fun ownsUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("mangalib.me") || lower.contains("mangalib.org")
    }

    override fun parseSearchResults(url: String, html: String): List<MangaSeriesSearchResult> {
        val doc = Jsoup.parse(html, url)
        val cards = doc.select("a[href*=/manga/]")
        return cards.mapNotNull { a ->
            val href = a.absUrl("href")
            val slug = extractSlug(href) ?: return@mapNotNull null
            val title = a.select("h3, h4, .title, .name").firstOrNull()?.text()?.trim()
                ?: a.text().trim()
                ?: slug
            if (title.isBlank()) return@mapNotNull null
            
            val img = a.select("img").firstOrNull()
            val coverUrl = img?.absUrl("src")?.takeIf { it.isNotBlank() }
                ?: img?.absUrl("data-src")?.takeIf { it.isNotBlank() }

            MangaSeriesSearchResult(
                sourceId = id,
                seriesId = href,
                title = title,
                coverUrl = coverUrl,
            )
        }.distinctBy { it.seriesId }
    }

    override fun parseSeriesPage(url: String, html: String): MangaSeriesPage? {
        val doc = Jsoup.parse(html, url)
        val title = doc.select("meta[property=og:title]").firstOrNull()?.attr("content")
            ?: doc.select("h1").firstOrNull()?.text()
            ?: doc.title()
        if (title.isBlank()) return null
        
        val coverUrl = doc.select("meta[property=og:image]").firstOrNull()?.attr("content")
            ?: doc.select("img.manga-cover").firstOrNull()?.absUrl("src")
            
        val description = doc.select("meta[name=description]").firstOrNull()?.attr("content")
            ?: doc.select("meta[property=og:description]").firstOrNull()?.attr("content")
            
        val details = MangaSeriesDetails(
            sourceId = id,
            seriesId = url,
            title = title,
            coverUrl = coverUrl,
            description = description,
        )

        val slug = extractSlug(url) ?: return null
        val chapterLinks = doc.select("a")
            .mapNotNull { anchor ->
                val href = anchor.absUrl("href")
                if (!href.contains("/v") || !href.contains("/c")) return@mapNotNull null
                if (!href.contains(slug)) return@mapNotNull null
                
                val volMatch = Regex("/v([^/]+)").find(href)
                val chMatch = Regex("/c([^/?]+)").find(href)
                if (volMatch == null || chMatch == null) return@mapNotNull null
                
                val volumeStr = volMatch.groupValues[1]
                val chapterStr = chMatch.groupValues[1]
                val chapterNum = chapterStr.toDoubleOrNull()
                
                val titleText = anchor.text().trim()
                val chapterTitle = if (titleText.isNotBlank()) titleText else "Vol. $volumeStr Ch. $chapterStr"
                
                MangaChapter(
                    sourceId = id,
                    seriesId = url,
                    chapterId = href,
                    stableKey = href.normalizeUrlKey(),
                    title = chapterTitle,
                    numberForSort = chapterNum,
                    publishedAtMillis = null,
                )
            }
            .distinctBy { it.stableKey }
            .sortedBy { it.numberForSort ?: Double.MAX_VALUE }

        return MangaSeriesPage(details = details, chapters = chapterLinks)
    }

    override fun parseChapterPages(url: String, html: String): List<MangaPage> {
        val pgMatch = Regex("window\\.__pg\\s*=\\s*(\\[.*?\\])\\s*;").find(html)
        val infoMatch = Regex("window\\.__info\\s*=\\s*(\\{.*?\\})\\s*;").find(html)
        if (pgMatch == null || infoMatch == null) {
            val nextDataMatch = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>").find(html)
            if (nextDataMatch != null) {
                return parseNextDataPages(url, nextDataMatch.groupValues[1])
            }
            return emptyList()
        }
        
        val pgJson = JSONArray(pgMatch.groupValues[1])
        val infoJson = JSONObject(infoMatch.groupValues[1])
        
        val servers = infoJson.optJSONObject("servers") ?: return emptyList()
        val img = infoJson.optJSONObject("img") ?: return emptyList()
        
        val serverKey = img.optString("server", "main")
        val serverUrl = servers.optString(serverKey).takeIf { it.isNotBlank() }
            ?: servers.optString("main").takeIf { it.isNotBlank() }
            ?: "https://img3.cdnlibs.org"
            
        val imgUrlPath = img.optString("url", "")
        
        return (0 until pgJson.length()).mapNotNull { i ->
            val pageObj = pgJson.optJSONObject(i) ?: return@mapNotNull null
            val filename = pageObj.optString("u").takeIf { it.isNotBlank() }
                ?: pageObj.optString("url").takeIf { it.isNotBlank() }
                ?: pageObj.optString("p")
            if (filename.isBlank()) return@mapNotNull null
            
            val fullImageUrl = buildString {
                append(serverUrl.trimEnd('/'))
                if (!imgUrlPath.startsWith('/')) append('/')
                append(imgUrlPath)
                if (!imgUrlPath.endsWith('/') && !filename.startsWith('/')) append('/')
                append(filename)
            }
            
            MangaPage(
                index = i,
                imageUrl = fullImageUrl,
                refererUrl = url,
                fileExtension = extensionFromUrl(fullImageUrl),
            )
        }
    }

    private fun parseNextDataPages(url: String, jsonStr: String): List<MangaPage> {
        val root = JSONObject(jsonStr)
        val props = root.optJSONObject("props") ?: return emptyList()
        val pageProps = props.optJSONObject("pageProps") ?: return emptyList()
        
        val chapter = pageProps.optJSONObject("chapter") ?: pageProps
        val reader = pageProps.optJSONObject("reader") ?: chapter
        
        val pgJson = reader.optJSONArray("pages") 
            ?: reader.optJSONArray("images")
            ?: return emptyList()
            
        val servers = reader.optJSONObject("servers")
            ?: pageProps.optJSONObject("servers")
            ?: JSONObject().apply { put("main", "https://img3.cdnlibs.org") }
            
        val serverKey = reader.optString("server", "main")
        val serverUrl = servers.optString(serverKey).takeIf { it.isNotBlank() }
            ?: servers.optString("main").takeIf { it.isNotBlank() }
            ?: "https://img3.cdnlibs.org"
            
        val imgUrlPath = reader.optString("url", "")
        
        return (0 until pgJson.length()).mapNotNull { i ->
            val pageObj = pgJson.optJSONObject(i)
            val filename = if (pageObj != null) {
                pageObj.optString("u").takeIf { it.isNotBlank() }
                    ?: pageObj.optString("url").takeIf { it.isNotBlank() }
                    ?: pageObj.optString("p")
            } else {
                pgJson.optString(i)
            }
            if (filename.isNullOrBlank()) return@mapNotNull null
            
            val fullImageUrl = buildString {
                append(serverUrl.trimEnd('/'))
                if (!imgUrlPath.startsWith('/')) append('/')
                append(imgUrlPath)
                if (!imgUrlPath.endsWith('/') && !filename.startsWith('/')) append('/')
                append(filename)
            }
            
            MangaPage(
                index = i,
                imageUrl = fullImageUrl,
                refererUrl = url,
                fileExtension = extensionFromUrl(fullImageUrl),
            )
        }
    }

    private fun extractSlug(url: String): String? {
        val match = Regex("/(?:ru/)?manga/([^/?#]+)").find(url)
        return match?.groupValues?.get(1)
    }

    private fun String.normalizeUrlKey(): String {
        return substringBefore('?').substringBefore('#').trimEnd('/')
    }

    private fun fetchText(url: String, referer: String): String {
        val connection = openConnection(
            url = url,
            accept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            referer = referer,
        )
        try {
            val code = connection.responseCode
            captureCookies(connection, url)
            if (code !in 200..299) {
                throw IOException("HTTP $code")
            }
            return connection.readTextBody()
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

    private fun HttpURLConnection.readTextBody(): String {
        val stream = if (responseCode in 200..399) inputStream else (errorStream ?: inputStream)
        return stream.bufferedReader().use { reader -> reader.readText() }
    }

    private fun HttpURLConnection.readImageBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        inputStream.use { input ->
            val buffer = ByteArray(DefaultImageBufferSize)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun extensionFromContentType(contentType: String?): String? {
        if (contentType == null) return null
        val mime = contentType.substringBefore(';').trim().lowercase()
        return when (mime) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> null
        }
    }

    private fun extensionFromUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        return path.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it in ImageExtensions }
    }

    companion object {
        const val SourceId = "mangalib"
        const val HomeUrl = "https://mangalib.me/"
        private const val ConnectTimeoutMillis = 15_000
        private const val ReadTimeoutMillis = 30_000
        private const val ImageConnectTimeoutMillis = 8_000
        private const val ImageReadTimeoutMillis = 8_000
        private const val DefaultImageBufferSize = 32 * 1024
        private val ImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")
        const val UserAgent =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    }
}

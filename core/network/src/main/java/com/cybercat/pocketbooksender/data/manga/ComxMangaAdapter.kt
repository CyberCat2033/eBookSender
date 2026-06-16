package com.cybercat.pocketbooksender.data.manga

import java.io.File
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ComxMangaAdapter @Inject constructor(
    private val parser: ComxHtmlParser,
    private val httpClient: ComxMangaHttpClient,
) : HtmlMangaSourceAdapter {
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
        if (httpClient.hasAuthenticatedSession()) {
            MangaAuthState.Authenticated(accountLabel = "WebView session")
        } else {
            MangaAuthState.Required
        }
    }

    override suspend fun searchSeries(query: String): List<MangaSeriesSearchResult> =
        withContext(Dispatchers.IO) {
            val searchUrl = buildSearchUrl(query)
            parser.parseSearchResults(searchUrl, httpClient.fetchText(searchUrl, HomeUrl))
        }

    override suspend fun getSeries(seriesId: String): MangaSeriesDetails =
        withContext(Dispatchers.IO) {
            val html = httpClient.fetchText(seriesId, HomeUrl)
            parser.parseSeriesPage(seriesId, html)?.details
                ?: parser.parseSeriesDetails(seriesId, html)
        }

    override suspend fun listChapters(seriesId: String): List<MangaChapter> =
        withContext(Dispatchers.IO) {
            parser.parseSeriesPage(seriesId, httpClient.fetchText(seriesId, HomeUrl))?.chapters.orEmpty()
        }

    override suspend fun getSeriesPage(seriesId: String): MangaSeriesPage =
        withContext(Dispatchers.IO) {
            parser.parseSeriesPage(seriesId, httpClient.fetchText(seriesId, HomeUrl))
                ?: throw IOException("Cannot parse manga series")
        }

    override suspend fun getChapterPages(chapterId: String): List<MangaPage> =
        withContext(Dispatchers.IO) {
            parser.parseChapterPages(chapterId, httpClient.fetchText(chapterId, HomeUrl))
        }

    override suspend fun downloadPage(page: MangaPage): MangaDownloadedPage =
        httpClient.downloadPage(page)

    override suspend fun downloadChapterArchive(
        chapter: MangaChapter,
        outputFile: File,
        onProgress: suspend (bytesRead: Long, totalBytes: Long?) -> Unit,
    ): MangaDownloadedArchive? =
        httpClient.downloadChapterArchive(chapter, outputFile, onProgress)

    override fun buildSearchUrl(query: String): String {
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
            .replace("+", "%20")
        return "${HomeUrl}search/$encoded"
    }

    override fun ownsUrl(url: String): Boolean =
        parser.ownsUrl(url)

    override fun parseSearchResults(url: String, html: String): List<MangaSeriesSearchResult> =
        parser.parseSearchResults(url, html)

    override fun parseSeriesPage(url: String, html: String): MangaSeriesPage? =
        parser.parseSeriesPage(url, html)

    override fun parseChapterPages(url: String, html: String): List<MangaPage> =
        parser.parseChapterPages(url, html)

    companion object {
        const val SourceId = "comx"
        const val HomeUrl = "https://com-x.life/"
        const val UserAgent =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    }
}

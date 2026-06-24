package com.cybercat.ebooksender.data.manga

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
    private val userAgentProvider: ComxUserAgentProvider
) : HtmlMangaSourceAdapter {
    override val id: String = SOURCE_ID
    override val title: String = "Com-X"
    override val homeUrl: String = HOME_URL
    override val browserUserAgent: String
        get() = userAgentProvider.userAgent
    override val capabilities: MangaSourceCapabilities =
        MangaSourceCapabilities(
            authMode = MangaAuthMode.WebLogin,
            maxParallelChapters = 1,
            maxParallelPages = 3
        )
    override val nativeLoginConfig: MangaNativeLoginConfig =
        MangaNativeLoginConfig(
            loginUrl = HOME_URL,
            showDoNotRemember = true
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
            parser.parseSearchResults(searchUrl, httpClient.fetchText(searchUrl, HOME_URL))
        }

    override suspend fun getSeries(seriesId: String): MangaSeriesDetails =
        withContext(Dispatchers.IO) {
            val html = httpClient.fetchText(seriesId, HOME_URL)
            parser.parseSeriesPage(seriesId, html)?.details
                ?: parser.parseSeriesDetails(seriesId, html)
        }

    override suspend fun listChapters(seriesId: String): List<MangaChapter> =
        withContext(Dispatchers.IO) {
            parser.parseSeriesPage(
                seriesId,
                httpClient.fetchText(seriesId, HOME_URL)
            )?.chapters.orEmpty()
        }

    override suspend fun getSeriesPage(seriesId: String): MangaSeriesPage =
        withContext(Dispatchers.IO) {
            parser.parseSeriesPage(seriesId, httpClient.fetchText(seriesId, HOME_URL))
                ?: throw IOException("Cannot parse manga series")
        }

    override suspend fun getChapterPages(chapterId: String): List<MangaPage> =
        withContext(Dispatchers.IO) {
            parser.parseChapterPages(chapterId, httpClient.fetchText(chapterId, HOME_URL))
        }

    override suspend fun downloadPage(page: MangaPage, outputFile: File): MangaDownloadedPage =
        httpClient.downloadPage(page, outputFile)

    override suspend fun downloadChapterArchive(
        chapter: MangaChapter,
        outputFile: File,
        onProgress: suspend (bytesRead: Long, totalBytes: Long?) -> Unit
    ): MangaDownloadedArchive? = httpClient.downloadChapterArchive(chapter, outputFile, onProgress)

    override fun buildSearchUrl(query: String): String {
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
            .replace("+", "%20")
        return "${HOME_URL}search/$encoded"
    }

    override fun ownsUrl(url: String): Boolean = parser.ownsUrl(url)

    override fun parseSearchResults(url: String, html: String): List<MangaSeriesSearchResult> =
        parser.parseSearchResults(url, html)

    override fun parseSeriesPage(url: String, html: String): MangaSeriesPage? =
        parser.parseSeriesPage(url, html)

    override fun parseChapterPages(url: String, html: String): List<MangaPage> =
        parser.parseChapterPages(url, html)

    companion object {
        const val SOURCE_ID = "comx"
        const val HOME_URL = "https://com-x.life/"
    }
}

package com.cybercat.ebooksender.data.manga

import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class MangaLibMangaAdapter @Inject constructor(
    private val parser: MangaLibParser,
    private val httpClient: MangaLibMangaHttpClient,
    private val userAgentProvider: ComxUserAgentProvider
) : HtmlMangaSourceAdapter {
    override val id: String = SOURCE_ID
    override val title: String = "MangaLib"
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
        MangaNativeLoginConfig(loginUrl = HOME_URL)

    override suspend fun authState(): MangaAuthState = MangaAuthState.Authenticated(
        accountLabel = "Public API session"
    )

    override suspend fun searchSeries(query: String): List<MangaSeriesSearchResult> =
        withContext(Dispatchers.IO) {
            val normalized = query.trim()
            if (normalized.isBlank()) {
                emptyList()
            } else {
                parser.parseSearchResults(httpClient.fetchJson(searchApiUrl(normalized), HOME_URL))
            }
        }

    override suspend fun getSeries(seriesId: String): MangaSeriesDetails =
        withContext(Dispatchers.IO) {
            parser.parseSeriesDetails(
                seriesId,
                httpClient.fetchJson(seriesApiUrl(seriesId), HOME_URL)
            )
        }

    override suspend fun listChapters(seriesId: String): List<MangaChapter> =
        withContext(Dispatchers.IO) {
            parser.parseChapters(seriesId, httpClient.fetchJson(chaptersApiUrl(seriesId), HOME_URL))
        }

    override suspend fun getSeriesPage(seriesId: String): MangaSeriesPage =
        withContext(Dispatchers.IO) {
            val details = getSeries(seriesId)
            val chapters = listChapters(details.seriesId)
            MangaSeriesPage(details = details, chapters = chapters)
        }

    override suspend fun getChapterPages(chapterId: String): List<MangaPage> =
        withContext(Dispatchers.IO) {
            parser.parseChapterPages(
                chapterId,
                httpClient.fetchJson(chapterApiUrl(MangaLibChapterId.decode(chapterId)), HOME_URL)
            )
        }

    override suspend fun downloadPage(page: MangaPage): MangaDownloadedPage =
        httpClient.downloadPage(page)

    override fun buildSearchUrl(query: String): String {
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
            .replace("+", "%20")
        return "${HOME_URL}search?type=manga&q=$encoded"
    }

    override fun ownsUrl(url: String): Boolean = ownsMangaLibUrl(url)

    override fun parseSearchResults(url: String, html: String): List<MangaSeriesSearchResult> =
        runCatching { parser.parseSearchResults(JSONObject(html)) }.getOrDefault(emptyList())

    override fun parseSeriesPage(url: String, html: String): MangaSeriesPage? = null

    override fun parseChapterPages(url: String, html: String): List<MangaPage> =
        runCatching { parser.parseChapterPages(url, JSONObject(html)) }.getOrDefault(emptyList())

    private fun searchApiUrl(query: String): String =
        "$API_BASE_URL/manga?q=${query.mangaLibApiPathSegment()}"

    private fun seriesApiUrl(seriesId: String): String =
        "$API_BASE_URL/manga/${seriesId.mangaLibApiPathSegment()}"

    private fun chaptersApiUrl(seriesId: String): String =
        "$API_BASE_URL/manga/${seriesId.mangaLibApiPathSegment()}/chapters"

    private fun chapterApiUrl(chapterId: MangaLibChapterId): String = buildString {
        append(API_BASE_URL)
        append("/manga/")
        append(chapterId.seriesId.mangaLibApiPathSegment())
        append("/chapter?volume=")
        append(chapterId.volume.mangaLibApiPathSegment())
        append("&number=")
        append(chapterId.number.mangaLibApiPathSegment())
        chapterId.branchId?.let { branchId ->
            append("&branch_id=")
            append(branchId.mangaLibApiPathSegment())
        }
    }

    companion object {
        const val SOURCE_ID = "mangalib"
        const val SITE_ID = "1"
        const val HOME_URL = "https://mangalib.me/"
        const val API_BASE_URL = "https://api.cdnlibs.org/api"
        const val IMAGE_BASE_URL = "https://img3.cdnlibs.org"

        fun chapterWebUrl(chapterId: MangaLibChapterId): String =
            "${HOME_URL}manga/${chapterId.seriesId}/v${chapterId.volume}/c${chapterId.number}"
    }
}

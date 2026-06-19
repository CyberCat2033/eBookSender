package com.cybercat.ebooksender.data.manga

import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ComxSearchParser @Inject constructor() {
    fun parseSearchResults(url: String, html: String): List<MangaSeriesSearchResult> {
        val document = Jsoup.parse(html, url)

        val searchPageResults = document
            .select("#dle-content .readed")
            .asSequence()
            .mapNotNull { block -> block.toReadedSearchResult() }
            .distinctBy { result -> result.seriesId.normalizeUrlKey() }
            .take(MAX_SEARCH_RESULTS)
            .toList()

        if (searchPageResults.isNotEmpty()) {
            return searchPageResults
        }

        val posterResults = document
            .select(
                "main a.poster[href], #dle-content a.poster[href], .sect__content a.poster[href], a.poster[href]"
            )
            .asSequence()
            .mapNotNull { anchor -> anchor.toSeriesSearchResult() }
            .distinctBy { result -> result.seriesId.normalizeUrlKey() }
            .take(MAX_SEARCH_RESULTS)
            .toList()

        if (posterResults.isNotEmpty()) {
            return posterResults
        }

        return parseJsonLdSearchResults(document)
            .distinctBy { result -> result.seriesId.normalizeUrlKey() }
            .take(MAX_SEARCH_RESULTS)
    }

    private fun Element.toReadedSearchResult(): MangaSeriesSearchResult? {
        val titleLink = selectFirst(".readed__title a[href]")
        val imageLink = selectFirst(".readed__img[href]")
        val href = firstNonBlank(
            titleLink?.absUrl("href"),
            imageLink?.absUrl("href")
        ).ifBlank { return null }

        if (!ownsComxUrl(href) || !href.isLikelySeriesUrl()) {
            return null
        }

        val title = firstNonBlank(
            titleLink?.text(),
            selectFirst("img[alt]")?.attr("alt"),
            titleFromUrl(href)
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
            sourceId = ComxMangaAdapter.SOURCE_ID,
            seriesId = href,
            title = title,
            coverUrl = selectFirst(".readed__img img, img")?.absImageUrl(),
            subtitle = meta
        )
    }

    private fun Element.toSeriesSearchResult(): MangaSeriesSearchResult? {
        val href = absUrl("href").ifBlank { return null }
        if (!ownsComxUrl(href) || !href.isLikelySeriesUrl()) {
            return null
        }

        val title = firstNonBlank(
            selectFirst(".poster__title, .poster__name, .title")?.text(),
            attr("title"),
            selectFirst("img[alt]")?.attr("alt"),
            text(),
            titleFromUrl(href)
        ).cleanPosterTitle()

        if (!title.isLikelySeriesTitle()) {
            return null
        }

        val subtitle = firstNonBlank(
            selectFirst(".poster__subtitle, .poster__desc, .poster__meta")?.text(),
            parent()?.ownText()
        ).cleanWhitespace().takeIf { it.length in 4..120 }

        return MangaSeriesSearchResult(
            sourceId = ComxMangaAdapter.SOURCE_ID,
            seriesId = href,
            title = title,
            coverUrl = selectFirst("img")?.absImageUrl(),
            subtitle = subtitle
        )
    }

    private fun parseJsonLdSearchResults(document: Document): List<MangaSeriesSearchResult> =
        document.select("script[type=application/ld+json]")
            .flatMap { script ->
                parseJsonLdNode(script.data().ifBlank { script.html() })
            }
            .filter { result -> result.title.isLikelySeriesTitle() }

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
        val url = json.firstString("url", "@id").resolveAgainst(ComxMangaAdapter.HOME_URL)
        if (!ownsComxUrl(url) || !url.isLikelySeriesUrl()) return null

        val title = firstNonBlank(
            json.firstString("name", "title"),
            titleFromUrl(url)
        ).cleanPosterTitle()

        return MangaSeriesSearchResult(
            sourceId = ComxMangaAdapter.SOURCE_ID,
            seriesId = url,
            title = title,
            coverUrl = json.firstString(
                "image",
                "thumbnailUrl"
            ).resolveAgainst(ComxMangaAdapter.HOME_URL)
                .takeIf { it.isNotBlank() }
        )
    }

    private companion object {
        private const val MAX_SEARCH_RESULTS = 60
    }
}

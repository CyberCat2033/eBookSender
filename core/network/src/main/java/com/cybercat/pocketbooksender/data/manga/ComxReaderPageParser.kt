package com.cybercat.pocketbooksender.data.manga

import java.net.URI
import javax.inject.Inject
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class ComxReaderPageParser @Inject constructor() {
    fun parseChapterPages(url: String, html: String): List<MangaPage> {
        val document = Jsoup.parse(html, url)
        val dataPages = parseReaderDataPages(url, extractComxWindowData(document))
        if (dataPages.isNotEmpty()) {
            return dataPages
        }

        val scriptDataPages = parseScriptReaderDataPages(url, document)
        if (scriptDataPages.isNotEmpty()) {
            return scriptDataPages
        }

        val urls = linkedImageUrls(document)
            .distinctBy { it.normalizeUrlKey() }
            .filter(::isReaderImageUrl)

        return urls.mapIndexed { index, imageUrl ->
            MangaPage(
                index = index,
                imageUrl = imageUrl,
                refererUrl = url,
                fileExtension = comxImageExtensionFromUrl(imageUrl)
            )
        }
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
                    fileExtension = comxImageExtensionFromUrl(imageUrl)
                )
            }
            .toList()
    }

    private fun parseScriptReaderDataPages(url: String, document: Document): List<MangaPage> =
        extractComxScriptJsonObjects(document)
            .asSequence()
            .flatMap { data -> parseReaderDataPages(url, data).asSequence() }
            .distinctBy { page -> page.imageUrl.normalizeUrlKey() }
            .mapIndexed { index, page -> page.copy(index = index) }
            .toList()

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

        return urls
    }

    private fun isReaderImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (comxImageExtensionFromUrl(lower) == null) return false
        return ComxIgnoredImageMarkers.none { marker -> lower.contains(marker) }
    }
}

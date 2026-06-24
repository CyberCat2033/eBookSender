package com.cybercat.ebooksender.data.manga

import java.net.URI
import java.net.URLEncoder
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

internal fun ownsMangaLibUrl(url: String): Boolean = runCatching {
    val host = URI(url).host.orEmpty().removePrefix("www.").lowercase()
    host == "mangalib.me" ||
        host == "api.cdnlibs.org" ||
        host == "cover.cdnlibs.org" ||
        host == "img3.cdnlibs.org" ||
        host == "crops.mangalib.me"
}.getOrDefault(false)

internal fun mangaLibImageExtensionFromUrl(url: String): String? {
    val path = url.substringBefore('?').substringBefore('#')
    return path.substringAfterLast('.', "")
        .lowercase()
        .takeIf { it in MangaLibImageExtensions }
}

internal fun String.mangaLibApiPathSegment(): String =
    URLEncoder.encode(trim(), Charsets.UTF_8.name()).replace("+", "%20")

internal fun JSONObject.mangaLibDataObject(): JSONObject? = optJSONObject("data")

internal fun JSONObject.mangaLibDataArray(): JSONArray? = optJSONArray("data")

internal fun JSONObject.mangaLibTitle(): String = firstNonBlank(
    optString("rus_name"),
    optString("name"),
    optString("eng_name"),
    optString("title"),
    optString("slug")
).cleanWhitespace()

internal fun JSONObject.mangaLibCoverUrl(): String? = optJSONObject("cover")
    ?.let { cover ->
        firstNonBlank(
            cover.optString("default"),
            cover.optString("md"),
            cover.optString("thumbnail")
        )
    }
    ?.takeIf(::isSafeMangaLibUrl)

internal fun String.mangaLibInstantMillisOrNull(): Long? = runCatching {
    Instant.parse(this).toEpochMilli()
}.getOrNull()

internal fun isSafeMangaLibUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme.equals("https", ignoreCase = true) && ownsMangaLibUrl(url)
}.getOrDefault(false)

internal fun isMangaLibGuardResponse(body: String): Boolean {
    val lower = body.lowercase()
    return lower.contains("ddos-guard") ||
        lower.contains("для доступа к сайту необходимо включить javascript") ||
        lower.contains("/_ddg") ||
        lower.contains("__ddg")
}

private val MangaLibImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")

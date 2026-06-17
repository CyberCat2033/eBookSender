package com.cybercat.pocketbooksender.data.manga

import java.net.URI
import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Element

internal fun ownsComxUrl(url: String): Boolean = runCatching {
    URI(url).host.orEmpty().removePrefix("www.") == "com-x.life"
}.getOrDefault(false)

internal fun comxImageExtensionFromUrl(url: String): String? {
    val path = url.substringBefore('?').substringBefore('#')
    return path.substringAfterLast('.', "")
        .lowercase()
        .takeIf { it in ComxImageExtensions }
}

internal fun Element.absImageUrl(): String = firstNonBlank(
    absUrl("data-src"),
    absUrl("data-original"),
    absUrl("data-lazy-src"),
    absUrl("src")
)

internal fun String.isLikelySeriesUrl(): Boolean {
    val lower = lowercase()
    return lower.startsWith(ComxMangaAdapter.HomeUrl) &&
        lower.substringBefore('?').endsWith(".html") &&
        !lower.contains("/user/") &&
        !lower.contains("/index.php") &&
        !lower.contains("/reader/")
}

internal fun String.isLikelySeriesTitle(): Boolean {
    val value = cleanWhitespace()
    if (value.length !in 2..160) return false
    if (value in ComxIgnoredTitles) return false
    if (value.matches(Regex("""^\d+([.,]\d+)?$"""))) return false
    return value.any { it.isLetter() }
}

internal fun String.extractChapterNumber(): Double? = ComxNumberRegex.findAll(this)
    .lastOrNull()
    ?.value
    ?.replace(',', '.')
    ?.toDoubleOrNull()

internal fun String.extractNewsId(): Long? = Regex("""/(\d+)-""").find(this)
    ?.groupValues
    ?.getOrNull(1)
    ?.toLongOrNull()

internal fun String.normalizeUrlKey(): String = runCatching {
    val uri = URI(this)
    URI(
        uri.scheme?.lowercase(),
        uri.authority?.lowercase(),
        uri.path?.trimEnd('/'),
        uri.query,
        null
    ).toString()
}.getOrDefault(this.trim().trimEnd('/'))

internal fun String.cleanPosterTitle(): String = cleanTitle()
    .replace(Regex("""^(Картинка|Image)\s+""", RegexOption.IGNORE_CASE), "")
    .cleanTitle()

internal fun String.cleanTitle(): String = cleanWhitespace()
    .replace(Regex("""^\d+([.,]\d+)?\s+"""), "")
    .trim(' ', '-', '—', '|')

internal fun String.cleanWhitespace(): String = replace('\u00A0', ' ')
    .replace(Regex("""\s+"""), " ")
    .trim()

internal fun String.stripSeriesPrefix(seriesTitle: String): String {
    val trimmed = this.trim()
    val cleanSeries = seriesTitle.replace(Regex("""[«»"'"“”]"""), "")
        .cleanWhitespace()
        .trim()
        .lowercase()
    if (cleanSeries.isEmpty()) return trimmed

    val cleanTitle = trimmed.replace(Regex("""[«»"'"“”]"""), "")
        .cleanWhitespace()
        .trim()
        .lowercase()
    if (cleanTitle.startsWith(cleanSeries)) {
        var seriesIndex = 0
        var titleIndex = 0
        while (seriesIndex < cleanSeries.length && titleIndex < trimmed.length) {
            val titleChar = trimmed[titleIndex].lowercaseChar()
            val seriesChar = cleanSeries[seriesIndex]

            if (titleChar == seriesChar) {
                seriesIndex++
                titleIndex++
            } else if (titleChar in listOf('«', '»', '"', '\'', '“', '”', ' ')) {
                titleIndex++
            } else if (seriesChar == ' ') {
                seriesIndex++
            } else {
                break
            }
        }
        if (seriesIndex >= cleanSeries.length) {
            val remaining = trimmed.substring(titleIndex)
            val stripped = remaining.trimStart {
                it == ' ' || it == '-' || it == '—' || it == '.' ||
                    it == ':' || it == '_' || it == '/' || it == '\\'
            }
            return stripped.ifBlank { trimmed }
        }
    }
    return trimmed
}

internal fun String.resolveAgainst(baseUrl: String): String {
    val raw = replace("\\/", "/").trim()
    if (raw.isBlank()) return ""
    return runCatching { URI(baseUrl).resolve(raw).toString() }.getOrDefault(raw)
}

internal fun JSONObject.firstString(vararg keys: String): String {
    keys.forEach { key ->
        when (val value = opt(key)) {
            is String -> if (value.isNotBlank()) return value.cleanWhitespace()

            is JSONObject -> value.firstString("url", "src", "href")
                .takeIf { it.isNotBlank() }
                ?.let { return it }

            is JSONArray -> value.optString(0)
                .takeIf { it.isNotBlank() }
                ?.let { return it.cleanWhitespace() }
        }
    }
    return ""
}

internal fun JSONObject.optFiniteDouble(key: String): Double? {
    val value = optDouble(key, Double.NaN)
    return value.takeIf { !it.isNaN() && it.isFinite() }
}

internal fun titleFromUrl(url: String): String =
    URLDecoder.decode(url.substringBefore('?').substringAfterLast('/'), Charsets.UTF_8.name())
        .removeSuffix(".html")
        .replace('-', ' ')
        .cleanTitle()

internal fun firstNonBlank(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() }.orEmpty().trim()

internal val ComxImageUrlRegex: Regex =
    Regex("""https?:\\?/\\?/[^\s"'<>]+?\.(?:jpe?g|png|webp|gif)(?:\?[^\s"'<>]*)?""")

internal val ComxIgnoredImageMarkers = listOf(
    "avatar",
    "favicon",
    "logo",
    "sprite",
    "/templates/",
    "/static/",
    "/emoji",
    "smiles",
    "rating"
)

private val ComxImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")

private val ComxIgnoredTitles = setOf(
    "Com-X .life",
    "Войти",
    "Регистрация",
    "Каталог",
    "Манга",
    "Комиксы",
    "Загрузить еще",
    "Открыть FAQ"
)

private val ComxNumberRegex = Regex("""\d+([.,]\d+)?""")

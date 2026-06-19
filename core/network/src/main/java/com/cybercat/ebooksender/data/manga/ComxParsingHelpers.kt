package com.cybercat.ebooksender.data.manga

import java.net.URI
import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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
    return lower.startsWith(ComxMangaAdapter.HOME_URL) &&
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

internal fun extractComxWindowData(html: String): JSONObject? =
    extractComxWindowData(Jsoup.parse(html))

internal fun extractComxWindowData(document: Document): JSONObject? = document.scriptSources()
    .mapNotNull { script -> script.extractAssignedJsonObject(COMX_WINDOW_DATA_PROPERTY) }
    .firstOrNull()

internal fun extractComxScriptJsonObjects(document: Document): List<JSONObject> =
    document.scriptSources()
        .filter { script -> script.containsLikelyComxReaderData() }
        .flatMap { script -> script.extractJsonValues() }
        .flatMap { value -> value.flattenJsonObjects() }
        .toList()

internal fun titleFromUrl(url: String): String =
    URLDecoder.decode(url.substringBefore('?').substringAfterLast('/'), Charsets.UTF_8.name())
        .removeSuffix(".html")
        .replace('-', ' ')
        .cleanTitle()

internal fun firstNonBlank(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() }.orEmpty().trim()

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

private const val COMX_WINDOW_DATA_PROPERTY = "__DATA__"

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

private fun Document.scriptSources(): Sequence<String> = select("script").asSequence()
    .map { script -> script.data().ifBlank { script.html() } }
    .filter { script -> script.isNotBlank() }

private fun String.extractAssignedJsonObject(target: String): JSONObject? {
    var searchStart = 0
    while (searchStart < length) {
        val assignmentIndex = indexOfAssignmentOutsideJavaScript(searchStart) ?: return null
        val leftHandSide = assignmentLeftHandSide(assignmentIndex)
        if (leftHandSide.referencesAssignmentTarget(target)) {
            parseJsonObjectAfterAssignment(assignmentIndex)?.let { return it }
        }
        searchStart = assignmentIndex + 1
    }
    return null
}

private fun String.parseJsonObjectAfterAssignment(assignmentIndex: Int): JSONObject? {
    val start = indexOfAssignedObjectStart(assignmentIndex + 1) ?: return null
    val end = findBalancedJsonValueEnd(start) ?: return null
    return parseJsonValue(substring(start, end + 1)) as? JSONObject
}

private fun String.extractJsonValues(): Sequence<Any> = sequence {
    var searchStart = 0
    while (searchStart < length) {
        val start = indexOfJsonValueStartOutsideJavaScript(searchStart) ?: break
        val end = findBalancedJsonValueEnd(start)
        if (end == null) {
            searchStart = start + 1
            continue
        }

        parseJsonValue(substring(start, end + 1))?.let { value ->
            yield(value)
            searchStart = end + 1
        } ?: run {
            searchStart = start + 1
        }
    }
}

private fun parseJsonValue(rawValue: String): Any? {
    val trimmed = rawValue.trim()
    if (trimmed.isBlank()) return null
    return runCatching {
        if (trimmed.startsWith("{")) {
            JSONObject(trimmed)
        } else {
            JSONArray(trimmed)
        }
    }.getOrNull()
}

private fun Any.flattenJsonObjects(): Sequence<JSONObject> = sequence {
    when (this@flattenJsonObjects) {
        is JSONObject -> {
            yield(this@flattenJsonObjects)
            keys().asSequence().forEach { key ->
                opt(key)?.let { value -> yieldAll(value.flattenJsonObjects()) }
            }
        }

        is JSONArray -> {
            for (index in 0 until length()) {
                opt(index)?.let { value -> yieldAll(value.flattenJsonObjects()) }
            }
        }
    }
}

private fun String.containsLikelyComxReaderData(): Boolean {
    val lower = lowercase()
    return lower.contains("images") ||
        lower.contains("comix/") ||
        ComxImageExtensions.any { extension -> lower.contains(".$extension") }
}

private fun String.indexOfAssignedObjectStart(startIndex: Int): Int? {
    var index = startIndex.coerceAtLeast(0)
    while (index < length) {
        when {
            this[index].isWhitespace() -> index += 1

            startsWith("//", index) -> {
                index = indexOf('\n', index + 2).takeIf { it >= 0 } ?: return null
            }

            startsWith("/*", index) -> {
                index = indexOf("*/", index + 2).takeIf { it >= 0 }?.plus(2) ?: return null
            }

            this[index] == '{' -> return index

            else -> return null
        }
    }
    return null
}

private fun String.indexOfAssignmentOutsideJavaScript(startIndex: Int): Int? =
    scanOutsideJavaScript(startIndex) { index, char ->
        char == '=' && isPlainAssignmentAt(index)
    }

private fun String.indexOfJsonValueStartOutsideJavaScript(startIndex: Int): Int? =
    scanOutsideJavaScript(startIndex) { _, char -> char == '{' || char == '[' }

private inline fun String.scanOutsideJavaScript(
    startIndex: Int,
    predicate: (index: Int, char: Char) -> Boolean
): Int? {
    var index = startIndex.coerceAtLeast(0)
    while (index < length) {
        val char = this[index]
        when {
            char == '"' || char == '\'' || char == '`' -> index = skipQuotedJavaScript(index, char)
            startsWith("//", index) -> index = skipLineComment(index)
            startsWith("/*", index) -> index = skipBlockComment(index)
            predicate(index, char) -> return index
            else -> index += 1
        }
    }
    return null
}

private fun String.skipQuotedJavaScript(startIndex: Int, quote: Char): Int {
    var index = startIndex + 1
    var escaped = false
    while (index < length) {
        val char = this[index]
        when {
            escaped -> escaped = false
            char == '\\' -> escaped = true
            char == quote -> return index + 1
        }
        index += 1
    }
    return length
}

private fun String.skipLineComment(startIndex: Int): Int =
    indexOf('\n', startIndex + 2).takeIf { it >= 0 } ?: length

private fun String.skipBlockComment(startIndex: Int): Int =
    indexOf("*/", startIndex + 2).takeIf { it >= 0 }?.plus(2) ?: length

private fun String.isPlainAssignmentAt(index: Int): Boolean {
    val previous = getOrNull(index - 1)
    val next = getOrNull(index + 1)
    return previous !in AssignmentOperatorPrefixes &&
        next != '=' &&
        next != '>'
}

private fun String.assignmentLeftHandSide(assignmentIndex: Int): String {
    var start = assignmentIndex - 1
    while (start >= 0 && this[start] != ';') {
        start -= 1
    }
    return substring(start + 1, assignmentIndex)
}

private fun String.referencesAssignmentTarget(target: String): Boolean {
    val compact = filterNot { it.isWhitespace() }
    return compact == target ||
        compact.endsWith(".$target") ||
        compact.endsWith("[\"$target\"]") ||
        compact.endsWith("['$target']") ||
        compact.endsWith("var$target") ||
        compact.endsWith("let$target") ||
        compact.endsWith("const$target")
}

private fun String.findBalancedJsonValueEnd(startIndex: Int): Int? {
    val stack = ArrayDeque<Char>()
    var index = startIndex
    while (index < length) {
        when (val char = this[index]) {
            '"', '\'' -> index = skipQuotedJavaScript(index, char)

            '{', '[' -> {
                stack.addLast(char)
                index += 1
            }

            '}' -> {
                if (stack.removeLastOrNull() != '{') return null
                if (stack.isEmpty()) return index
                index += 1
            }

            ']' -> {
                if (stack.removeLastOrNull() != '[') return null
                if (stack.isEmpty()) return index
                index += 1
            }

            else -> index += 1
        }
    }
    return null
}

private val AssignmentOperatorPrefixes =
    setOf('=', '!', '<', '>', '+', '-', '*', '/', '%', '&', '|', '^')

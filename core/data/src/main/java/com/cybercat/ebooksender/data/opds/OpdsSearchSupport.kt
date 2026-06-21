package com.cybercat.ebooksender.data.opds

import com.cybercat.ebooksender.util.SearchQueryNormalizer
import com.cybercat.ebooksender.util.UrlHostMatcher
import java.net.URI
import java.net.URLEncoder

internal fun List<OpdsLink>.rankOpdsSearchLinks(): List<OpdsLink> = sortedWith(
    compareBy<OpdsLink> { link ->
        when {
            link.type.orEmpty().contains("opensearchdescription", ignoreCase = true) -> 0
            link.href.contains(OPDS_SEARCH_TERMS_PLACEHOLDER, ignoreCase = true) -> 1
            else -> 2
        }
    }.thenBy { link -> link.href }
)

internal fun buildOpdsSearchUrls(bookSearchUrl: String, query: String): List<String> =
    (listOf(bookSearchUrl) + bookSearchUrl.flibustaAuthorSearchUrls(query)).distinct()

internal fun resolveOpdsTemplateUrl(baseUrl: String, href: String): String {
    val escapedHref = href
        .replace("{", "%7B")
        .replace("}", "%7D")
    return OpdsUrlResolver.resolveUrl(baseUrl, escapedHref)
        .replace("%7B", "{")
        .replace("%7D", "}")
}

internal fun normalizeOpdsSearchTemplateOrigin(sourceBaseUrl: String, templateUrl: String): String {
    val sourceUri = runCatching { URI(sourceBaseUrl) }.getOrNull() ?: return templateUrl
    val templateUri = runCatching { URI(templateUrl.withEscapedTemplateBraces()) }
        .getOrNull() ?: return templateUrl
    if (!sourceUri.scheme.equals("https", ignoreCase = true) ||
        !templateUri.scheme.equals("http", ignoreCase = true) ||
        sourceUri.rawAuthority.isNullOrBlank() ||
        templateUri.rawAuthority.isNullOrBlank()
    ) {
        return templateUrl
    }

    val sourceHost = sourceUri.host.orEmpty()
    val templateHost = templateUri.host.orEmpty()
    val hostsAreCompatible = sourceHost.equals(templateHost, ignoreCase = true) ||
        listOf(sourceHost, templateHost)
            .all { host -> FLIBUSTA_HOST_MARKERS.any { marker -> marker in host.lowercase() } }
    if (!hostsAreCompatible) return templateUrl

    return templateUri.withSchemeAndAuthority(
        scheme = sourceUri.scheme,
        authority = sourceUri.rawAuthority
    ).withUnescapedTemplateBraces()
}

internal fun mergeOpdsSearchCatalogs(title: String, catalogs: List<OpdsCatalog>): OpdsCatalog {
    if (catalogs.size == 1) return catalogs.first()

    return OpdsCatalog(
        title = title,
        entries = catalogs.flatMap { catalog -> catalog.entries },
        links = catalogs
            .flatMap { catalog -> catalog.links }
            .distinctBy { link ->
                listOf(link.href, link.rel.orEmpty(), link.type.orEmpty()).joinToString("|")
            }
    )
}

internal fun OpdsCatalog.filterAuthorSearchEntries(query: String): OpdsCatalog {
    val tokens = SearchQueryNormalizer.tokens(query)
    if (tokens.size < 2) return this

    val filteredEntries = entries.filter { entry ->
        val title = SearchQueryNormalizer.comparableText(entry.title)
        tokens.all { token -> token in title }
    }

    return if (filteredEntries.isEmpty()) {
        this
    } else {
        copy(entries = filteredEntries)
    }
}

internal fun expandOpdsSearchTemplate(template: String, query: String): String {
    val encodedQuery = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
    return template
        .replace(Regex("\\{searchTerms\\??\\}"), encodedQuery)
        .replace(Regex("[?&][^?&=]+=\\{[^}]+\\}"), "")
        .replace(Regex("\\{[^}]+\\}"), "")
        .replace("?&", "?")
        .trimEnd('?', '&')
}

private fun String.flibustaAuthorSearchUrls(query: String): List<String> {
    val uri = runCatching { URI(this) }.getOrNull() ?: return emptyList()
    if (!UrlHostMatcher.hostContainsAny(this, FLIBUSTA_HOST_MARKERS)) return emptyList()

    val normalizedQuery = SearchQueryNormalizer.normalize(query)
    if (normalizedQuery.isBlank()) return emptyList()

    val authorPrefix = normalizedQuery
        .split(' ')
        .lastOrNull { token -> token.length >= 2 }
        ?: return emptyList()

    val baseUrl = "${uri.scheme}://${uri.rawAuthority}"
    return listOf("$baseUrl/opds/authorsindex/${authorPrefix.urlPathEncode()}")
}

private fun String.urlPathEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
    .replace("+", "%20")

private fun String.withEscapedTemplateBraces(): String = replace("{", "%7B").replace("}", "%7D")

private fun String.withUnescapedTemplateBraces(): String = replace("%7B", "{").replace("%7D", "}")

private fun URI.withSchemeAndAuthority(scheme: String, authority: String): String = buildString {
    append(scheme)
    append("://")
    append(authority)
    append(rawPath.orEmpty().ifBlank { "/" })
    if (!rawQuery.isNullOrBlank()) {
        append('?')
        append(rawQuery)
    }
    if (!rawFragment.isNullOrBlank()) {
        append('#')
        append(rawFragment)
    }
}

internal const val OPDS_SEARCH_TERMS_PLACEHOLDER = "{searchTerms"

private val FLIBUSTA_HOST_MARKERS = listOf("flibusta", "flub")

package com.cybercat.ebooksender.data.opds

import com.cybercat.ebooksender.util.SearchQueryNormalizer
import com.cybercat.ebooksender.util.UrlHostMatcher
import java.net.URI
import java.net.URLEncoder

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

private val FLIBUSTA_HOST_MARKERS = listOf("flibusta", "flub")

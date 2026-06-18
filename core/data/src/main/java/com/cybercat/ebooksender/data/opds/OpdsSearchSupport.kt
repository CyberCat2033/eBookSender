package com.cybercat.ebooksender.data.opds

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
    val tokens = query.searchTokens()
    if (tokens.size < 2) return this

    val filteredEntries = entries.filter { entry ->
        val title = entry.title.searchComparableText()
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
    val host = uri.host.orEmpty().lowercase()
    if ("flibusta" !in host && "flub" !in host) return emptyList()

    val normalizedQuery = query.cleanAuthorSearchQuery()
    if (normalizedQuery.isBlank()) return emptyList()

    val authorPrefix = normalizedQuery
        .split(' ')
        .lastOrNull { token -> token.length >= 2 }
        ?: return emptyList()

    val baseUrl = "${uri.scheme}://${uri.rawAuthority}"
    return listOf("$baseUrl/opds/authorsindex/${authorPrefix.urlPathEncode()}")
}

private fun String.cleanAuthorSearchQuery(): String = trim()
    .replace(Regex("[^\\p{L}\\p{N}\\s-]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
    .lowercase()

private fun String.searchTokens(): List<String> = trim()
    .replace(Regex("[^\\p{L}\\p{N}\\s-]+"), " ")
    .replace(Regex("\\s+"), " ")
    .lowercase()
    .split(' ')
    .map { token -> token.trim('-', ' ') }
    .filter { token -> token.length >= 2 }

private fun String.searchComparableText(): String = searchTokens().joinToString(" ")

private fun String.urlPathEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
    .replace("+", "%20")

package com.cybercat.pocketbooksender.data.opds

import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchOpdsCatalogUseCase @Inject constructor(
    private val opdsRepository: OpdsRepository,
    private val parser: OpdsParser,
    private val httpClient: OpdsHttpClient
) {
    suspend operator fun invoke(
        baseUrl: String,
        searchLink: OpdsLink,
        query: String,
        mergedCatalogTitle: String
    ): Result<SearchOpdsCatalogResult> {
        return try {
            val catalogs = buildSearchUrls(baseUrl, searchLink, query)
                .mapNotNull { searchUrl -> loadSearchCatalog(searchUrl, query) }

            if (catalogs.isEmpty()) {
                return Result.failure(OpdsSearchCatalogUnavailableException())
            }

            Result.success(
                SearchOpdsCatalogResult(
                    currentUrl = catalogs.first().url,
                    catalog = mergeSearchCatalogs(
                        title = mergedCatalogTitle,
                        catalogs = catalogs.map(SearchOpdsCatalog::catalog)
                    )
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private suspend fun buildSearchUrls(
        baseUrl: String,
        searchLink: OpdsLink,
        query: String
    ): List<String> {
        val bookSearchUrl = buildSearchUrl(baseUrl, searchLink, query)
        return (listOf(bookSearchUrl) + bookSearchUrl.flibustaAuthorSearchUrls(query)).distinct()
    }

    private suspend fun buildSearchUrl(
        baseUrl: String,
        searchLink: OpdsLink,
        query: String
    ): String = withContext(Dispatchers.IO) {
        val resolvedLink = resolveTemplateUrl(baseUrl, searchLink.href)
        val template = when {
            searchLink.href.contains(SEARCH_TERMS_PLACEHOLDER) -> resolvedLink

            searchLink.type.orEmpty().contains("opensearchdescription", ignoreCase = true) -> {
                loadOpenSearchTemplate(resolvedLink)
            }

            else -> {
                resolvedLink
            }
        }

        expandSearchTemplate(template, query)
    }

    private suspend fun loadSearchCatalog(searchUrl: String, query: String): SearchOpdsCatalog? {
        return try {
            val catalog = opdsRepository.loadCatalog(searchUrl)
            if (!searchUrl.contains("/opds/authorsindex/", ignoreCase = true)) {
                return SearchOpdsCatalog(searchUrl, catalog)
            }

            val authorLink = catalog.entries
                .singleOrNull()
                ?.navigation
                ?.firstOrNull { link -> link.href.contains("/opds/authors/", ignoreCase = true) }

            if (authorLink == null) {
                return SearchOpdsCatalog(searchUrl, catalog.filterAuthorEntries(query))
            }

            SearchOpdsCatalog(
                url = authorLink.href,
                catalog = opdsRepository.loadCatalog(authorLink.href).filterAuthorEntries(query)
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: OpdsAuthenticationRequiredException) {
            throw error
        } catch (error: Throwable) {
            null
        }
    }

    private suspend fun loadOpenSearchTemplate(url: String): String {
        val connection = httpClient.openConnection(
            url = url,
            accept = OPENSEARCH_DESCRIPTION_ACCEPT
        )
        try {
            val template = connection.inputStream.use { input ->
                parser.parseOpenSearch(input).bestTemplate
            }

            if (template.isNullOrBlank()) {
                throw IOException("OpenSearch template was not found")
            }

            return resolveTemplateUrl(url, template)
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveTemplateUrl(baseUrl: String, href: String): String {
        val escapedHref = href
            .replace("{", "%7B")
            .replace("}", "%7D")
        return OpdsUrlResolver.resolveUrl(baseUrl, escapedHref)
            .replace("%7B", "{")
            .replace("%7D", "}")
    }

    private fun mergeSearchCatalogs(title: String, catalogs: List<OpdsCatalog>): OpdsCatalog {
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

    private fun OpdsCatalog.filterAuthorEntries(query: String): OpdsCatalog {
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

    private fun String.searchTokens(): List<String> = trim()
        .replace(Regex("[^\\p{L}\\p{N}\\s-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .lowercase()
        .split(' ')
        .map { token -> token.trim('-', ' ') }
        .filter { token -> token.length >= 2 }

    private fun String.searchComparableText(): String = searchTokens().joinToString(" ")

    private companion object {
        const val SEARCH_TERMS_PLACEHOLDER = "{searchTerms"
        const val OPENSEARCH_DESCRIPTION_ACCEPT =
            "application/opensearchdescription+xml, application/xml, text/xml, */*"
    }
}

data class SearchOpdsCatalogResult(val currentUrl: String, val catalog: OpdsCatalog)

class OpdsSearchCatalogUnavailableException : Exception()

private data class SearchOpdsCatalog(val url: String, val catalog: OpdsCatalog)

private fun expandSearchTemplate(template: String, query: String): String {
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

private fun String.urlPathEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
    .replace("+", "%20")

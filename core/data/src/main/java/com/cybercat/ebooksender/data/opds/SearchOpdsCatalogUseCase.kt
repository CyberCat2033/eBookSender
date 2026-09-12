package com.cybercat.ebooksender.data.opds

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SearchOpdsCatalogUseCase internal constructor(
    private val loadCatalog: suspend (String) -> OpdsCatalog,
    private val loadSearchTemplate: suspend (String) -> String,
    private val searchTimeoutMillis: Long = SEARCH_TIMEOUT_MILLIS
) {
    @Inject
    constructor(opdsRepository: OpdsRepository) : this(
        opdsRepository::loadCatalog,
        opdsRepository::loadSearchTemplate
    )

    suspend operator fun invoke(
        baseUrl: String,
        searchLinks: List<OpdsLink>,
        query: String,
        mergedCatalogTitle: String,
        onPartialResult: (SearchOpdsCatalogResult) -> Unit = {}
    ): Result<SearchOpdsCatalogResult> {
        var latestResult: SearchOpdsCatalogResult? = null
        var completed = false
        return try {
            val result = withTimeoutOrNull(searchTimeoutMillis) {
                val found = searchLinks.rankOpdsSearchLinks().firstNotNullOfOrNull { searchLink ->
                    val urls = try {
                        buildSearchUrls(baseUrl, searchLink, query)
                    } catch (error: Exception) {
                        error.rethrowIfSearchInterrupted()
                        return@firstNotNullOfOrNull null
                    }
                    val catalogs = arrayOfNulls<SearchOpdsCatalog>(urls.size)
                    channelFlow {
                        urls.forEachIndexed { index, url ->
                            launch { send(index to loadSearchCatalog(url, query)) }
                        }
                    }.collect { (index, catalog) ->
                        catalogs[index] = catalog
                        catalogs.filterNotNull().takeIf { it.isNotEmpty() }?.let { loaded ->
                            val partial = loaded.toSearchResult(
                                mergedCatalogTitle,
                                isPartial = true
                            )
                            latestResult = partial
                            if (partial.catalog.entries.isNotEmpty()) {
                                onPartialResult(partial)
                            }
                        }
                    }
                    catalogs.filterNotNull().takeIf { it.isNotEmpty() }?.toSearchResult(
                        title = mergedCatalogTitle,
                        isPartial = catalogs.any { it == null }
                    )
                }
                completed = true
                found
            } ?: latestResult ?: return Result.failure(
                if (completed) {
                    OpdsSearchCatalogUnavailableException()
                } else {
                    OpdsSearchTimeoutException()
                }
            )
            Result.success(result)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun buildSearchUrls(
        baseUrl: String,
        searchLink: OpdsLink,
        query: String
    ): List<String> {
        val resolvedLink = resolveOpdsTemplateUrl(baseUrl, searchLink.href)
        val template = if (
            !searchLink.href.contains(OPDS_SEARCH_TERMS_PLACEHOLDER) &&
            searchLink.type.orEmpty().contains("opensearchdescription", ignoreCase = true)
        ) {
            normalizeOpdsSearchTemplateOrigin(
                sourceBaseUrl = baseUrl,
                templateUrl = resolveOpdsTemplateUrl(resolvedLink, loadSearchTemplate(resolvedLink))
            )
        } else {
            resolvedLink
        }
        return buildOpdsSearchUrls(expandOpdsSearchTemplate(template, query), query)
    }

    private suspend fun loadSearchCatalog(searchUrl: String, query: String): SearchOpdsCatalog? {
        return try {
            val catalog = loadCatalog(searchUrl)
            if (!searchUrl.contains("/opds/authorsindex/", ignoreCase = true)) {
                return SearchOpdsCatalog(searchUrl, catalog)
            }

            val authorLink = catalog.entries
                .singleOrNull()
                ?.navigation
                ?.firstOrNull { link -> link.href.contains("/opds/authors/", ignoreCase = true) }

            if (authorLink == null) {
                return SearchOpdsCatalog(searchUrl, catalog.filterAuthorSearchEntries(query))
            }

            SearchOpdsCatalog(
                url = authorLink.href,
                catalog = loadCatalog(authorLink.href).filterAuthorSearchEntries(query)
            )
        } catch (error: Exception) {
            error.rethrowIfSearchInterrupted()
            null
        }
    }

    private fun Exception.rethrowIfSearchInterrupted() {
        if (this is CancellationException || this is OpdsAuthenticationRequiredException) {
            throw this
        }
    }

    private fun List<SearchOpdsCatalog>.toSearchResult(title: String, isPartial: Boolean) =
        SearchOpdsCatalogResult(
            currentUrl = first().url,
            catalog = mergeOpdsSearchCatalogs(title, map(SearchOpdsCatalog::catalog)),
            isPartial = isPartial
        )

    private companion object {
        const val SEARCH_TIMEOUT_MILLIS = 30_000L
    }
}

data class SearchOpdsCatalogResult(
    val currentUrl: String,
    val catalog: OpdsCatalog,
    val isPartial: Boolean = false
)

class OpdsSearchCatalogUnavailableException : Exception()

class OpdsSearchTimeoutException : IOException("OPDS search timed out")

private data class SearchOpdsCatalog(val url: String, val catalog: OpdsCatalog)

class OpenSearchTemplateNotFoundException : IOException("OpenSearch template was not found")

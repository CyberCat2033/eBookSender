package com.cybercat.pocketbooksender.data.opds

import java.io.IOException
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
                    catalog = mergeOpdsSearchCatalogs(
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
        return buildOpdsSearchUrls(bookSearchUrl, query)
    }

    private suspend fun buildSearchUrl(
        baseUrl: String,
        searchLink: OpdsLink,
        query: String
    ): String = withContext(Dispatchers.IO) {
        val resolvedLink = resolveOpdsTemplateUrl(baseUrl, searchLink.href)
        val template = when {
            searchLink.href.contains(SEARCH_TERMS_PLACEHOLDER) -> resolvedLink

            searchLink.type.orEmpty().contains("opensearchdescription", ignoreCase = true) -> {
                loadOpenSearchTemplate(resolvedLink)
            }

            else -> {
                resolvedLink
            }
        }

        expandOpdsSearchTemplate(template, query)
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
                return SearchOpdsCatalog(searchUrl, catalog.filterAuthorSearchEntries(query))
            }

            SearchOpdsCatalog(
                url = authorLink.href,
                catalog = opdsRepository.loadCatalog(authorLink.href)
                    .filterAuthorSearchEntries(query)
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

            return resolveOpdsTemplateUrl(url, template)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val SEARCH_TERMS_PLACEHOLDER = "{searchTerms"
        const val OPENSEARCH_DESCRIPTION_ACCEPT =
            "application/opensearchdescription+xml, application/xml, text/xml, */*"
    }
}

data class SearchOpdsCatalogResult(val currentUrl: String, val catalog: OpdsCatalog)

class OpdsSearchCatalogUnavailableException : Exception()

private data class SearchOpdsCatalog(val url: String, val catalog: OpdsCatalog)

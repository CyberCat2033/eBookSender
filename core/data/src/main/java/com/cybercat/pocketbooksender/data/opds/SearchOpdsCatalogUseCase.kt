package com.cybercat.pocketbooksender.data.opds

import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class SearchOpdsCatalogUseCase @Inject constructor(private val opdsRepository: OpdsRepository) {
    suspend operator fun invoke(
        baseUrl: String,
        searchLink: OpdsLink,
        query: String,
        mergedCatalogTitle: String
    ): Result<SearchOpdsCatalogResult> {
        return try {
            val catalogs = opdsRepository.buildSearchUrls(baseUrl, searchLink, query)
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
}

data class SearchOpdsCatalogResult(val currentUrl: String, val catalog: OpdsCatalog)

class OpdsSearchCatalogUnavailableException : Exception()

private data class SearchOpdsCatalog(val url: String, val catalog: OpdsCatalog)

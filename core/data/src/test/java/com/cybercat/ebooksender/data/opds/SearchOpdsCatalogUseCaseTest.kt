package com.cybercat.ebooksender.data.opds

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchOpdsCatalogUseCaseTest {
    @Test
    fun showsBooksWhileAuthorSearchIsStillRunning() = runBlocking {
        withTimeout(5_000) {
            val authorsStarted = CompletableDeferred<Unit>()
            val releaseAuthors = CompletableDeferred<Unit>()
            val firstResult = CompletableDeferred<SearchOpdsCatalogResult>()
            val useCase = useCase { url ->
                if (url.contains("authorsindex")) {
                    authorsStarted.complete(Unit)
                    releaseAuthors.await()
                    catalog("Author")
                } else {
                    authorsStarted.await()
                    catalog("Book")
                }
            }
            val search = async { search(useCase) { firstResult.complete(it) } }

            assertEquals(listOf("Book"), firstResult.await().titles())
            assertFalse(search.isCompleted)
            releaseAuthors.complete(Unit)
            val result = search.await().getOrThrow()
            assertEquals(listOf("Book", "Author"), result.titles())
            assertFalse(result.isPartial)
        }
    }

    @Test
    fun fasterAuthorsDoNotChangeFinalBookPaginationOrResultOrder() = runBlocking {
        withTimeout(5_000) {
            val releaseBooks = CompletableDeferred<Unit>()
            val authorsShown = CompletableDeferred<Unit>()
            val useCase = useCase { url ->
                if (url.contains("authorsindex")) {
                    catalog("Author")
                } else {
                    releaseBooks.await()
                    catalog("Book").copy(links = listOf(nextPage))
                }
            }
            val search = async {
                search(useCase) {
                    if (it.titles() == listOf("Author")) authorsShown.complete(Unit)
                }
            }
            authorsShown.await()
            releaseBooks.complete(Unit)

            val result = search.await().getOrThrow()
            assertEquals(listOf("Book", "Author"), result.titles())
            assertEquals(BOOK_URL, result.currentUrl)
            assertEquals(listOf(nextPage), result.catalog.links)
        }
    }

    @Test
    fun authorFailurePreservesBooksAndMarksResultsIncomplete() = runBlocking {
        val result = search(
            useCase { url ->
                if (url.contains("authorsindex")) throw IOException("Unavailable")
                catalog("Book")
            }
        ).getOrThrow()

        assertEquals(listOf("Book"), result.titles())
        assertTrue(result.isPartial)
    }

    @Test
    fun searchDeadlineKeepsAvailableResultsAndCancelsSlowBranch() = runBlocking {
        withTimeout(5_000) {
            val canceled = CompletableDeferred<Unit>()
            val useCase = SearchOpdsCatalogUseCase(
                loadCatalog = { url ->
                    if (url.contains("authorsindex")) {
                        try {
                            awaitCancellation()
                        } finally {
                            canceled.complete(Unit)
                        }
                    } else {
                        catalog("Book")
                    }
                },
                loadSearchTemplate = { error("Unused") },
                searchTimeoutMillis = 200
            )
            val result = search(useCase).getOrThrow()

            assertTrue(result.isPartial)
            assertEquals(listOf("Book"), result.titles())
            canceled.await()
        }
    }

    @Test
    fun searchDeadlineIncludesOpenSearchDescriptionLoading() = runBlocking {
        val useCase = SearchOpdsCatalogUseCase(
            loadCatalog = { error("Template is not ready") },
            loadSearchTemplate = { awaitCancellation() },
            searchTimeoutMillis = 100
        )
        val result = useCase(BASE_URL, listOf(descriptionLink), "Author", "Results")

        assertTrue(result.exceptionOrNull() is OpdsSearchTimeoutException)
    }

    @Test
    fun cancellationStopsBothSearchesWithoutPublishingFinalResults() = runBlocking {
        withTimeout(5_000) {
            val started = List(2) { CompletableDeferred<Unit>() }
            val canceled = List(2) { CompletableDeferred<Unit>() }
            val useCase = useCase { url ->
                val index = if (url.contains("authorsindex")) 1 else 0
                started[index].complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    canceled[index].complete(Unit)
                }
            }
            val search = async { search(useCase) }
            started.forEach { it.await() }
            search.cancelAndJoin()

            assertTrue(search.isCancelled)
            canceled.forEach { it.await() }
        }
    }

    @Test
    fun authenticationFailureIsNotHiddenByPartialSuccess() = runBlocking {
        val result = search(
            useCase { url ->
                if (url.contains("authorsindex")) throw OpdsAuthenticationRequiredException(url)
                catalog("Book")
            }
        )

        assertTrue(result.exceptionOrNull() is OpdsAuthenticationRequiredException)
    }

    @Test
    fun failedDescriptionFallsBackToDirectSearchLink() = runBlocking {
        val useCase = SearchOpdsCatalogUseCase(
            loadCatalog = { catalog("Result") },
            loadSearchTemplate = { throw IOException("Unavailable") }
        )
        val result = useCase(
            BASE_URL,
            listOf(descriptionLink, directLink),
            "Author",
            "Results"
        )

        assertTrue(result.isSuccess)
    }

    private fun useCase(loader: suspend (String) -> OpdsCatalog) = SearchOpdsCatalogUseCase(
        loadCatalog = loader,
        loadSearchTemplate = { error("Unused") }
    )

    private suspend fun search(
        useCase: SearchOpdsCatalogUseCase,
        onPartial: (SearchOpdsCatalogResult) -> Unit = {}
    ) = useCase(BASE_URL, listOf(directLink), "Author", "Results", onPartial)

    private fun catalog(title: String) = OpdsCatalog(
        title = title,
        entries = listOf(OpdsEntry(title, title, emptyList(), null, null, emptyList(), emptyList()))
    )

    private fun SearchOpdsCatalogResult.titles() = catalog.entries.map { it.title }

    private companion object {
        const val BASE_URL = "https://flibusta.test/opds"
        const val BOOK_URL = "$BASE_URL/search?query=Author"
        val directLink =
            OpdsLink("$BASE_URL/search?query={searchTerms}", "search", "application/atom+xml", null)
        val descriptionLink =
            OpdsLink(
                "$BASE_URL/search.xml",
                "search",
                "application/opensearchdescription+xml",
                null
            )
        val nextPage = OpdsLink("$BOOK_URL&page=2", "next", "application/atom+xml", null)
    }
}

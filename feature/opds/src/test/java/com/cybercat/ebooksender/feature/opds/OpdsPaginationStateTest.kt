package com.cybercat.ebooksender.feature.opds

import com.cybercat.ebooksender.data.opds.OpdsCatalog
import com.cybercat.ebooksender.data.opds.OpdsLink
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpdsPaginationStateTest {
    private val pagedCatalog = OpdsUiState(
        catalog = OpdsCatalog("Results", emptyList()),
        paging = OpdsPagingState(
            nextLink = OpdsLink("https://example.org/page/2", "next", null, null)
        )
    )

    @Test
    fun newSearchHidesPreviousPaginationUntilCompletedResultsHaveAnotherPage() {
        assertTrue(pagedCatalog.shouldShowPagination)
        val searching = pagedCatalog.copy(catalog = null, isLoading = true, isSearching = true)
        assertFalse(searching.shouldShowPagination)
        assertFalse(pagedCatalog.copy(isSearching = true).shouldShowPagination)
        assertFalse(pagedCatalog.copy(paging = OpdsPagingState()).shouldShowPagination)
        assertTrue(pagedCatalog.shouldShowPagination)
    }

    @Test
    fun loadingFailureDoesNotExposeOldPageControlsWithoutACatalog() {
        assertFalse(pagedCatalog.copy(catalog = null).shouldShowPagination)
        assertFalse(pagedCatalog.copy(isLoading = true).shouldShowPagination)
    }

    @Test
    fun lastPageStillAllowsReturningToPreviousPages() {
        val lastPage = pagedCatalog.copy(
            paging = OpdsPagingState(
                currentPage = 2,
                previousPages = listOf(OpdsPageHistoryEntry("First", "https://example.org/page/1"))
            )
        )
        assertTrue(lastPage.shouldShowPagination)
        assertFalse(lastPage.copy(isDownloading = true).shouldShowPagination)
    }
}

package com.cybercat.pocketbooksender.feature.opds

import com.cybercat.pocketbooksender.data.opds.OpdsCatalog
import com.cybercat.pocketbooksender.data.opds.OpdsLink
import com.cybercat.pocketbooksender.data.opds.OpdsSource

data class OpdsUiState(
    val sources: List<OpdsSource> = emptyList(),
    val webMode: WebContentMode = WebContentMode.Opds,
    val urlInput: String = "",
    val titleInput: String = "",
    val searchInput: String = "",
    val currentUrl: String? = null,
    val catalog: OpdsCatalog? = null,
    val history: List<OpdsHistoryEntry> = emptyList(),
    val paging: OpdsPagingState = OpdsPagingState(),
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: OpdsDownloadUiProgress? = null,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val showAuthDialog: Boolean = false,
    val authDialogSourceId: String? = null,
    val authDialogSourceTitle: String = "",
    val authDialogUsername: String = "",
    val authDialogPassword: String = "",
    val authDialogUrlToRetry: String? = null
) {
    val canGoBack: Boolean = history.isNotEmpty()
}

data class OpdsHistoryEntry(
    val title: String,
    val url: String,
    val paging: OpdsPagingSnapshot = OpdsPagingSnapshot()
)

data class OpdsPageHistoryEntry(val title: String, val url: String)

data class OpdsPagingSnapshot(
    val currentPage: Int = 1,
    val previousPages: List<OpdsPageHistoryEntry> = emptyList(),
    val totalPages: Int? = null
)

data class OpdsPagingState(
    val currentPage: Int = 1,
    val previousPages: List<OpdsPageHistoryEntry> = emptyList(),
    val nextLink: OpdsLink? = null,
    val totalPages: Int? = null
) {
    val canGoPrevious: Boolean = previousPages.isNotEmpty()
    val canGoNext: Boolean = nextLink != null
    val shouldShow: Boolean = currentPage > 1 || canGoNext
}

internal fun OpdsPagingState.toSnapshot(): OpdsPagingSnapshot = OpdsPagingSnapshot(
    currentPage = currentPage,
    previousPages = previousPages,
    totalPages = totalPages
)

internal fun OpdsPagingSnapshot.toPagingState(): OpdsPagingState = OpdsPagingState(
    currentPage = currentPage,
    previousPages = previousPages,
    totalPages = totalPages
)

enum class WebContentMode {
    Opds,
    Manga
}

data class OpdsDownloadUiProgress(
    val completedCount: Int,
    val totalCount: Int,
    val bytesRead: Long = 0L,
    val totalBytes: Long? = null,
    val isCanceling: Boolean = false
) {
    val currentFileProgress: Float?
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { total ->
                (bytesRead.toDouble() / total.toDouble())
                    .coerceIn(0.0, 1.0)
                    .toFloat()
            }

    val currentFilePercent: Int?
        get() = currentFileProgress?.let { progress ->
            (progress * 100).toInt().coerceIn(0, 100)
        }

    val overallProgress: Float?
        get() {
            val safeTotalCount = totalCount.coerceAtLeast(1)
            currentFileProgress?.let { fileProgress ->
                return ((completedCount + fileProgress) / safeTotalCount)
                    .coerceIn(0f, 1f)
            }
            return if (safeTotalCount > 1 && completedCount > 0) {
                (completedCount.toFloat() / safeTotalCount.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
        }
}

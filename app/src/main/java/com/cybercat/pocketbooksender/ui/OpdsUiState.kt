package com.cybercat.pocketbooksender.ui

import com.cybercat.pocketbooksender.data.opds.OpdsCatalog
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
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val showAuthDialog: Boolean = false,
    val authDialogSourceId: String? = null,
    val authDialogSourceTitle: String = "",
    val authDialogUsername: String = "",
    val authDialogPassword: String = "",
    val authDialogUrlToRetry: String? = null,
) {
    val canGoBack: Boolean = history.isNotEmpty()
}

data class OpdsHistoryEntry(
    val title: String,
    val url: String,
)

enum class WebContentMode {
    Opds,
    Manga,
}

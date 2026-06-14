package com.cybercat.pocketbooksender.ui

import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.PocketBookDevice
import com.cybercat.pocketbooksender.model.UploadItem

data class TransferUiState(
    val ftpInput: String = "",
    val isConnecting: Boolean = false,
    val connectedDevice: PocketBookDevice? = null,
    val isTransferActive: Boolean = false,
    val activeTransferItemIds: Set<String> = emptySet(),
    val queue: List<UploadItem> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val errorMessage: String? = null,
    val programmingTags: List<String> = emptyList(),
    val mangaSeriesSuggestions: List<String> = emptyList(),
) {
    val isConnected: Boolean = connectedDevice != null
}

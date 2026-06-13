package com.cybercat.pocketbooksender.ui

import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.DeviceCatalog
import com.cybercat.pocketbooksender.model.PocketBookDevice
import com.cybercat.pocketbooksender.model.UploadItem

data class SenderUiState(
    val ftpInput: String = "",
    val isConnecting: Boolean = false,
    val connectedDevice: PocketBookDevice? = null,
    val isTransferActive: Boolean = false,
    val queue: List<UploadItem> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val deviceCatalog: DeviceCatalog = DeviceCatalog(),
    val opds: OpdsUiState = OpdsUiState(),
    val manga: MangaUiState = MangaUiState(),
    val programmingTags: List<String> = emptyList(),
    val mangaSeriesSuggestions: List<String> = emptyList(),
    val errorMessage: String? = null,
    val settingsStatusMessage: String? = null,
) {
    val isConnected: Boolean = connectedDevice != null
}

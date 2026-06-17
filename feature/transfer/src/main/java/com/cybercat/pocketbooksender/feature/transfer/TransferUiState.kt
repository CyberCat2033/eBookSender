package com.cybercat.pocketbooksender.feature.transfer

import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.RemoteDevice
import com.cybercat.pocketbooksender.model.UploadItem

data class TransferUiState(
    val ftpInput: String = "",
    val isConnecting: Boolean = false,
    val connectedDevice: RemoteDevice? = null,
    val queue: List<UploadItem> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val showVpnBypassDialog: Boolean = false,
    val documentsTags: List<String> = emptyList(),
    val mangaSeriesSuggestions: List<String> = emptyList()
) {
    val isConnected: Boolean = connectedDevice != null
}

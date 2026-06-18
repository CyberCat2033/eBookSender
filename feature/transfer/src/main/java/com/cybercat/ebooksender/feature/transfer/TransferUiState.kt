package com.cybercat.ebooksender.feature.transfer

import com.cybercat.ebooksender.model.AppSettings
import com.cybercat.ebooksender.model.RemoteDevice
import com.cybercat.ebooksender.model.UploadItem

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

package com.cybercat.pocketbooksender.feature.transfer

data class TransferRuntimeUiState(
    val isTransferActive: Boolean = false,
    val activeTransferItemIds: Set<String> = emptySet(),
    val currentUploadItemId: String? = null,
    val currentUploadProgress: Float = 0f
) {
    fun progressFor(itemId: String, fallback: Float): Float =
        if (itemId == currentUploadItemId) currentUploadProgress else fallback
}

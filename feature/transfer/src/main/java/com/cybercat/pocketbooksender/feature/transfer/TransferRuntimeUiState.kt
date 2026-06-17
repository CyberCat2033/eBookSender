package com.cybercat.pocketbooksender.feature.transfer

data class TransferRuntimeUiState(
    val isTransferActive: Boolean = false,
    val activeTransferItemIds: Set<String> = emptySet(),
    val uploadProgressById: Map<String, Float> = emptyMap()
)

package com.cybercat.pocketbooksender.ui

import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.DeviceCatalog
import com.cybercat.pocketbooksender.model.PocketBookDevice

data class CatalogUiState(
    val connectedDevice: PocketBookDevice? = null,
    val deviceCatalog: DeviceCatalog = DeviceCatalog(),
    val settings: AppSettings = AppSettings(),
    val isEditMode: Boolean = false,
    val selectedFilePaths: Set<String> = emptySet(),
    val isDeleting: Boolean = false,
    val deleteErrorMessage: String? = null,
)

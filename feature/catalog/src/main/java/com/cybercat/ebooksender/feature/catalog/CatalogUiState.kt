package com.cybercat.ebooksender.feature.catalog

import com.cybercat.ebooksender.model.AppSettings
import com.cybercat.ebooksender.model.DeviceCatalog
import com.cybercat.ebooksender.model.RemoteDevice

data class CatalogUiState(
    val connectedDevice: RemoteDevice? = null,
    val deviceCatalog: DeviceCatalog = DeviceCatalog(),
    val settings: AppSettings = AppSettings(),
    val isEditMode: Boolean = false,
    val selectedFilePaths: Set<String> = emptySet(),
    val isDeleting: Boolean = false,
    val deleteErrorMessage: String? = null
)

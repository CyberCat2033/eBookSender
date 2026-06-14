package com.cybercat.pocketbooksender.ui

import com.cybercat.pocketbooksender.model.AppSettings

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val settingsStatusMessage: String? = null,
)

package com.cybercat.pocketbooksender.feature.settings

import com.cybercat.pocketbooksender.model.AppSettings

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val settingsStatusMessage: String? = null,
    val pendingRename: PendingRename? = null,
    val showLogoutWarning: Boolean = false,
    val activeFolderRename: FolderType? = null,
    val availableLocales: List<com.cybercat.pocketbooksender.localization.LocaleInfo> = emptyList(),
)

data class PendingRename(
    val folderType: FolderType,
    val oldName: String,
    val newName: String,
)

enum class FolderType {
    Books,
    Documents,
    Manga,
}

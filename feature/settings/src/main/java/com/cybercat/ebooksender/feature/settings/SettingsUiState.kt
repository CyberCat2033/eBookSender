package com.cybercat.ebooksender.feature.settings

import com.cybercat.ebooksender.model.AppSettings

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val settingsStatusMessage: SettingsStatusMessage? = null,
    val pendingRename: PendingRename? = null,
    val showLogoutWarning: Boolean = false,
    val activeFolderRename: FolderType? = null,
    val availableLocales: List<com.cybercat.ebooksender.localization.LocaleInfo> = emptyList()
)

data class PendingRename(val folderType: FolderType, val oldName: String, val newName: String)

sealed class SettingsStatusMessage {
    data class Text(val value: String) : SettingsStatusMessage()
    object FolderRenameNotSupported : SettingsStatusMessage()
}

enum class FolderType {
    Books,
    Documents,
    Manga
}

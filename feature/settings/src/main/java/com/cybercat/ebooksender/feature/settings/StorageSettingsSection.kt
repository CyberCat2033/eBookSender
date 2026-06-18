package com.cybercat.ebooksender.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.ImeAction
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.model.normalizeFtpRelativeRootPath

@Composable
internal fun StorageSettingsSection(
    state: SettingsUiState,
    folderFieldResetKey: Int,
    onRootPathChanged: (String) -> Unit,
    onBooksFolderNameChanged: (String) -> Unit,
    onDocumentsFolderNameChanged: (String) -> Unit,
    onMangaFolderNameChanged: (String) -> Unit
) {
    val strings = LocalStrings.current
    val activeFolderRename = state.activeFolderRename

    SettingsSection(title = strings.settingsStorageSection) {
        ValidatedSettingsField(
            value = state.settings.rootPath,
            onValueChange = onRootPathChanged,
            label = strings.settingsRootPath,
            leadingIcon = Icons.Outlined.Folder,
            validation = ::normalizeFtpRelativeRootPath
        )
        ValidatedSettingsField(
            value = state.settings.booksFolderName,
            onValueChange = onBooksFolderNameChanged,
            label = strings.settingsBooksFolder,
            resetKey = folderFieldResetKey,
            leadingIcon = Icons.Outlined.Folder,
            validation = { input -> sanitizeFolderName(input, "Books") },
            isSaving = activeFolderRename == FolderType.Books,
            actionEnabled = activeFolderRename == null
        )
        ValidatedSettingsField(
            value = state.settings.documentsFolderName,
            onValueChange = onDocumentsFolderNameChanged,
            label = strings.settingsDocsFolder,
            resetKey = folderFieldResetKey,
            leadingIcon = Icons.Outlined.Folder,
            validation = { input -> sanitizeFolderName(input, "Documents") },
            isSaving = activeFolderRename == FolderType.Documents,
            actionEnabled = activeFolderRename == null
        )
        ValidatedSettingsField(
            value = state.settings.mangaFolderName,
            onValueChange = onMangaFolderNameChanged,
            label = strings.settingsMangaFolder,
            resetKey = folderFieldResetKey,
            leadingIcon = Icons.Outlined.Folder,
            imeAction = ImeAction.Next,
            validation = { input -> sanitizeFolderName(input, "Manga") },
            isSaving = activeFolderRename == FolderType.Manga,
            actionEnabled = activeFolderRename == null
        )
    }
}

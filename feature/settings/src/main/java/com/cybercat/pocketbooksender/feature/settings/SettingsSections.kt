package com.cybercat.pocketbooksender.feature.settings

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cybercat.pocketbooksender.localization.LocalStrings
import com.cybercat.pocketbooksender.model.AppTheme
import com.cybercat.pocketbooksender.model.normalizeFtpRootPath
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import kotlinx.coroutines.delay

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
            validation = ::normalizeFtpRootPath
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

@Composable
internal fun NamingSettingsSection(
    state: SettingsUiState,
    focusedNamingTemplateSlot: NamingTemplateSlot?,
    onFocusChanged: (NamingTemplateSlot, Boolean) -> Unit,
    onDefaultDocumentsTagChanged: (String) -> Unit,
    onDefaultMangaSeriesChanged: (String) -> Unit,
    onBookFileNameTemplateChanged: (String) -> Unit,
    onDocumentsFileNameTemplateChanged: (String) -> Unit,
    onMangaFileNameTemplateChanged: (String) -> Unit
) {
    val strings = LocalStrings.current
    SettingsSection(title = strings.settingsNamingSection) {
        var namingTokensContainerCoordinates by remember {
            mutableStateOf<LayoutCoordinates?>(null)
        }
        var namingTokensTargetY by remember { mutableStateOf(0) }
        var namingTokensHeightPx by remember { mutableStateOf(0) }
        val commonTokens = mapOf(
            "title" to strings.settingsNamingExampleTitle,
            "author" to strings.settingsNamingExampleAuthor,
            "year" to strings.settingsNamingExampleYear,
            "ext" to "epub",
            "original" to strings.settingsNamingExampleOriginal,
            "tag" to strings.settingsNamingExampleTag,
            "series" to strings.settingsNamingExampleSeries,
            "index" to strings.settingsNamingExampleIndex,
            "volume" to strings.settingsNamingExampleVolume,
            "publisher" to strings.settingsNamingExamplePublisher
        )

        var bookTemplatePreview by remember(state.settings.bookFileNameTemplate) {
            mutableStateOf(state.settings.bookFileNameTemplate)
        }
        var docsTemplatePreview by remember(state.settings.documentsFileNameTemplate) {
            mutableStateOf(state.settings.documentsFileNameTemplate)
        }
        var mangaTemplatePreview by remember(state.settings.mangaFileNameTemplate) {
            mutableStateOf(state.settings.mangaFileNameTemplate)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    namingTokensContainerCoordinates = coordinates
                }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                NamingTokensAnchor(
                    slot = NamingTemplateSlot.Books,
                    activeSlot = focusedNamingTemplateSlot,
                    tokenHeightPx = namingTokensHeightPx,
                    containerCoordinates = namingTokensContainerCoordinates,
                    onPositioned = { namingTokensTargetY = it }
                )

                NamingTemplateBlock(
                    value = state.settings.bookFileNameTemplate,
                    onValueChange = onBookFileNameTemplateChanged,
                    label = strings.settingsNamingBooksTemplate,
                    imeAction = ImeAction.Next,
                    onPreviewChange = { bookTemplatePreview = it },
                    previewLabel = strings.get("settings_naming_preview", strings.categoryBooks),
                    previewTemplate = bookTemplatePreview,
                    exampleTokens = commonTokens,
                    folderName = state.settings.booksFolderName,
                    onFocusChanged = { isFocused ->
                        onFocusChanged(NamingTemplateSlot.Books, isFocused)
                    },
                    validation = { it.trim().ifBlank { "{title}" } }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NamingTokensAnchor(
                        slot = NamingTemplateSlot.Documents,
                        activeSlot = focusedNamingTemplateSlot,
                        tokenHeightPx = namingTokensHeightPx,
                        containerCoordinates = namingTokensContainerCoordinates,
                        onPositioned = { namingTokensTargetY = it }
                    )

                    ValidatedSettingsField(
                        value = state.settings.defaultDocumentsTag,
                        onValueChange = onDefaultDocumentsTagChanged,
                        label = strings.settingsNamingDocsTag,
                        imeAction = ImeAction.Next,
                        validation = { it.trim().ifBlank { "Untagged" } }
                    )
                    NamingTemplateBlock(
                        value = state.settings.documentsFileNameTemplate,
                        onValueChange = onDocumentsFileNameTemplateChanged,
                        label = strings.settingsNamingDocsTemplate,
                        imeAction = ImeAction.Next,
                        onPreviewChange = { docsTemplatePreview = it },
                        previewLabel = strings.get(
                            "settings_naming_preview",
                            strings.categoryDocuments
                        ),
                        previewTemplate = docsTemplatePreview,
                        exampleTokens = commonTokens,
                        folderName = state.settings.documentsFolderName,
                        onFocusChanged = { isFocused ->
                            onFocusChanged(NamingTemplateSlot.Documents, isFocused)
                        },
                        validation = { it.trim().ifBlank { "{title}" } }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NamingTokensAnchor(
                        slot = NamingTemplateSlot.Manga,
                        activeSlot = focusedNamingTemplateSlot,
                        tokenHeightPx = namingTokensHeightPx,
                        containerCoordinates = namingTokensContainerCoordinates,
                        onPositioned = { namingTokensTargetY = it }
                    )

                    ValidatedSettingsField(
                        value = state.settings.defaultMangaSeries,
                        onValueChange = onDefaultMangaSeriesChanged,
                        label = strings.settingsNamingMangaSeries,
                        imeAction = ImeAction.Next,
                        validation = { it.trim().ifBlank { "Unknown_Series" } }
                    )
                    NamingTemplateBlock(
                        value = state.settings.mangaFileNameTemplate,
                        onValueChange = onMangaFileNameTemplateChanged,
                        label = strings.settingsNamingMangaTemplate,
                        imeAction = ImeAction.Done,
                        onPreviewChange = { mangaTemplatePreview = it },
                        previewLabel = strings.get(
                            "settings_naming_preview",
                            strings.categoryManga
                        ),
                        previewTemplate = mangaTemplatePreview,
                        exampleTokens = commonTokens,
                        folderName = state.settings.mangaFolderName,
                        extension = "cbz",
                        onFocusChanged = { isFocused ->
                            onFocusChanged(NamingTemplateSlot.Manga, isFocused)
                        },
                        validation = { it.trim().ifBlank { "{series}_{volume}" } }
                    )
                }
            }

            if (focusedNamingTemplateSlot != null) {
                MovingNamingTokensHint(
                    text = strings.settingsNamingTokens,
                    targetOffsetY = namingTokensTargetY,
                    onHeightChanged = { namingTokensHeightPx = it }
                )
            }
        }
    }
}

@Composable
internal fun InterfaceSettingsSection(
    state: SettingsUiState,
    onDynamicColorChanged: (Boolean) -> Unit,
    onBypassVpnForLocalConnectionsChanged: (Boolean) -> Unit,
    onHapticFeedbackEnabledChanged: (Boolean) -> Unit,
    onWarnOnDisconnectedRenameChanged: (Boolean) -> Unit,
    onThemeChanged: (AppTheme) -> Unit,
    onLanguageClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current

    SettingsSection(title = strings.settingsInterfaceSection) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(strings.settingsDynamicColor, style = MaterialTheme.typography.bodyLarge)
                Text(
                    strings.settingsDynamicColorDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.settings.useDynamicColor,
                onCheckedChange = {
                    view.performHapticIfAllowed(
                        context,
                        state.settings.enableHaptics,
                        HapticFeedbackConstants.VIRTUAL_KEY
                    )
                    onDynamicColorChanged(it)
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(strings.settingsBypassVpn, style = MaterialTheme.typography.bodyLarge)
                Text(
                    strings.settingsBypassVpnDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.settings.bypassVpnForLocalConnections,
                onCheckedChange = {
                    view.performHapticIfAllowed(
                        context,
                        state.settings.enableHaptics,
                        HapticFeedbackConstants.VIRTUAL_KEY
                    )
                    onBypassVpnForLocalConnectionsChanged(it)
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(strings.settingsHaptic, style = MaterialTheme.typography.bodyLarge)
                Text(
                    strings.settingsHapticDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.settings.enableHaptics,
                onCheckedChange = {
                    view.performHapticIfAllowed(context, true, HapticFeedbackConstants.VIRTUAL_KEY)
                    onHapticFeedbackEnabledChanged(it)
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(strings.settingsWarnDisconnected, style = MaterialTheme.typography.bodyLarge)
                Text(
                    strings.settingsWarnDisconnectedDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.settings.warnOnDisconnectedRename,
                onCheckedChange = {
                    view.performHapticIfAllowed(
                        context,
                        state.settings.enableHaptics,
                        HapticFeedbackConstants.VIRTUAL_KEY
                    )
                    onWarnOnDisconnectedRenameChanged(it)
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    view.performHapticIfAllowed(
                        context,
                        state.settings.enableHaptics,
                        HapticFeedbackConstants.VIRTUAL_KEY
                    )
                    onLanguageClick()
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(strings.settingsLanguageOption, style = MaterialTheme.typography.bodyLarge)
                val currentLangName = if (state.settings.languageCode == "system") {
                    "${strings.settingsLanguageSystem} (${strings.languageName})"
                } else {
                    strings.languageName
                }
                Text(
                    currentLangName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.settingsTheme, style = MaterialTheme.typography.bodyLarge)
            val options = listOf(
                AppTheme.Light to strings.settingsThemeLight,
                AppTheme.Dark to strings.settingsThemeDark,
                AppTheme.System to strings.settingsThemeSystem
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (option, label) ->
                    SegmentedButton(
                        selected = state.settings.theme == option,
                        onClick = {
                            view.performHapticIfAllowed(
                                context,
                                state.settings.enableHaptics,
                                HapticFeedbackConstants.VIRTUAL_KEY
                            )
                            onThemeChanged(option)
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size
                        )
                    ) {
                        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
internal fun MaintenanceSettingsSection(
    state: SettingsUiState,
    onClearDownloadCache: () -> Unit,
    onLogoutAll: () -> Unit,
    onClearStatusMessage: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current

    SettingsSection(title = strings.settingsMaintenanceSection) {
        Column(
            modifier = Modifier.animateContentSize(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = IntSize.VisibilityThreshold
                )
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    view.performHapticIfAllowed(
                        context,
                        state.settings.enableHaptics,
                        HapticFeedbackConstants.LONG_PRESS
                    )
                    onClearDownloadCache()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.settingsClearCache)
            }

            Button(
                onClick = {
                    view.performHapticIfAllowed(
                        context,
                        state.settings.enableHaptics,
                        HapticFeedbackConstants.LONG_PRESS
                    )
                    onLogoutAll()
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.settingsLogoutAll)
            }

            val statusMessage = state.settingsStatusMessage
            AnimatedContent(
                targetState = statusMessage,
                transitionSpec = {
                    if (targetState != null) {
                        (
                            expandVertically(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                expandFrom = Alignment.Top
                            ) +
                                fadeIn(
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            )
                            .togetherWith(fadeOut())
                    } else {
                        fadeIn().togetherWith(
                            shrinkVertically(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                shrinkTowards = Alignment.Top
                            ) +
                                fadeOut(
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                        )
                    }
                },
                label = "StatusMessageAnimation"
            ) { message ->
                if (message != null) {
                    LaunchedEffect(message) {
                        delay(3000)
                        onClearStatusMessage()
                    }

                    val displayMessage = when {
                        message.startsWith("Cleared ") -> {
                            val size =
                                message.substringAfter(
                                    "Cleared "
                                ).substringBefore(" MB").toDoubleOrNull()
                                    ?: 0.0
                            strings.get("settings_cleared_cache", size)
                        }

                        message == "Nothing to clear" -> strings.settingsNothingToClear

                        message.startsWith("Renamed '") -> {
                            val old = message.substringAfter("Renamed '").substringBefore("' to '")
                            val new = message.substringAfter(
                                "' to '"
                            ).substringBefore("' on device")
                            strings.get("settings_renamed_on_device", old, new)
                        }

                        message.startsWith("Could not rename: folder '") -> {
                            val folder = message.substringAfter(
                                "Could not rename: folder '"
                            ).substringBefore("' already exists")
                            strings.get("settings_rename_failed_exists", folder)
                        }

                        message.startsWith("Could not rename folder on device: ") -> {
                            val err = message.substringAfter("Could not rename folder on device: ")
                            strings.get("settings_rename_failed_error", err)
                        }

                        else -> message
                    }

                    Text(
                        text = displayMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp).fillMaxWidth()
                    )
                } else {
                    Box(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

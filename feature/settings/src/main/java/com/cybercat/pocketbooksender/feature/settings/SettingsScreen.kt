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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.cybercat.pocketbooksender.ui.AnimatedAlertDialog
import com.cybercat.pocketbooksender.ui.LocalAdaptiveLayoutInfo
import com.cybercat.pocketbooksender.ui.LocalDismissDialog
import com.cybercat.pocketbooksender.ui.LocalDismissDialogAfter
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    scrollState: ScrollState,
    onRootPathChanged: (String) -> Unit,
    onBooksFolderNameChanged: (String) -> Unit,
    onDocumentsFolderNameChanged: (String) -> Unit,
    onMangaFolderNameChanged: (String) -> Unit,
    onDefaultDocumentsTagChanged: (String) -> Unit,
    onDefaultMangaSeriesChanged: (String) -> Unit,
    onBookFileNameTemplateChanged: (String) -> Unit,
    onDocumentsFileNameTemplateChanged: (String) -> Unit,
    onMangaFileNameTemplateChanged: (String) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onHapticFeedbackEnabledChanged: (Boolean) -> Unit,
    onBypassVpnForLocalConnectionsChanged: (Boolean) -> Unit,
    onClearDownloadCache: () -> Unit,
    onClearStatusMessage: () -> Unit,
    onThemeChanged: (AppTheme) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onWarnOnDisconnectedRenameChanged: (Boolean) -> Unit,
    onConfirmPendingRename: () -> Unit,
    onCancelPendingRename: () -> Unit,
    onLogoutAll: () -> Unit,
    onConfirmLogoutAll: () -> Unit,
    onDismissLogoutWarning: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val adaptiveLayout = LocalAdaptiveLayoutInfo.current
    val activeFolderRename = state.activeFolderRename
    var focusedNamingTemplateSlot by remember { mutableStateOf<NamingTemplateSlot?>(null) }

    // --- Rename warning dialog: local lifecycle for animated dismiss ---
    var showRenameWarning by remember { mutableStateOf(false) }
    var pendingRenameSnapshot by remember { mutableStateOf<PendingRename?>(null) }
    var folderFieldResetKey by remember { mutableStateOf(0) }
    val hadPendingRename = remember { mutableStateOf(false) }

    LaunchedEffect(state.pendingRename) {
        val hasPending = state.pendingRename != null
        if (hasPending) {
            pendingRenameSnapshot = state.pendingRename
            showRenameWarning = true
        }
        // When pendingRename disappears (cancel OR confirm), reset folder fields
        if (hadPendingRename.value && !hasPending) folderFieldResetKey++
        hadPendingRename.value = hasPending
    }

    fun updateFocusedNamingTemplateSlot(slot: NamingTemplateSlot, isFocused: Boolean) {
        focusedNamingTemplateSlot =
            when {
                isFocused -> slot
                focusedNamingTemplateSlot == slot -> null
                else -> focusedNamingTemplateSlot
            }
    }

    if (showRenameWarning) {
        pendingRenameSnapshot?.let { pending ->
            AnimatedAlertDialog(
                onDismissRequest = {
                    showRenameWarning = false
                    onCancelPendingRename()
                },
                title = { Text(strings.settingsDialogTitle) },
                text = {
                    val folderDescription =
                        when (pending.folderType) {
                            FolderType.Books -> strings.settingsBooksFolder
                            FolderType.Documents -> strings.settingsDocsFolder
                            FolderType.Manga -> strings.settingsMangaFolder
                        }
                    Text(strings.get("settings_dialog_body", folderDescription, pending.newName))
                },
                confirmButton = {
                    val dismiss = LocalDismissDialog.current
                    TextButton(
                        onClick = {
                            view.performHapticIfAllowed(
                                context,
                                state.settings.enableHaptics,
                                HapticFeedbackConstants.CONFIRM
                            )
                            onConfirmPendingRename()
                            dismiss()
                        }
                    ) {
                        Text(strings.settingsDialogConfirm)
                    }
                },
                dismissButton = {
                    val dismiss = LocalDismissDialog.current
                    TextButton(
                        onClick = {
                            view.performHapticIfAllowed(
                                context,
                                state.settings.enableHaptics,
                                HapticFeedbackConstants.VIRTUAL_KEY
                            )
                            onCancelPendingRename()
                            dismiss()
                        }
                    ) {
                        Text(strings.settingsDialogCancel)
                    }
                }
            )
        }
    }

    if (state.showLogoutWarning) {
        AnimatedAlertDialog(
            onDismissRequest = onDismissLogoutWarning,
            title = { Text(strings.settingsLogoutWarningTitle) },
            text = { Text(strings.settingsLogoutWarningBody) },
            confirmButton = {
                val dismiss = LocalDismissDialog.current
                TextButton(
                    onClick = {
                        view.performHapticIfAllowed(
                            context,
                            state.settings.enableHaptics,
                            HapticFeedbackConstants.LONG_PRESS
                        )
                        onConfirmLogoutAll()
                        dismiss()
                    }
                ) {
                    Text(strings.settingsLogoutWarningConfirm)
                }
            },
            dismissButton = {
                val dismiss = LocalDismissDialog.current
                TextButton(
                    onClick = {
                        view.performHapticIfAllowed(
                            context,
                            state.settings.enableHaptics,
                            HapticFeedbackConstants.VIRTUAL_KEY
                        )
                        onDismissLogoutWarning()
                        dismiss()
                    }
                ) {
                    Text(strings.settingsDialogCancel)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            val containerColor = MaterialTheme.colorScheme.background
            val contentColor = MaterialTheme.colorScheme.onBackground
            key(containerColor, contentColor) {
                TopAppBar(
                    title = { Text(strings.settingsTitle) },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = containerColor,
                            scrolledContainerColor = containerColor,
                            navigationIconContentColor = contentColor,
                            titleContentColor = contentColor,
                            actionIconContentColor = contentColor
                        )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(
                            horizontal = adaptiveLayout.screenHorizontalPadding,
                            vertical = 16.dp
                        ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
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

                SettingsSection(title = strings.settingsNamingSection) {
                    val activeNamingTemplateSlot = focusedNamingTemplateSlot
                    var namingTokensContainerCoordinates by remember {
                        mutableStateOf<LayoutCoordinates?>(null)
                    }
                    var namingTokensTargetY by remember { mutableStateOf(0) }
                    var namingTokensHeightPx by remember { mutableStateOf(0) }
                    val commonTokens =
                        mapOf(
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

                    var bookTemplatePreview by remember(
                        state.settings.bookFileNameTemplate
                    ) { mutableStateOf(state.settings.bookFileNameTemplate) }
                    var docsTemplatePreview by remember(
                        state.settings.documentsFileNameTemplate
                    ) { mutableStateOf(state.settings.documentsFileNameTemplate) }
                    var mangaTemplatePreview by remember(
                        state.settings.mangaFileNameTemplate
                    ) { mutableStateOf(state.settings.mangaFileNameTemplate) }

                    Box(
                        modifier =
                            Modifier
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
                                activeSlot = activeNamingTemplateSlot,
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
                                previewLabel = strings.get(
                                    "settings_naming_preview",
                                    strings.categoryBooks
                                ),
                                previewTemplate = bookTemplatePreview,
                                exampleTokens = commonTokens,
                                folderName = state.settings.booksFolderName,
                                onFocusChanged = { isFocused ->
                                    updateFocusedNamingTemplateSlot(
                                        NamingTemplateSlot.Books,
                                        isFocused
                                    )
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
                                    activeSlot = activeNamingTemplateSlot,
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
                                        updateFocusedNamingTemplateSlot(
                                            NamingTemplateSlot.Documents,
                                            isFocused
                                        )
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
                                    activeSlot = activeNamingTemplateSlot,
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
                                        updateFocusedNamingTemplateSlot(
                                            NamingTemplateSlot.Manga,
                                            isFocused
                                        )
                                    },
                                    validation = {
                                        it.trim().ifBlank { "{series}_{volume}" }
                                    }
                                )
                            }
                        }

                        if (activeNamingTemplateSlot != null) {
                            MovingNamingTokensHint(
                                text = strings.settingsNamingTokens,
                                targetOffsetY = namingTokensTargetY,
                                onHeightChanged = { namingTokensHeightPx = it }
                            )
                        }
                    }
                }

                SettingsSection(title = strings.settingsInterfaceSection) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                strings.settingsDynamicColor,
                                style = MaterialTheme.typography.bodyLarge
                            )
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
                            Text(
                                strings.settingsBypassVpn,
                                style = MaterialTheme.typography.bodyLarge
                            )
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
                                view.performHapticIfAllowed(
                                    context,
                                    true,
                                    HapticFeedbackConstants.VIRTUAL_KEY
                                )
                                onHapticFeedbackEnabledChanged(it)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                strings.settingsWarnDisconnected,
                                style = MaterialTheme.typography.bodyLarge
                            )
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

                    // Language Option
                    var showLanguageDialog by remember { mutableStateOf(false) }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    view.performHapticIfAllowed(
                                        context,
                                        state.settings.enableHaptics,
                                        HapticFeedbackConstants.VIRTUAL_KEY
                                    )
                                    showLanguageDialog = true
                                }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                strings.settingsLanguageOption,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            val currentLangName =
                                if (state.settings.languageCode == "system") {
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

                    if (showLanguageDialog) {
                        AnimatedAlertDialog(
                            onDismissRequest = { showLanguageDialog = false },
                            title = { Text(strings.settingsLanguageDialogTitle) },
                            text = {
                                val dismissAfter = LocalDismissDialogAfter.current
                                val selectLanguage: (String) -> Unit = { languageCode ->
                                    view.performHapticIfAllowed(
                                        context,
                                        state.settings.enableHaptics,
                                        HapticFeedbackConstants.VIRTUAL_KEY
                                    )
                                    dismissAfter {
                                        if (languageCode != state.settings.languageCode) {
                                            onLanguageChanged(languageCode)
                                        }
                                    }
                                }
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // "System language" row
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectLanguage("system")
                                                }.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        androidx.compose.material3.RadioButton(
                                            selected = state.settings.languageCode == "system",
                                            onClick = {
                                                selectLanguage("system")
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(strings.settingsLanguageSystem)
                                    }

                                    // Available locales list
                                    state.availableLocales.forEach { locale ->
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectLanguage(locale.code)
                                                    }.padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            androidx.compose.material3.RadioButton(
                                                selected =
                                                    state.settings.languageCode == locale.code,
                                                onClick = {
                                                    selectLanguage(locale.code)
                                                }
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(locale.name)
                                        }
                                    }
                                }
                            },
                            dismissButton = {
                                val dismiss = LocalDismissDialog.current
                                TextButton(onClick = dismiss) {
                                    Text(strings.settingsDialogCancel)
                                }
                            }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.settingsTheme, style = MaterialTheme.typography.bodyLarge)
                        val options =
                            listOf(
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
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsSection(title = strings.settingsMaintenanceSection) {
                    Column(
                        modifier =
                            Modifier.animateContentSize(
                                animationSpec =
                                    spring(
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
                            colors =
                                androidx.compose.material3.ButtonDefaults.buttonColors(
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
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            expandFrom = Alignment.Top
                                        ) +
                                            fadeIn(
                                                animationSpec = spring(
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        ).togetherWith(fadeOut())
                                } else {
                                    fadeIn().togetherWith(
                                        shrinkVertically(
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            shrinkTowards = Alignment.Top
                                        ) +
                                            fadeOut(
                                                animationSpec = spring(
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
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

                                val displayMessage =
                                    when {
                                        message.startsWith("Cleared ") -> {
                                            val size =
                                                message.substringAfter(
                                                    "Cleared "
                                                ).substringBefore(" MB").toDoubleOrNull()
                                                    ?: 0.0
                                            strings.get("settings_cleared_cache", size)
                                        }

                                        message == "Nothing to clear" -> {
                                            strings.settingsNothingToClear
                                        }

                                        message.startsWith("Renamed '") -> {
                                            val old = message.substringAfter(
                                                "Renamed '"
                                            ).substringBefore("' to '")
                                            val new = message.substringAfter(
                                                "' to '"
                                            ).substringBefore("' on device")
                                            strings.get("settings_renamed_on_device", old, new)
                                        }

                                        message.startsWith("Could not rename: folder '") -> {
                                            val folder =
                                                message
                                                    .substringAfter(
                                                        "Could not rename: folder '"
                                                    ).substringBefore("' already exists")
                                            strings.get("settings_rename_failed_exists", folder)
                                        }

                                        message.startsWith(
                                            "Could not rename folder on device: "
                                        ) -> {
                                            val err = message.substringAfter(
                                                "Could not rename folder on device: "
                                            )
                                            strings.get("settings_rename_failed_error", err)
                                        }

                                        else -> {
                                            message
                                        }
                                    }

                                Text(
                                    text = displayMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier =
                                        Modifier
                                            .padding(top = 4.dp)
                                            .fillMaxWidth()
                                )
                            } else {
                                // Empty box to keep layout consistent during exit
                                Box(Modifier.fillMaxWidth())
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

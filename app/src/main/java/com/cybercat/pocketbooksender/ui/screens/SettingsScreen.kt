package com.cybercat.pocketbooksender.ui.screens

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import com.cybercat.pocketbooksender.ui.FolderType
import com.cybercat.pocketbooksender.ui.PendingRename
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cybercat.pocketbooksender.model.AppTheme
import com.cybercat.pocketbooksender.ui.SettingsUiState
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import com.cybercat.pocketbooksender.localization.LocalStrings
import kotlinx.coroutines.delay

@Composable
private fun ValidatedSettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector? = null,
    placeholder: String = "",
    imeAction: ImeAction = ImeAction.Next,
    onPreviewChange: ((String) -> Unit)? = null,
    validation: (String) -> String = { it }
) {
    var textFieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val view = LocalView.current
    val isChanged = textFieldValue.text != value

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            onPreviewChange?.invoke(newValue.text)
        },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    textFieldValue = textFieldValue.copy(
                        selection = TextRange(textFieldValue.text.length)
                    )
                }
            },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = leadingIcon?.let { icon ->
            { Icon(icon, contentDescription = null) }
        },
        trailingIcon = if (isChanged) {
            {
                IconButton(onClick = {
                    view.performHapticIfAllowed(context, true, HapticFeedbackConstants.CONFIRM)
                    onValueChange(validation(textFieldValue.text))
                    focusManager.clearFocus()
                }) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Save",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onAny = {
                view.performHapticIfAllowed(context, true, HapticFeedbackConstants.CONFIRM)
                onValueChange(validation(textFieldValue.text))
                if (imeAction == ImeAction.Done) {
                    focusManager.clearFocus()
                } else {
                    defaultKeyboardAction(imeAction)
                }
            }
        )
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun NamingPreview(
    label: String,
    template: String,
    exampleTokens: Map<String, String>,
    folderName: String,
    extension: String = "epub"
) {
    val rendered = exampleTokens.entries.fold(template.ifBlank { "{title}" }) { current, (key, value) ->
        current.replace("{$key}", value)
    }
    val path = "$folderName/${exampleTokens["author"] ?: exampleTokens["tag"] ?: exampleTokens["series"] ?: "..."}/$rendered.$extension"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "$label preview",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

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
    onClearDownloadCache: () -> Unit,
    onClearStatusMessage: () -> Unit,
    onThemeChanged: (AppTheme) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onWarnOnDisconnectedRenameChanged: (Boolean) -> Unit,
    onConfirmPendingRename: () -> Unit,
    onCancelPendingRename: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current

    state.pendingRename?.let { pending ->
        AlertDialog(
            onDismissRequest = onCancelPendingRename,
            title = { Text(strings.settingsDialogTitle) },
            text = {
                val folderDescription = when (pending.folderType) {
                    FolderType.Books -> strings.settingsBooksFolder
                    FolderType.Documents -> strings.settingsDocsFolder
                    FolderType.Manga -> strings.settingsMangaFolder
                }
                Text(strings.get("settings_dialog_body", folderDescription, pending.newName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.CONFIRM)
                        onConfirmPendingRename()
                    }
                ) {
                    Text(strings.settingsDialogConfirm)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                        onCancelPendingRename()
                    }
                ) {
                    Text(strings.settingsDialogCancel)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle) },
                windowInsets = WindowInsets(0.dp),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsSection(title = strings.settingsStorageSection) {
                ValidatedSettingsField(
                    value = state.settings.rootPath,
                    onValueChange = onRootPathChanged,
                    label = strings.settingsRootPath,
                    leadingIcon = Icons.Outlined.Folder,
                    validation = { input ->
                        val trimmed = input.trim().replace('\\', '/')
                        if (trimmed.isBlank() || trimmed == "/" || trimmed.contains("..")) "/mnt/ext1"
                        else "/" + trimmed.trim('/')
                    }
                )
                ValidatedSettingsField(
                    value = state.settings.booksFolderName,
                    onValueChange = onBooksFolderNameChanged,
                    label = strings.settingsBooksFolder,
                    leadingIcon = Icons.Outlined.Folder,
                    validation = { input ->
                        val clean = input.trim().replace(Regex("[\\\\/:*?\"<>|]"), "")
                        if (clean.isBlank() || clean.equals("system", ignoreCase = true)) "Books" else clean
                    }
                )
                ValidatedSettingsField(
                    value = state.settings.documentsFolderName,
                    onValueChange = onDocumentsFolderNameChanged,
                    label = strings.settingsDocsFolder,
                    leadingIcon = Icons.Outlined.Folder,
                    validation = { input ->
                        val clean = input.trim().replace(Regex("[\\\\/:*?\"<>|]"), "")
                        if (clean.isBlank() || clean.equals("system", ignoreCase = true)) "Documents" else clean
                    }
                )
                ValidatedSettingsField(
                    value = state.settings.mangaFolderName,
                    onValueChange = onMangaFolderNameChanged,
                    label = strings.settingsMangaFolder,
                    leadingIcon = Icons.Outlined.Folder,
                    imeAction = ImeAction.Next,
                    validation = { input ->
                        val clean = input.trim().replace(Regex("[\\\\/:*?\"<>|]"), "")
                        if (clean.isBlank() || clean.equals("system", ignoreCase = true)) "Manga" else clean
                    }
                )
            }

            SettingsSection(title = strings.settingsNamingSection) {
                Text(
                    text = strings.settingsNamingTokens,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                
                val commonTokens = mapOf(
                    "title" to "The Great Gatsby",
                    "author" to "F. Scott Fitzgerald",
                    "year" to "1925",
                    "ext" to "epub",
                    "original" to "great_gatsby_final",
                    "tag" to "Classic",
                    "series" to "Masterpieces",
                    "index" to "1",
                    "volume" to "Vol.1",
                    "publisher" to "Scribner"
                )

                var bookTemplatePreview by remember(state.settings.bookFileNameTemplate) { mutableStateOf(state.settings.bookFileNameTemplate) }
                var docsTemplatePreview by remember(state.settings.documentsFileNameTemplate) { mutableStateOf(state.settings.documentsFileNameTemplate) }
                var mangaTemplatePreview by remember(state.settings.mangaFileNameTemplate) { mutableStateOf(state.settings.mangaFileNameTemplate) }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ValidatedSettingsField(
                        value = state.settings.bookFileNameTemplate,
                        onValueChange = onBookFileNameTemplateChanged,
                        label = strings.settingsNamingBooksTemplate,
                        imeAction = ImeAction.Next,
                        onPreviewChange = { bookTemplatePreview = it },
                        validation = { it.trim().ifBlank { "{title}" } }
                    )
                    NamingPreview(
                        label = strings.get("settings_naming_preview", strings.categoryBooks),
                        template = bookTemplatePreview,
                        exampleTokens = commonTokens,
                        folderName = state.settings.booksFolderName
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ValidatedSettingsField(
                        value = state.settings.defaultDocumentsTag,
                        onValueChange = onDefaultDocumentsTagChanged,
                        label = strings.settingsNamingDocsTag,
                        imeAction = ImeAction.Next,
                        validation = { it.trim().ifBlank { "Untagged" } }
                    )
                    ValidatedSettingsField(
                        value = state.settings.documentsFileNameTemplate,
                        onValueChange = onDocumentsFileNameTemplateChanged,
                        label = strings.settingsNamingDocsTemplate,
                        imeAction = ImeAction.Next,
                        onPreviewChange = { docsTemplatePreview = it },
                        validation = { it.trim().ifBlank { "{title}" } }
                    )
                    NamingPreview(
                        label = strings.get("settings_naming_preview", strings.categoryDocuments),
                        template = docsTemplatePreview,
                        exampleTokens = commonTokens,
                        folderName = state.settings.documentsFolderName
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ValidatedSettingsField(
                        value = state.settings.defaultMangaSeries,
                        onValueChange = onDefaultMangaSeriesChanged,
                        label = strings.settingsNamingMangaSeries,
                        imeAction = ImeAction.Next,
                        validation = { it.trim().ifBlank { "Unknown_Series" } }
                    )
                    ValidatedSettingsField(
                        value = state.settings.mangaFileNameTemplate,
                        onValueChange = onMangaFileNameTemplateChanged,
                        label = strings.settingsNamingMangaTemplate,
                        imeAction = ImeAction.Done,
                        onPreviewChange = { mangaTemplatePreview = it },
                        validation = { it.trim().ifBlank { "{series}_{volume}" } }
                    )
                    NamingPreview(
                        label = strings.get("settings_naming_preview", strings.categoryManga),
                        template = mangaTemplatePreview,
                        exampleTokens = commonTokens,
                        folderName = state.settings.mangaFolderName,
                        extension = "cbz"
                    )
                }
            }

            SettingsSection(title = strings.settingsInterfaceSection) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(strings.settingsDynamicColor, style = MaterialTheme.typography.bodyLarge)
                        Text(strings.settingsDynamicColorDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = state.settings.useDynamicColor,
                        onCheckedChange = {
                            view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                            onDynamicColorChanged(it)
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(strings.settingsHaptic, style = MaterialTheme.typography.bodyLarge)
                        Text(strings.settingsHapticDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text(strings.settingsWarnDisconnectedDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = state.settings.warnOnDisconnectedRename,
                        onCheckedChange = {
                            view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                            onWarnOnDisconnectedRenameChanged(it)
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // Language Option
                var showLanguageDialog by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                            showLanguageDialog = true
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
                        Text(currentLangName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (showLanguageDialog) {
                    AlertDialog(
                        onDismissRequest = { showLanguageDialog = false },
                        title = { Text(strings.settingsLanguageDialogTitle) },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // "System language" row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                            onLanguageChanged("system")
                                            showLanguageDialog = false
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.RadioButton(
                                        selected = state.settings.languageCode == "system",
                                        onClick = {
                                            view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                            onLanguageChanged("system")
                                            showLanguageDialog = false
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.settingsLanguageSystem)
                                }

                                // Available locales list
                                state.availableLocales.forEach { locale ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                                onLanguageChanged(locale.code)
                                                showLanguageDialog = false
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        androidx.compose.material3.RadioButton(
                                            selected = state.settings.languageCode == locale.code,
                                            onClick = {
                                                view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                                onLanguageChanged(locale.code)
                                                showLanguageDialog = false
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(locale.name)
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showLanguageDialog = false }) {
                                Text(strings.settingsDialogCancel)
                            }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

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
                                    view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                    onThemeChanged(option)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                            ) { Text(label) }
                        }
                    }
                }
            }

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
                            view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.LONG_PRESS)
                            onClearDownloadCache()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.settingsClearCache)
                    }

                    val statusMessage = state.settingsStatusMessage
                    AnimatedContent(
                        targetState = statusMessage,
                        transitionSpec = {
                            if (targetState != null) {
                                (expandVertically(
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    expandFrom = Alignment.Top
                                ) + fadeIn(
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )).togetherWith(fadeOut())
                            } else {
                                fadeIn().togetherWith(
                                    shrinkVertically(
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        shrinkTowards = Alignment.Top
                                    ) + fadeOut(
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
                                    val size = message.substringAfter("Cleared ").substringBefore(" MB").toDoubleOrNull() ?: 0.0
                                    strings.get("settings_cleared_cache", size)
                                }
                                message == "Nothing to clear" -> strings.settingsNothingToClear
                                message.startsWith("Renamed '") -> {
                                    val old = message.substringAfter("Renamed '").substringBefore("' to '")
                                    val new = message.substringAfter("' to '").substringBefore("' on device")
                                    strings.get("settings_renamed_on_device", old, new)
                                }
                                message.startsWith("Could not rename: folder '") -> {
                                    val folder = message.substringAfter("Could not rename: folder '").substringBefore("' already exists")
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
                                modifier = Modifier
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

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
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
) {
    val context = LocalContext.current
    val view = LocalView.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            SettingsSection(title = "POCKETBOOK STORAGE") {
                ValidatedSettingsField(
                    value = state.settings.rootPath,
                    onValueChange = onRootPathChanged,
                    label = "Root path",
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
                    label = "Books folder name",
                    leadingIcon = Icons.Outlined.Folder,
                    validation = { input ->
                        val clean = input.trim().replace(Regex("[\\\\/:*?\"<>|]"), "")
                        if (clean.isBlank() || clean.equals("system", ignoreCase = true)) "Books" else clean
                    }
                )
                ValidatedSettingsField(
                    value = state.settings.documentsFolderName,
                    onValueChange = onDocumentsFolderNameChanged,
                    label = "Documents folder name",
                    leadingIcon = Icons.Outlined.Folder,
                    validation = { input ->
                        val clean = input.trim().replace(Regex("[\\\\/:*?\"<>|]"), "")
                        if (clean.isBlank() || clean.equals("system", ignoreCase = true)) "Documents" else clean
                    }
                )
                ValidatedSettingsField(
                    value = state.settings.mangaFolderName,
                    onValueChange = onMangaFolderNameChanged,
                    label = "Manga folder name",
                    leadingIcon = Icons.Outlined.Folder,
                    imeAction = ImeAction.Next,
                    validation = { input ->
                        val clean = input.trim().replace(Regex("[\\\\/:*?\"<>|]"), "")
                        if (clean.isBlank() || clean.equals("system", ignoreCase = true)) "Manga" else clean
                    }
                )
            }

            SettingsSection(title = "NAMING RULES") {
                Text(
                    text = "Tokens: {title}, {author}, {tag}, {series}, {volume}, {year}, {index}, {publisher}, {ext}, {original}.",
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
                        label = "Books file name template",
                        imeAction = ImeAction.Next,
                        onPreviewChange = { bookTemplatePreview = it },
                        validation = { it.trim().ifBlank { "{title}" } }
                    )
                    NamingPreview(
                        label = "Books",
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
                        label = "Default documents tag",
                        imeAction = ImeAction.Next,
                        validation = { it.trim().ifBlank { "Untagged" } }
                    )
                    ValidatedSettingsField(
                        value = state.settings.documentsFileNameTemplate,
                        onValueChange = onDocumentsFileNameTemplateChanged,
                        label = "Documents file name template",
                        imeAction = ImeAction.Next,
                        onPreviewChange = { docsTemplatePreview = it },
                        validation = { it.trim().ifBlank { "{title}" } }
                    )
                    NamingPreview(
                        label = "Documents",
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
                        label = "Default manga series",
                        imeAction = ImeAction.Next,
                        validation = { it.trim().ifBlank { "Unknown_Series" } }
                    )
                    ValidatedSettingsField(
                        value = state.settings.mangaFileNameTemplate,
                        onValueChange = onMangaFileNameTemplateChanged,
                        label = "Manga file name template",
                        imeAction = ImeAction.Done,
                        onPreviewChange = { mangaTemplatePreview = it },
                        validation = { it.trim().ifBlank { "{series}_{volume}" } }
                    )
                    NamingPreview(
                        label = "Manga",
                        template = mangaTemplatePreview,
                        exampleTokens = commonTokens,
                        folderName = state.settings.mangaFolderName,
                        extension = "cbz"
                    )
                }
            }

            SettingsSection(title = "INTERFACE") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Dynamic color", style = MaterialTheme.typography.bodyLarge)
                        Text("Use Material You colors", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("Haptic feedback", style = MaterialTheme.typography.bodyLarge)
                        Text("Vibrate on interactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = state.settings.enableHaptics,
                        onCheckedChange = {
                            view.performHapticIfAllowed(context, true, HapticFeedbackConstants.VIRTUAL_KEY)
                            onHapticFeedbackEnabledChanged(it)
                        }
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("App theme", style = MaterialTheme.typography.bodyLarge)
                    val options = listOf(
                        AppTheme.Light to "Light",
                        AppTheme.Dark to "Dark",
                        AppTheme.System to "System"
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

            SettingsSection(title = "MAINTENANCE") {
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
                        Text("Clear download cache")
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
                            Text(
                                text = message,
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

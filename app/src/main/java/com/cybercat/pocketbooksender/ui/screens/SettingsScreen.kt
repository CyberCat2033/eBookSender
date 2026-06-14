package com.cybercat.pocketbooksender.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.cybercat.pocketbooksender.ui.SettingsUiState
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.cybercat.pocketbooksender.util.performHapticIfAllowed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    scrollState: ScrollState,
    onRootPathChanged: (String) -> Unit,
    onDefaultProgrammingTagChanged: (String) -> Unit,
    onDefaultMangaSeriesChanged: (String) -> Unit,
    onBookFileNameTemplateChanged: (String) -> Unit,
    onProgrammingFileNameTemplateChanged: (String) -> Unit,
    onMangaFileNameTemplateChanged: (String) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onHapticFeedbackEnabledChanged: (Boolean) -> Unit,
    onClearDownloadCache: () -> Unit,
) {
    var latestStatusMessage by remember { mutableStateOf(state.settingsStatusMessage.orEmpty()) }
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(state.settingsStatusMessage) {
        state.settingsStatusMessage
            ?.takeIf { it.isNotBlank() }
            ?.let { latestStatusMessage = it }
    }

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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("PocketBook storage", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = state.settings.rootPath,
                        onValueChange = onRootPathChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Root path") },
                        leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                        singleLine = true,
                    )
                }
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Naming rules", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Tokens: {title}, {author}, {tag}, {series}, {volume}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.settings.defaultProgrammingTag,
                        onValueChange = onDefaultProgrammingTagChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Default programming tag") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.settings.defaultMangaSeries,
                        onValueChange = onDefaultMangaSeriesChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Default manga series") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.settings.bookFileNameTemplate,
                        onValueChange = onBookFileNameTemplateChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Books file name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.settings.programmingFileNameTemplate,
                        onValueChange = onProgrammingFileNameTemplateChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Programming file name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.settings.mangaFileNameTemplate,
                        onValueChange = onMangaFileNameTemplateChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Manga file name") },
                        singleLine = true,
                    )
                }
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Dynamic color", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Use Material You colors from wallpaper on Android 12+.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.settings.useDynamicColor,
                        onCheckedChange = { checked ->
                            view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                            onDynamicColorChanged(checked)
                        },
                    )
                }
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Haptic feedback", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Vibrate on key actions, successes, and errors.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.settings.enableHaptics,
                        onCheckedChange = { checked ->
                            view.performHapticIfAllowed(context, true, HapticFeedbackConstants.VIRTUAL_KEY)
                            onHapticFeedbackEnabledChanged(checked)
                        },
                    )
                }
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Downloaded cache", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Clear temporary OPDS books and manga files. History, settings, and downloaded chapter marks stay intact.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.LONG_PRESS)
                            onClearDownloadCache()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Clear downloaded files")
                    }
                    AnimatedVisibility(
                        visible = state.settingsStatusMessage != null,
                        enter = fadeIn() + slideInVertically { height -> -height / 4 },
                        exit = fadeOut() + slideOutVertically { height -> -height / 4 },
                    ) {
                        Text(
                            text = latestStatusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

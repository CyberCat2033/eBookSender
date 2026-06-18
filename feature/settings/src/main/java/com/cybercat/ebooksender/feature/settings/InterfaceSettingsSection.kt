package com.cybercat.ebooksender.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.model.AppTheme
import com.cybercat.ebooksender.model.MangaLoginMode
import com.cybercat.ebooksender.util.AppHapticFeedback
import com.cybercat.ebooksender.util.performHapticIfAllowed

@Composable
internal fun InterfaceSettingsSection(
    state: SettingsUiState,
    onDynamicColorChanged: (Boolean) -> Unit,
    onBypassVpnForLocalConnectionsChanged: (Boolean) -> Unit,
    onHapticFeedbackEnabledChanged: (Boolean) -> Unit,
    onWarnOnDisconnectedRenameChanged: (Boolean) -> Unit,
    onThemeChanged: (AppTheme) -> Unit,
    onLanguageClick: () -> Unit,
    onMangaLoginModeClick: () -> Unit
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
                        AppHapticFeedback.Press
                    )
                    onDynamicColorChanged(it)
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    view.performHapticIfAllowed(
                        context,
                        state.settings.enableHaptics,
                        AppHapticFeedback.Press
                    )
                    onMangaLoginModeClick()
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(strings.settingsMangaLoginMode, style = MaterialTheme.typography.bodyLarge)
                Text(
                    state.settings.mangaLoginMode.displayName(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                        AppHapticFeedback.Press
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
                    view.performHapticIfAllowed(context, true, AppHapticFeedback.Press)
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
                        AppHapticFeedback.Press
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
                        AppHapticFeedback.Press
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
                                AppHapticFeedback.Press
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
internal fun MangaLoginMode.displayName(): String {
    val strings = LocalStrings.current
    return when (this) {
        MangaLoginMode.Ask -> strings.settingsMangaLoginModeAsk
        MangaLoginMode.WebView -> strings.settingsMangaLoginModeWebView
        MangaLoginMode.Native -> strings.settingsMangaLoginModeNative
    }
}

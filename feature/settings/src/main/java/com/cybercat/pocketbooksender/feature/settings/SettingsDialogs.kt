package com.cybercat.pocketbooksender.feature.settings

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.cybercat.pocketbooksender.localization.LocalStrings
import com.cybercat.pocketbooksender.ui.AnimatedAlertDialog
import com.cybercat.pocketbooksender.ui.LocalDismissDialog
import com.cybercat.pocketbooksender.ui.LocalDismissDialogAfter
import com.cybercat.pocketbooksender.util.performHapticIfAllowed

/**
 * Confirmation dialog shown when a folder rename is pending (device-connected rename flow).
 */
@Composable
internal fun SettingsRenameWarningDialog(
    pending: PendingRename,
    enableHaptics: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val folderDescription = when (pending.folderType) {
        FolderType.Books -> strings.settingsBooksFolder
        FolderType.Documents -> strings.settingsDocsFolder
        FolderType.Manga -> strings.settingsMangaFolder
    }

    AnimatedAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(strings.settingsDialogTitle) },
        text = {
            Text(strings.get("settings_dialog_body", folderDescription, pending.newName))
        },
        confirmButton = {
            val dismiss = LocalDismissDialog.current
            TextButton(
                onClick = {
                    view.performHapticIfAllowed(
                        context,
                        enableHaptics,
                        HapticFeedbackConstants.CONFIRM
                    )
                    onConfirm()
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
                        enableHaptics,
                        HapticFeedbackConstants.VIRTUAL_KEY
                    )
                    onCancel()
                    dismiss()
                }
            ) {
                Text(strings.settingsDialogCancel)
            }
        }
    )
}

/**
 * Confirmation dialog shown before logging out all OPDS sources.
 */
@Composable
internal fun SettingsLogoutWarningDialog(
    enableHaptics: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.settingsLogoutWarningTitle) },
        text = { Text(strings.settingsLogoutWarningBody) },
        confirmButton = {
            val dismiss = LocalDismissDialog.current
            TextButton(
                onClick = {
                    view.performHapticIfAllowed(
                        context,
                        enableHaptics,
                        HapticFeedbackConstants.LONG_PRESS
                    )
                    onConfirm()
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
                        enableHaptics,
                        HapticFeedbackConstants.VIRTUAL_KEY
                    )
                    onDismiss()
                    dismiss()
                }
            ) {
                Text(strings.settingsDialogCancel)
            }
        }
    )
}

/**
 * Language picker dialog shown from the Interface section.
 */
@Composable
internal fun SettingsLanguageDialog(
    state: SettingsUiState,
    enableHaptics: Boolean,
    onLanguageChanged: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.settingsLanguageDialogTitle) },
        text = {
            val dismissAfter = LocalDismissDialogAfter.current
            val selectLanguage: (String) -> Unit = { languageCode ->
                view.performHapticIfAllowed(
                    context,
                    enableHaptics,
                    HapticFeedbackConstants.VIRTUAL_KEY
                )
                dismissAfter {
                    if (languageCode != state.settings.languageCode) {
                        onLanguageChanged(languageCode)
                    }
                }
            }
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
                        .clickable { selectLanguage("system") }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.settings.languageCode == "system",
                        onClick = { selectLanguage("system") }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(strings.settingsLanguageSystem)
                }

                // Available locales list
                state.availableLocales.forEach { locale ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectLanguage(locale.code) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.settings.languageCode == locale.code,
                            onClick = { selectLanguage(locale.code) }
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

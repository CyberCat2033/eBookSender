package com.cybercat.ebooksender.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.cybercat.ebooksender.data.update.AppUpdateStatus
import com.cybercat.ebooksender.data.update.AvailableAppUpdate
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.util.AppHapticFeedback
import com.cybercat.ebooksender.util.performHapticIfAllowed

@Composable
fun AppUpdateDialog(
    status: AppUpdateStatus,
    enableHaptics: Boolean,
    loadChangelog: suspend (AvailableAppUpdate, String) -> String?,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val view = LocalView.current
    val update = when (status) {
        is AppUpdateStatus.UpdateAvailable -> status.update
        else -> null
    } ?: return
    val languageCode = strings.languageCode
    val hasChangelog = update.changelogUrlFor(languageCode) != null
    var changelog by remember(update.versionCode, languageCode) { mutableStateOf<String?>(null) }
    var changelogLoaded by remember(update.versionCode, languageCode) {
        mutableStateOf(!hasChangelog)
    }

    LaunchedEffect(update.versionCode, languageCode) {
        if (!hasChangelog) {
            changelog = null
            changelogLoaded = true
            return@LaunchedEffect
        }
        changelog = null
        changelogLoaded = false
        changelog = loadChangelog(update, languageCode)
        changelogLoaded = true
    }

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.get("update_dialog_title", update.versionName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    strings.get(
                        "update_dialog_body",
                        update.versionName,
                        update.versionCode
                    )
                )
                if (hasChangelog) {
                    HorizontalDivider()
                    Text(
                        text = strings.updateDialogChangelogTitle,
                        style = MaterialTheme.typography.titleSmall
                    )
                    when {
                        !changelogLoaded -> CircularProgressIndicator()

                        changelog.isNullOrBlank() -> Text(strings.updateDialogChangelogUnavailable)

                        else -> Text(
                            text = changelog.orEmpty(),
                            modifier = Modifier
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        },
        confirmButton = {
            val dismiss = LocalDismissDialog.current
            TextButton(
                onClick = {
                    view.performHapticIfAllowed(
                        context,
                        enableHaptics,
                        AppHapticFeedback.Confirm
                    )
                    onInstall()
                    dismiss()
                }
            ) {
                Text(strings.updateDialogInstall)
            }
        },
        dismissButton = {
            val dismiss = LocalDismissDialog.current
            TextButton(
                onClick = {
                    view.performHapticIfAllowed(
                        context,
                        enableHaptics,
                        AppHapticFeedback.Press
                    )
                    onDismiss()
                    dismiss()
                }
            ) {
                Text(strings.updateDialogLater)
            }
        }
    )
}

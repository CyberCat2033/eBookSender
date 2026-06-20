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
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.util.AppHapticFeedback
import com.cybercat.ebooksender.util.performHapticIfAllowed

@Composable
fun UpdateAvailableDialog(
    versionCode: Long,
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    enableHaptics: Boolean,
    hasChangelog: Boolean,
    loadChangelog: suspend (String) -> String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val view = LocalView.current
    val languageCode = strings.languageCode
    var changelog by remember(versionCode, languageCode) { mutableStateOf<String?>(null) }
    var changelogLoaded by remember(versionCode, languageCode) {
        mutableStateOf(!hasChangelog)
    }

    LaunchedEffect(versionCode, languageCode, hasChangelog) {
        if (!hasChangelog) {
            changelog = null
            changelogLoaded = true
            return@LaunchedEffect
        }
        changelog = null
        changelogLoaded = false
        changelog = loadChangelog(languageCode)
        changelogLoaded = true
    }

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body)
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
                    onConfirm()
                    dismiss()
                }
            ) {
                Text(confirmLabel)
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
                Text(dismissLabel)
            }
        }
    )
}

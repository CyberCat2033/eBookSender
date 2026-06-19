package com.cybercat.ebooksender.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.cybercat.ebooksender.data.update.AppUpdateStatus
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.util.AppHapticFeedback
import com.cybercat.ebooksender.util.performHapticIfAllowed

@Composable
fun AppUpdateDialog(
    status: AppUpdateStatus,
    enableHaptics: Boolean,
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

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.get("update_dialog_title", update.versionName)) },
        text = {
            Text(
                strings.get(
                    "update_dialog_body",
                    update.versionName,
                    update.versionCode
                )
            )
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

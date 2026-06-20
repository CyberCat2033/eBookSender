package com.cybercat.ebooksender.ui

import androidx.compose.runtime.Composable
import com.cybercat.ebooksender.data.update.AppUpdateStatus
import com.cybercat.ebooksender.data.update.AvailableAppUpdate
import com.cybercat.ebooksender.localization.LocalStrings

@Composable
fun AppUpdateDialog(
    status: AppUpdateStatus,
    enableHaptics: Boolean,
    loadChangelog: suspend (AvailableAppUpdate, String) -> String?,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val update = when (status) {
        is AppUpdateStatus.UpdateAvailable -> status.update
        else -> null
    } ?: return
    val languageCode = strings.languageCode

    UpdateAvailableDialog(
        versionCode = update.versionCode,
        title = strings.get("update_dialog_title", update.versionName),
        body = strings.get(
            "update_dialog_body",
            update.versionName,
            update.versionCode
        ),
        confirmLabel = strings.updateDialogInstall,
        dismissLabel = strings.updateDialogLater,
        enableHaptics = enableHaptics,
        hasChangelog = update.changelogUrlFor(languageCode) != null,
        loadChangelog = { code -> loadChangelog(update, code) },
        onConfirm = onInstall,
        onDismiss = onDismiss
    )
}

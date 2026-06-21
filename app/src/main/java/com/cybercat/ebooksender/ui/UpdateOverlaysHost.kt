package com.cybercat.ebooksender.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.cybercat.ebooksender.data.update.AppUpdateState
import com.cybercat.ebooksender.data.update.AvailableAppUpdate
import com.cybercat.ebooksender.data.update.AvailablePocketBookServerUpdate
import com.cybercat.ebooksender.data.update.PocketBookServerUpdateState
import com.cybercat.ebooksender.data.update.PocketBookServerUpdateStatus
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.util.AppHapticFeedback
import com.cybercat.ebooksender.util.performHapticIfAllowed

private val UPDATE_PROGRESS_OVERLAY_MARGIN = 16.dp

@Composable
internal fun BoxScope.UpdateOverlaysHost(
    appUpdateState: AppUpdateState,
    onLoadUpdateChangelog: suspend (AvailableAppUpdate, String) -> String?,
    onInstallUpdate: () -> Unit,
    onCancelUpdateDownload: () -> Unit,
    pocketBookServerUpdateState: PocketBookServerUpdateState,
    onLoadPocketBookServerUpdateChangelog:
    suspend (AvailablePocketBookServerUpdate, String) -> String?,
    onInstallPocketBookServerUpdate: () -> Unit,
    onCancelPocketBookServerUpdate: () -> Unit,
    enableHaptics: Boolean
) {
    var dismissedUpdateEventId by remember { mutableStateOf<Long?>(null) }
    var dismissedPocketBookServerUpdateEventId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(UPDATE_PROGRESS_OVERLAY_MARGIN),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = appUpdateState.isDownloading,
            enter = fadeIn() + slideInVertically { height -> height },
            exit = fadeOut() + slideOutVertically { height -> height }
        ) {
            AppUpdateProgressOverlay(
                progress = appUpdateState.downloadProgress,
                enableHaptics = enableHaptics,
                onCancel = onCancelUpdateDownload
            )
        }
        AnimatedVisibility(
            visible = pocketBookServerUpdateState.isInstalling,
            enter = fadeIn() + slideInVertically { height -> height },
            exit = fadeOut() + slideOutVertically { height -> height }
        ) {
            PocketBookServerUpdateProgressOverlay(
                progress = pocketBookServerUpdateState.installProgress,
                enableHaptics = enableHaptics,
                onCancel = onCancelPocketBookServerUpdate
            )
        }
    }

    val updateStatus = appUpdateState.status
    if (updateStatus != null && dismissedUpdateEventId != appUpdateState.statusEventId) {
        AppUpdateDialog(
            status = updateStatus,
            enableHaptics = enableHaptics,
            loadChangelog = onLoadUpdateChangelog,
            onInstall = onInstallUpdate,
            onDismiss = { dismissedUpdateEventId = appUpdateState.statusEventId }
        )
    }

    val pocketBookServerStatus = pocketBookServerUpdateState.status
    if (
        pocketBookServerStatus != null &&
        dismissedPocketBookServerUpdateEventId != pocketBookServerUpdateState.statusEventId
    ) {
        PocketBookServerUpdateDialog(
            status = pocketBookServerStatus,
            enableHaptics = enableHaptics,
            loadChangelog = onLoadPocketBookServerUpdateChangelog,
            onInstall = onInstallPocketBookServerUpdate,
            onDismiss = {
                dismissedPocketBookServerUpdateEventId =
                    pocketBookServerUpdateState.statusEventId
            }
        )
    }
}

@Composable
private fun PocketBookServerUpdateDialog(
    status: PocketBookServerUpdateStatus,
    enableHaptics: Boolean,
    loadChangelog: suspend (AvailablePocketBookServerUpdate, String) -> String?,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val update = when (status) {
        is PocketBookServerUpdateStatus.UpdateAvailable -> status.update

        is PocketBookServerUpdateStatus.InstalledVersionUnknown -> status.update

        is PocketBookServerUpdateStatus.InstalledPendingRestart -> {
            PocketBookServerUpdateRestartDialog(
                update = status.update,
                enableHaptics = enableHaptics,
                onDismiss = onDismiss
            )
            return
        }

        else -> null
    } ?: return
    val isVersionUnknown = status is PocketBookServerUpdateStatus.InstalledVersionUnknown
    val languageCode = strings.languageCode

    UpdateAvailableDialog(
        versionCode = update.versionCode,
        title = strings.get("pb_server_update_dialog_title", update.versionName),
        body = if (isVersionUnknown) {
            strings.get(
                "pb_server_update_dialog_body_unknown",
                update.versionName,
                update.versionCode
            )
        } else {
            strings.get(
                "pb_server_update_dialog_body",
                update.versionName,
                update.versionCode
            )
        },
        confirmLabel = strings.get("pb_server_update_dialog_install"),
        dismissLabel = strings.updateDialogLater,
        enableHaptics = enableHaptics,
        hasChangelog = update.changelogUrlFor(languageCode) != null,
        loadChangelog = { code -> loadChangelog(update, code) },
        onConfirm = onInstall,
        onDismiss = onDismiss
    )
}

@Composable
private fun PocketBookServerUpdateRestartDialog(
    update: AvailablePocketBookServerUpdate,
    enableHaptics: Boolean,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val view = LocalView.current

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(strings.get("pb_server_update_restart_dialog_title", update.versionName))
        },
        text = {
            Text(strings.get("pb_server_update_restart_dialog_body", update.versionName))
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
                    dismiss()
                }
            ) {
                Text(strings.get("pb_server_update_restart_dialog_confirm"))
            }
        }
    )
}

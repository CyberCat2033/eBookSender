package com.cybercat.ebooksender.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cybercat.ebooksender.data.update.AppUpdateErrorReason
import com.cybercat.ebooksender.data.update.AppUpdateStatus
import com.cybercat.ebooksender.data.update.PocketBookServerUpdateErrorReason
import com.cybercat.ebooksender.data.update.PocketBookServerUpdateStatus
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.util.AppHapticFeedback
import com.cybercat.ebooksender.util.performHapticIfAllowed
import kotlinx.coroutines.delay

@Composable
internal fun MaintenanceSettingsSection(
    state: SettingsUiState,
    onCheckForUpdates: () -> Unit,
    onInstallUpdate: () -> Unit,
    onCheckPocketBookServerUpdates: () -> Unit,
    onInstallPocketBookServerUpdate: () -> Unit,
    onClearDownloadCache: () -> Unit,
    onLogoutAll: () -> Unit,
    onResetSettings: () -> Unit,
    onClearStatusMessage: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current

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
            Text(
                text = strings.get(
                    "settings_current_version",
                    state.appUpdateState.currentVersionName,
                    state.appUpdateState.currentVersionCode
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    view.performHapticIfAllowed(
                        context,
                        state.settings.enableHaptics,
                        AppHapticFeedback.Confirm
                    )
                    onCheckForUpdates()
                },
                enabled = !state.appUpdateState.isChecking &&
                    !state.appUpdateState.isDownloading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.appUpdateState.isChecking) {
                        strings.settingsCheckingUpdates
                    } else {
                        strings.settingsCheckUpdates
                    }
                )
            }

            state.appUpdateState.availableUpdate?.let { update ->
                Button(
                    onClick = {
                        view.performHapticIfAllowed(
                            context,
                            state.settings.enableHaptics,
                            AppHapticFeedback.Confirm
                        )
                        onInstallUpdate()
                    },
                    enabled = !state.appUpdateState.isChecking &&
                        !state.appUpdateState.isDownloading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.get("settings_install_update", update.versionName))
                }
            }

            val updateStatusText = state.appUpdateState.status?.toSettingsText(strings)
            if (updateStatusText != null) {
                Text(
                    text = updateStatusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state.isPocketBookConnected) {
                Text(
                    text = state.pocketBookServerUpdateState.installedVersion?.let { version ->
                        strings.get(
                            "pb_server_current_version",
                            version.versionName,
                            version.versionCode
                        )
                    } ?: strings.pbServerCurrentVersionUnknown,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        view.performHapticIfAllowed(
                            context,
                            state.settings.enableHaptics,
                            AppHapticFeedback.Confirm
                        )
                        onCheckPocketBookServerUpdates()
                    },
                    enabled = !state.pocketBookServerUpdateState.isChecking &&
                        !state.pocketBookServerUpdateState.isInstalling,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.SystemUpdate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.pocketBookServerUpdateState.isChecking) {
                            strings.pbServerCheckingUpdates
                        } else {
                            strings.pbServerCheckUpdates
                        }
                    )
                }

                state.pocketBookServerUpdateState.availableUpdate?.let { update ->
                    Button(
                        onClick = {
                            view.performHapticIfAllowed(
                                context,
                                state.settings.enableHaptics,
                                AppHapticFeedback.Confirm
                            )
                            onInstallPocketBookServerUpdate()
                        },
                        enabled = !state.pocketBookServerUpdateState.isChecking &&
                            !state.pocketBookServerUpdateState.isInstalling,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.get("pb_server_install_update", update.versionName))
                    }
                }

                val pocketBookServerStatusText =
                    state.pocketBookServerUpdateState.status?.toSettingsText(strings)
                if (pocketBookServerStatusText != null) {
                    Text(
                        text = pocketBookServerStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (
                            state.pocketBookServerUpdateState.status
                                is PocketBookServerUpdateStatus.Error
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Button(
                onClick = {
                    view.performHapticIfAllowed(
                        context,
                        state.settings.enableHaptics,
                        AppHapticFeedback.LongPress
                    )
                    onClearDownloadCache()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.settingsClearCache)
            }

            Button(
                onClick = {
                    view.performHapticIfAllowed(
                        context,
                        state.settings.enableHaptics,
                        AppHapticFeedback.LongPress
                    )
                    onLogoutAll()
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.settingsLogoutAll)
            }

            Button(
                onClick = {
                    view.performHapticIfAllowed(
                        context,
                        state.settings.enableHaptics,
                        AppHapticFeedback.LongPress
                    )
                    onResetSettings()
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Restore, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.settingsResetToDefaults)
            }

            val statusMessage = state.settingsStatusMessage
            AnimatedContent(
                targetState = statusMessage,
                transitionSpec = {
                    if (targetState != null) {
                        (
                            expandVertically(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                expandFrom = Alignment.Top
                            ) +
                                fadeIn(
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            )
                            .togetherWith(fadeOut())
                    } else {
                        fadeIn().togetherWith(
                            shrinkVertically(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                shrinkTowards = Alignment.Top
                            ) +
                                fadeOut(
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

                    val displayMessage = when (message) {
                        SettingsStatusMessage.FolderRenameNotSupported -> {
                            strings.settingsRenameNotSupported
                        }

                        is SettingsStatusMessage.Text -> message.value
                    }

                    Text(
                        text = displayMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp).fillMaxWidth()
                    )
                } else {
                    Box(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun AppUpdateStatus.toSettingsText(
    strings: com.cybercat.ebooksender.localization.AppStrings
): String? = when (this) {
    AppUpdateStatus.NoUpdateAvailable -> strings.updateNoUpdate

    is AppUpdateStatus.UpdateAvailable ->
        strings.get("settings_update_available", update.versionName)

    is AppUpdateStatus.Downloading -> strings.updateDialogDownloading

    AppUpdateStatus.DownloadCanceled -> strings.updateDownloadCanceled

    is AppUpdateStatus.ReadyToInstall ->
        strings.get("settings_update_available", update.versionName)

    is AppUpdateStatus.Error -> reason.toSettingsText(strings)
}

private fun AppUpdateErrorReason.toSettingsText(
    strings: com.cybercat.ebooksender.localization.AppStrings
): String = when (this) {
    AppUpdateErrorReason.Network -> strings.updateErrorNetwork
    AppUpdateErrorReason.InvalidManifest -> strings.updateErrorInvalidManifest
    AppUpdateErrorReason.NoCompatibleArtifact -> strings.updateErrorNoCompatibleArtifact
    AppUpdateErrorReason.DownloadFailed -> strings.updateErrorDownloadFailed
    AppUpdateErrorReason.ChecksumMismatch -> strings.updateErrorChecksumMismatch
    AppUpdateErrorReason.SignatureMismatch -> strings.updateErrorSignatureMismatch
    AppUpdateErrorReason.InstallUnavailable -> strings.updateErrorInstallUnavailable
    AppUpdateErrorReason.Unknown -> strings.updateErrorUnknown
}

private fun PocketBookServerUpdateStatus.toSettingsText(
    strings: com.cybercat.ebooksender.localization.AppStrings
): String? = when (this) {
    PocketBookServerUpdateStatus.NoPocketBookConnected -> strings.pbServerConnectRequired

    PocketBookServerUpdateStatus.NoUpdateAvailable -> strings.pbServerNoUpdate

    is PocketBookServerUpdateStatus.UpdateAvailable ->
        strings.get("pb_server_update_available", update.versionName)

    is PocketBookServerUpdateStatus.InstalledVersionUnknown ->
        strings.get("pb_server_update_available_unknown", update.versionName)

    is PocketBookServerUpdateStatus.Installing -> strings.pbServerInstallingUpdate

    is PocketBookServerUpdateStatus.Installed ->
        strings.get("pb_server_update_installed", version.versionName)

    PocketBookServerUpdateStatus.InstallCanceled -> strings.pbServerUpdateCanceled

    is PocketBookServerUpdateStatus.Error -> reason.toSettingsText(strings)
}

private fun PocketBookServerUpdateErrorReason.toSettingsText(
    strings: com.cybercat.ebooksender.localization.AppStrings
): String = when (this) {
    PocketBookServerUpdateErrorReason.Network -> strings.pbServerUpdateErrorNetwork

    PocketBookServerUpdateErrorReason.InvalidManifest ->
        strings.pbServerUpdateErrorInvalidManifest

    PocketBookServerUpdateErrorReason.MissingArtifacts ->
        strings.pbServerUpdateErrorMissingArtifacts

    PocketBookServerUpdateErrorReason.DownloadFailed ->
        strings.pbServerUpdateErrorDownloadFailed

    PocketBookServerUpdateErrorReason.ChecksumMismatch ->
        strings.pbServerUpdateErrorChecksumMismatch

    PocketBookServerUpdateErrorReason.UploadFailed ->
        strings.pbServerUpdateErrorUploadFailed

    PocketBookServerUpdateErrorReason.ApplyFailed ->
        strings.pbServerUpdateErrorApplyFailed

    PocketBookServerUpdateErrorReason.Unknown -> strings.pbServerUpdateErrorUnknown
}

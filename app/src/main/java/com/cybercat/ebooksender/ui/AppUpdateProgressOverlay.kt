package com.cybercat.ebooksender.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cybercat.ebooksender.data.update.AppUpdateDownloadProgress
import com.cybercat.ebooksender.localization.LocalStrings

@Composable
fun AppUpdateProgressOverlay(
    progress: AppUpdateDownloadProgress?,
    enableHaptics: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val fraction = progress?.fraction
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    ProgressOverlayCard(
        title = progressTitle(fraction),
        subtitle = strings.updateDownloadPreparing,
        progress = fraction,
        icon = Icons.Outlined.Download,
        cancelContentDescription = strings.updateDownloadCancel,
        enableHaptics = enableHaptics,
        onCancel = onCancel,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = contentColor,
        progressColor = contentColor,
        progressTrackColor = contentColor.copy(alpha = 0.24f)
    )
}

@Composable
private fun progressTitle(fraction: Float?): String {
    val strings = LocalStrings.current
    return fraction?.let {
        strings.get("update_download_progress_title", (it * 100).toInt().coerceIn(0, 100))
    } ?: strings.updateDialogDownloading
}

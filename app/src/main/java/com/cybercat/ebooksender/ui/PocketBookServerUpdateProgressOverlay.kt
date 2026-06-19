package com.cybercat.ebooksender.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cybercat.ebooksender.data.update.PocketBookServerUpdatePhase
import com.cybercat.ebooksender.data.update.PocketBookServerUpdateProgress
import com.cybercat.ebooksender.localization.LocalStrings

@Composable
fun PocketBookServerUpdateProgressOverlay(
    progress: PocketBookServerUpdateProgress?,
    enableHaptics: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val fraction = progress?.fraction
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    ProgressOverlayCard(
        title = fraction?.let {
            strings.get("pb_server_update_progress_title", (it * 100).toInt().coerceIn(0, 100))
        } ?: strings.pbServerInstallingUpdate,
        subtitle = when (progress?.phase) {
            PocketBookServerUpdatePhase.Downloading -> strings.pbServerUpdateDownloading
            PocketBookServerUpdatePhase.Uploading -> strings.pbServerUpdateUploading
            null -> strings.pbServerUpdatePreparing
        },
        progress = fraction,
        icon = Icons.Outlined.Download,
        cancelContentDescription = strings.pbServerUpdateCancel,
        enableHaptics = enableHaptics,
        onCancel = onCancel,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = contentColor,
        progressColor = contentColor,
        progressTrackColor = contentColor.copy(alpha = 0.24f)
    )
}

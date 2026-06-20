package com.cybercat.ebooksender.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cybercat.ebooksender.util.AppHapticFeedback
import com.cybercat.ebooksender.util.performHapticIfAllowed

@Composable
fun ProgressOverlayCard(
    title: String,
    progress: Float?,
    icon: ImageVector,
    cancelContentDescription: String,
    enableHaptics: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showSpinner: Boolean = progress == null,
    cancelEnabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    progressColor: Color = contentColor,
    progressTrackColor: Color = contentColor.copy(alpha = ProgressOverlayDefaults.TRACK_ALPHA),
    titleStyle: TextStyle = MaterialTheme.typography.titleSmall,
    titleFontWeight: FontWeight? = null,
    subtitleMaxLines: Int = 2,
    verticalSpacing: Dp = ProgressOverlayDefaults.VerticalSpacing,
    trailingContent: @Composable RowScope.() -> Unit = {},
    footerContent: @Composable ColumnScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = ProgressOverlayDefaults.MaxWidth),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = ProgressOverlayDefaults.Elevation,
        shadowElevation = ProgressOverlayDefaults.Elevation
    ) {
        Column(
            modifier = Modifier.padding(ProgressOverlayDefaults.ContentPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ProgressOverlayDefaults.RowSpacing)
            ) {
                if (showSpinner) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ProgressOverlayDefaults.LeadingIconSize),
                        color = contentColor,
                        strokeWidth = ProgressOverlayDefaults.SpinnerStrokeWidth
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(ProgressOverlayDefaults.LeadingIconSize),
                        tint = contentColor
                    )
                }
                Column(Modifier.weight(1f)) {
                    SingleLineMarqueeText(
                        text = title,
                        style = titleStyle,
                        fontWeight = titleFontWeight,
                        color = contentColor
                    )
                    subtitle?.let {
                        if (subtitleMaxLines == 1) {
                            SingleLineMarqueeText(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor
                            )
                        } else {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor,
                                maxLines = subtitleMaxLines,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                trailingContent()
                OutlinedIconButton(
                    onClick = {
                        view.performHapticIfAllowed(
                            context,
                            enableHaptics,
                            AppHapticFeedback.Reject
                        )
                        onCancel()
                    },
                    modifier = Modifier.size(ProgressOverlayDefaults.CancelButtonSize),
                    enabled = cancelEnabled
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = cancelContentDescription,
                        tint = if (cancelEnabled) {
                            contentColor
                        } else {
                            contentColor.copy(alpha = ProgressOverlayDefaults.DISABLED_ALPHA)
                        }
                    )
                }
            }

            AnimatedLinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                color = progressColor,
                trackColor = progressTrackColor
            )

            footerContent()
        }
    }
}

@Composable
fun AnimatedLinearProgressIndicator(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    label: String = "AnimatedLinearProgress"
) {
    val safeProgress = progress?.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = safeProgress ?: 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = label
    )

    if (safeProgress == null) {
        LinearProgressIndicator(
            modifier = modifier,
            color = color,
            trackColor = trackColor
        )
    } else {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = modifier,
            color = color,
            trackColor = trackColor
        )
    }
}

object ProgressOverlayDefaults {
    val MaxWidth = 560.dp
    val ContentPadding = 14.dp
    val RowSpacing = 12.dp
    val VerticalSpacing = 10.dp
    val LeadingIconSize = 24.dp
    val CancelButtonSize = 48.dp
    val SpinnerStrokeWidth = 3.dp
    val Elevation = 8.dp
    const val TRACK_ALPHA = 0.24f
    const val DISABLED_ALPHA = 0.38f
}

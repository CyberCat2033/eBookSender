package com.cybercat.pocketbooksender.feature.settings

import android.view.HapticFeedbackConstants
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
import com.cybercat.pocketbooksender.localization.LocalStrings
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import kotlinx.coroutines.delay

@Composable
internal fun MaintenanceSettingsSection(
    state: SettingsUiState,
    onClearDownloadCache: () -> Unit,
    onLogoutAll: () -> Unit,
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
            Button(
                onClick = {
                    view.performHapticIfAllowed(
                        context,
                        state.settings.enableHaptics,
                        HapticFeedbackConstants.LONG_PRESS
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
                        HapticFeedbackConstants.LONG_PRESS
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

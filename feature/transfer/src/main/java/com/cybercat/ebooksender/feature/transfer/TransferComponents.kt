package com.cybercat.ebooksender.feature.transfer

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.model.BookCategory
import com.cybercat.ebooksender.model.UploadItem
import com.cybercat.ebooksender.model.UploadStatus
import com.cybercat.ebooksender.ui.AdaptiveSingleLineText
import com.cybercat.ebooksender.ui.AnimatedAlertDialog
import com.cybercat.ebooksender.ui.AppOutlinedTextField
import com.cybercat.ebooksender.ui.LocalDismissDialog
import com.cybercat.ebooksender.ui.ProgressOverlayCard
import com.cybercat.ebooksender.ui.SingleLineMarqueeText
import com.cybercat.ebooksender.util.AppHapticFeedback
import com.cybercat.ebooksender.util.performHapticIfAllowed
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
fun ConnectionPanel(
    state: TransferUiState,
    onFtpInputChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onQrScanned: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val iconTint by animateColorAsState(
        targetValue = if (state.isConnected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 220),
        label = "ConnectionIconTint"
    )
    val disconnectButtonAlpha by animateFloatAsState(
        targetValue = if (state.isConnected) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "DisconnectButtonAlpha"
    )
    val headerTitle = when {
        state.isConnected -> strings.sendMsgConnected
        state.isConnecting -> strings.sendStatusCheckingFtp
        else -> strings.sendHeaderConnectDevice
    }
    val headerSubtitle = state.connectedDevice?.ftpUrl ?: strings.sendScanQrDesc

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.WifiTethering,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = iconTint
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = headerSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            view.performHapticIfAllowed(
                                context,
                                state.settings.enableHaptics,
                                AppHapticFeedback.Reject
                            )
                            onDisconnect()
                        },
                        enabled = state.isConnected,
                        modifier = Modifier.alpha(disconnectButtonAlpha)
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = if (state.isConnected) {
                                strings.sendBtnDisconnect
                            } else {
                                null
                            }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !state.isConnected,
                enter = expandVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    expandFrom = Alignment.Top
                ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AppOutlinedTextField(
                        value = state.ftpInput,
                        onValueChange = onFtpInputChanged,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(strings.sendLabelFtp) },
                        leadingIcon = {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                        },
                        trailingIcon = {
                            if (state.ftpInput.isNotEmpty()) {
                                IconButton(onClick = {
                                    view.performHapticIfAllowed(
                                        context,
                                        state.settings.enableHaptics,
                                        AppHapticFeedback.Press
                                    )
                                    onFtpInputChanged("")
                                }) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = strings.get("action_clear")
                                    )
                                }
                            }
                        },
                        placeholderText = strings.sendPlaceholderFtp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                view.performHapticIfAllowed(
                                    context,
                                    state.settings.enableHaptics,
                                    AppHapticFeedback.Press
                                )
                                startQrScan(context, onQrScanned)
                            },
                            enabled = !state.isConnecting,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.sendBtnScanQr)
                        }
                        Button(
                            onClick = {
                                view.performHapticIfAllowed(
                                    context,
                                    state.settings.enableHaptics,
                                    AppHapticFeedback.Confirm
                                )
                                if (state.ftpInput.isBlank()) {
                                    startQrScan(context, onQrScanned)
                                } else {
                                    onConnect()
                                }
                            },
                            enabled = !state.isConnecting,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Outlined.WifiTethering, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            AdaptiveSingleLineText(
                                text = if (state.isConnecting) {
                                    strings.sendStatusChecking
                                } else {
                                    strings.sendBtnConnect
                                },
                                compactText = if (state.isConnecting) {
                                    strings.sendStatusChecking
                                } else {
                                    strings.sendBtnConnectCompact
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

data class QueueRow(val key: String, val item: UploadItem)

fun List<UploadItem>.withStableLazyKeys(): List<QueueRow> {
    val seen = mutableMapOf<String, Int>()
    return mapIndexed { index, item ->
        val baseKey = "queue:${item.id}:${item.sourceUri}"
        val duplicateIndex = seen.getOrDefault(baseKey, 0)
        seen[baseKey] = duplicateIndex + 1
        QueueRow(
            key = if (duplicateIndex == 0) {
                baseKey
            } else {
                "$baseKey:duplicate:$duplicateIndex:$index"
            },
            item = item
        )
    }
}

private fun startQrScan(context: Context, onQrScanned: (String) -> Unit) {
    GmsBarcodeScanning.getClient(context)
        .startScan()
        .addOnSuccessListener { barcode ->
            barcode.rawValue?.let(onQrScanned)
        }
}

@Composable
fun ActionRow(
    canAddFiles: Boolean,
    canUpload: Boolean,
    enableHaptics: Boolean,
    onAddFiles: () -> Unit,
    onUploadAll: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = {
                view.performHapticIfAllowed(
                    context,
                    enableHaptics,
                    AppHapticFeedback.Press
                )
                onAddFiles()
            },
            enabled = canAddFiles,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            AdaptiveSingleLineText(
                text = strings.sendBtnAddFiles,
                compactText = strings.sendBtnAddFilesCompact
            )
        }
        Button(
            onClick = {
                view.performHapticIfAllowed(context, enableHaptics, AppHapticFeedback.Confirm)
                onUploadAll()
            },
            enabled = canUpload,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Icon(Icons.Outlined.Upload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(strings.sendBtnUpload)
        }
    }
}

@Composable
fun QueueHeader(count: Int, canBatchRenameManga: Boolean, onBatchRenameManga: () -> Unit) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = strings.sendHeaderQueue,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = count.toString(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(Modifier.weight(1f))
        if (canBatchRenameManga) {
            IconButton(onClick = onBatchRenameManga) {
                Icon(Icons.Outlined.Edit, contentDescription = strings.sendRenameMangaTitle)
            }
        }
    }
}

@Composable
fun EmptyQueue() {
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = strings.sendMsgNoFiles,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = strings.sendLabelAddBooksDesc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun UploadedSection(
    items: List<UploadItem>,
    enableHaptics: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = strings.sendUploadedHeader,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = strings.get("send_uploaded_books_count", items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val rotationState by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "ChevronRotation"
                )
                IconButton(onClick = {
                    expanded = !expanded
                    view.performHapticIfAllowed(
                        context,
                        enableHaptics,
                        AppHapticFeedback.Press
                    )
                }) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) {
                            strings.sendBtnCollapseUploaded
                        } else {
                            strings.sendBtnShowUploaded
                        },
                        modifier = Modifier.rotate(rotationState)
                    )
                }
            }

            val animationDuration = remember(items.size) {
                minOf(750, 250 + (items.size * 35))
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = animationDuration),
                    expandFrom = Alignment.Top
                ) + fadeIn(tween(durationMillis = animationDuration)),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = animationDuration),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(tween(durationMillis = animationDuration))
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEach { item ->
                        Column {
                            SingleLineMarqueeText(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            SingleLineMarqueeText(
                                text = item.plannedPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadProgressOverlay(
    queue: List<UploadItem>,
    currentUploadItemId: String?,
    currentUploadProgress: Float,
    isCanceling: Boolean,
    enableHaptics: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentItem = currentUploadItemId?.let { itemId ->
        queue.firstOrNull { item -> item.id == itemId }
    } ?: queue.firstOrNull { item -> item.status == UploadStatus.Uploading }
    val uploadedCount = queue.count { it.status == UploadStatus.Uploaded }
    val failedCount = queue.count { it.status == UploadStatus.Failed }
    val totalCount = queue.size

    if (totalCount == 0) return

    val activeProgress = if (currentItem?.status == UploadStatus.Uploading) {
        currentUploadProgress.coerceIn(0f, 1f)
    } else {
        0f
    }
    val overallProgress = (uploadedCount + activeProgress) / totalCount

    val strings = LocalStrings.current
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

    ProgressOverlayCard(
        title = when {
            isCanceling -> strings.get("send_upload_canceling")
            uploadedCount == totalCount -> strings.sendUploadComplete
            else -> strings.sendSendingStatus
        },
        progress = overallProgress,
        icon = Icons.Outlined.Upload,
        showSpinner = isCanceling,
        cancelContentDescription = strings.get("send_upload_cancel"),
        cancelEnabled = !isCanceling,
        enableHaptics = enableHaptics,
        onCancel = onCancel,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = contentColor,
        progressColor = MaterialTheme.colorScheme.primary,
        progressTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
        titleStyle = MaterialTheme.typography.titleMedium,
        titleFontWeight = FontWeight.Bold,
        verticalSpacing = 8.dp,
        trailingContent = {
            Text(
                text = "${(overallProgress * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    ) {
        Text(
            text = if (failedCount > 0) {
                strings.get(
                    "send_progress_detail_failed",
                    uploadedCount,
                    totalCount,
                    failedCount
                )
            } else {
                strings.get("send_progress_detail", uploadedCount, totalCount)
            },
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.8f)
        )
        if (currentItem != null) {
            SingleLineMarqueeText(
                text = currentItem.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
    }
}

val QueueFadeInSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

val QueueFadeOutSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

val QueuePlacementSpec = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

fun queueClearDelayMillis(rowCount: Int): Long {
    if (rowCount <= 0) return 0L
    val stagger =
        minOf(rowCount - 1, QUEUE_CLEAR_MAX_STAGGERED_ROWS).toLong() * QUEUE_CLEAR_STAGGER_MILLIS
    return stagger + QUEUE_CLEAR_EXIT_SETTLE_MILLIS
}

const val QUEUE_CLEAR_MAX_STAGGERED_ROWS = 8
const val QUEUE_CLEAR_STAGGER_MILLIS = 45L
const val QUEUE_SINGLE_REMOVE_MILLIS = 560L
const val QUEUE_CLEAR_EXIT_SETTLE_MILLIS = QUEUE_SINGLE_REMOVE_MILLIS + 120L

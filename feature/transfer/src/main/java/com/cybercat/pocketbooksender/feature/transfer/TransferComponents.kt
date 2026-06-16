package com.cybercat.pocketbooksender.feature.transfer

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.cybercat.pocketbooksender.localization.LocalStrings
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.UploadItem
import com.cybercat.pocketbooksender.model.UploadStatus
import com.cybercat.pocketbooksender.ui.AnimatedAlertDialog
import com.cybercat.pocketbooksender.ui.LocalDismissDialog
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
fun MangaBatchEditorDialog(
    activeMangaQueue: List<UploadItem>,
    suggestions: List<String>,
    enableHaptics: Boolean,
    onDismiss: () -> Unit,
    onApply: (String?, String) -> Unit,
) {
    val uniqueSeries = remember(activeMangaQueue) {
        activeMangaQueue.mapNotNull { item -> item.mangaSeries?.takeIf(String::isNotBlank) }
            .distinctBy { it.lowercase() }
    }

    var targetSeries by remember { mutableStateOf<String?>(uniqueSeries.firstOrNull()) }
    var series by remember(targetSeries) { mutableStateOf(targetSeries ?: "") }

    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.sendRenameMangaTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (uniqueSeries.size > 1) {
                    Text(
                        text = strings.mangaUpdatesSelectSeriesToRename,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = targetSeries == null,
                            onClick = {
                                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                targetSeries = null
                            },
                            label = { Text(strings.mangaUpdatesAllSeries) }
                        )
                        uniqueSeries.forEach { s ->
                            FilterChip(
                                selected = targetSeries == s,
                                onClick = {
                                    view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                    targetSeries = s
                                },
                                label = { Text(s) }
                            )
                        }
                    }
                }

                Text(
                    text = if (targetSeries == null) {
                        strings.get("send_batch_rename_desc", activeMangaQueue.size)
                    } else {
                        val countForTarget = activeMangaQueue.count { it.mangaSeries?.equals(targetSeries, ignoreCase = true) == true }
                        strings.get("manga_updates_rename_desc", countForTarget, targetSeries ?: "")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MangaSeriesRenamePanel(
                    selectedSeries = series,
                    suggestions = suggestions,
                    onSeriesChanged = { series = it },
                    onSuggestionSelected = { suggestion ->
                        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                        series = suggestion
                    },
                )
            }
        },
        confirmButton = {
            val dismiss = LocalDismissDialog.current
            TextButton(
                onClick = {
                    view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.CONFIRM)
                    onApply(targetSeries, series.trim())
                    dismiss()
                },
                enabled = series.isNotBlank(),
            ) {
                Text(strings.sendRenameMangaApply)
            }
        },
        dismissButton = {
            val dismiss = LocalDismissDialog.current
            TextButton(onClick = {
                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                dismiss()
            }) {
                Text(strings.sendRenameMangaCancel)
            }
        },
    )
}

@Composable
internal fun MangaSeriesRenamePanel(
    selectedSeries: String,
    suggestions: List<String>,
    onSeriesChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSuggestionSelected: (String) -> Unit = onSeriesChanged,
) {
    val strings = LocalStrings.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = selectedSeries,
            onValueChange = onSeriesChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(strings.sendRenameMangaSeries) },
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .forEach { suggestion ->
                    FilterChip(
                        selected = selectedSeries.equals(suggestion, ignoreCase = true),
                        onClick = { onSuggestionSelected(suggestion) },
                        label = { Text(suggestion) },
                    )
                }
        }
    }
}

@Composable
fun ConnectionPanel(
    state: TransferUiState,
    onFtpInputChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onQrScanned: (String) -> Unit,
    onDisconnect: () -> Unit,
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
        label = "ConnectionIconTint",
    )
    val disconnectButtonAlpha by animateFloatAsState(
        targetValue = if (state.isConnected) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "DisconnectButtonAlpha",
    )
    val headerTitle = when {
        state.isConnected -> strings.sendMsgConnected
        state.isConnecting -> strings.sendStatusCheckingFtp
        else -> strings.sendHeaderConnectPocketbook
    }
    val headerSubtitle = state.connectedDevice?.ftpUrl ?: strings.sendScanQrDesc

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.WifiTethering,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = iconTint,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = headerSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = {
                            view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.REJECT)
                            onDisconnect()
                        },
                        enabled = state.isConnected,
                        modifier = Modifier.alpha(disconnectButtonAlpha),
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = if (state.isConnected) strings.sendBtnDisconnect else null,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !state.isConnected,
                enter = expandVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    expandFrom = Alignment.Top,
                ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
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
                                    view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                    onFtpInputChanged("")
                                }) {
                                    Icon(Icons.Outlined.Close, contentDescription = strings.get("action_clear"))
                                }
                            }
                        },
                        placeholder = { Text(strings.sendPlaceholderFtp) },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                startQrScan(context, onQrScanned)
                            },
                            enabled = !state.isConnecting,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.sendBtnScanQr)
                        }
                        Button(
                            onClick = {
                                view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.CONFIRM)
                                if (state.ftpInput.isBlank()) {
                                    startQrScan(context, onQrScanned)
                                } else {
                                    onConnect()
                                }
                            },
                            enabled = !state.isConnecting,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Icon(Icons.Outlined.WifiTethering, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.isConnecting) strings.sendStatusChecking else strings.sendBtnConnect)
                        }
                    }
                }
            }
        }
    }
}

data class QueueRow(
    val key: String,
    val item: UploadItem,
)

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
            item = item,
        )
    }
}

private fun startQrScan(
    context: Context,
    onQrScanned: (String) -> Unit,
) {
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
    onUploadAll: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = {
                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                onAddFiles()
            },
            enabled = canAddFiles,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(strings.sendBtnAddFiles)
        }
        Button(
            onClick = {
                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.CONFIRM)
                onUploadAll()
            },
            enabled = canUpload,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Icon(Icons.Outlined.Upload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(strings.sendBtnUpload)
        }
    }
}

@Composable
fun QueueHeader(
    count: Int,
    canBatchRenameManga: Boolean,
    onBatchRenameManga: () -> Unit,
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = strings.sendHeaderQueue,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = count.toString(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = strings.sendMsgNoFiles,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = strings.sendLabelAddBooksDesc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun UploadedSection(
    items: List<UploadItem>,
    enableHaptics: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = strings.sendUploadedHeader,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = strings.get("send_uploaded_books_count", items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val rotationState by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "ChevronRotation"
                )
                IconButton(onClick = {
                    expanded = !expanded
                    view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                }) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) strings.sendBtnCollapseUploaded else strings.sendBtnShowUploaded,
                        modifier = Modifier.rotate(rotationState)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    expandFrom = Alignment.Top,
                ) + fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
                exit = shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(spring(stiffness = Spring.StiffnessMediumLow)),
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEach { item ->
                        Column {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.plannedPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
    modifier: Modifier = Modifier,
) {
    val uploadingItems = queue.filter { it.status == UploadStatus.Uploading }
    val uploadedCount = queue.count { it.status == UploadStatus.Uploaded }
    val failedCount = queue.count { it.status == UploadStatus.Failed }
    val totalCount = queue.size

    if (totalCount == 0) return

    val activeProgressSum = uploadingItems.sumOf { it.progress.toDouble() }.toFloat()
    val overallProgress = (uploadedCount + activeProgressSum) / totalCount

    val animatedProgress by animateFloatAsState(
        targetValue = overallProgress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "OverallProgress"
    )
    val strings = LocalStrings.current
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val currentItem = uploadingItems.firstOrNull()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = contentColor,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uploadedCount == totalCount) strings.sendUploadComplete else strings.sendSendingStatus,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(overallProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            )

            Text(
                text = if (failedCount > 0) {
                    strings.get("send_progress_detail_failed", uploadedCount, totalCount, failedCount)
                } else {
                    strings.get("send_progress_detail", uploadedCount, totalCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.8f),
            )
            if (currentItem != null) {
                Text(
                    text = currentItem.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
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
    val stagger = minOf(rowCount - 1, QueueClearMaxStaggeredRows).toLong() * QueueClearStaggerMillis
    return stagger + QueueClearExitSettleMillis
}

const val QueueClearMaxStaggeredRows = 8
const val QueueClearStaggerMillis = 45L
const val QueueSingleRemoveMillis = 560L
const val QueueClearExitSettleMillis = QueueSingleRemoveMillis + 120L

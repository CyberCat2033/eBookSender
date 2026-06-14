package com.cybercat.pocketbooksender.ui.screens

import android.net.Uri
import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.UploadItem
import com.cybercat.pocketbooksender.model.UploadStatus
import com.cybercat.pocketbooksender.ui.TransferUiState
import com.cybercat.pocketbooksender.localization.LocalStrings
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    state: TransferUiState,
    listState: LazyListState,
    onFtpInputChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onQrScanned: (String) -> Unit,
    onDisconnect: () -> Unit,
    onAddUris: (List<Uri>) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearQueue: () -> Unit,
    onCategoryChanged: (String, BookCategory) -> Unit,
    onDocumentsTagChanged: (String, String) -> Unit,
    onMangaSeriesChanged: (String, String) -> Unit,
    onQueuedMangaSeriesChanged: (String) -> Unit,
    onUploadAll: () -> Unit,
) {
    val strings = LocalStrings.current
    var clearTrigger by remember { mutableStateOf(0) }
    var clearInProgress by remember { mutableStateOf(false) }
    var clearAnimatedRowCount by remember { mutableStateOf(0) }
    val queue = state.queue
    val activeTransferItemIds = state.activeTransferItemIds
    val activeQueue = remember(queue, state.isTransferActive, activeTransferItemIds) {
        if (state.isTransferActive && activeTransferItemIds.isNotEmpty()) {
            queue.filter { item ->
                item.status != UploadStatus.Uploaded || item.id in activeTransferItemIds
            }
        } else {
            queue.filterNot { it.status == UploadStatus.Uploaded }
        }
    }
    val activeRows = remember(activeQueue) { activeQueue.withStableLazyKeys() }
    val activeMangaQueue = remember(activeQueue) {
        activeQueue.filter { it.category == BookCategory.Manga }
    }
    val canBatchRenameManga = activeMangaQueue.size > 1 && !state.isTransferActive && !clearInProgress
    var showMangaBatchEditor by remember { mutableStateOf(false) }
    val uploadedQueue = remember(queue, state.isTransferActive) {
        if (state.isTransferActive) {
            emptyList()
        } else {
            queue.filter { it.status == UploadStatus.Uploaded }
        }
    }
    val animatedUploadedSectionCount = if (uploadedQueue.isNotEmpty()) 1 else 0
    val hasUploadableFiles = remember(queue) {
        queue.any {
            it.status == UploadStatus.Pending ||
                it.status == UploadStatus.Failed ||
                it.status == UploadStatus.Skipped
        }
    }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = onAddUris,
    )

    LaunchedEffect(canBatchRenameManga) {
        if (!canBatchRenameManga) {
            showMangaBatchEditor = false
        }
    }

    if (showMangaBatchEditor && canBatchRenameManga) {
        MangaBatchEditorDialog(
            count = activeMangaQueue.size,
            currentSeries = activeMangaQueue.commonMangaSeries(),
            suggestions = state.mangaSeriesSuggestions,
            onDismiss = { showMangaBatchEditor = false },
            onApply = { series ->
                onQueuedMangaSeriesChanged(series)
                showMangaBatchEditor = false
            },
        )
    }

    LaunchedEffect(clearTrigger) {
        if (clearTrigger > 0) {
            delay(queueClearDelayMillis(clearAnimatedRowCount))
            onClearQueue()
            clearInProgress = false
        }
    }

    LaunchedEffect(state.queue.isEmpty()) {
        if (state.queue.isEmpty()) {
            clearInProgress = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.appName) },
                windowInsets = WindowInsets(0.dp),
                actions = {
                    if (state.queue.isNotEmpty()) {
                        val context = LocalContext.current
                        val view = LocalView.current
                        IconButton(
                            onClick = {
                                if (!clearInProgress && !state.isTransferActive) {
                                    view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.LONG_PRESS)
                                    clearAnimatedRowCount = animatedUploadedSectionCount + activeRows.size
                                    clearInProgress = true
                                    clearTrigger++
                                }
                            },
                            enabled = !clearInProgress && !state.isTransferActive,
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = strings.sendBtnClearQueue)
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ConnectionPanel(
                        state = state,
                        onFtpInputChanged = onFtpInputChanged,
                        onConnect = onConnect,
                        onQrScanned = onQrScanned,
                        onDisconnect = onDisconnect,
                    )
                }

                item {
                    ActionRow(
                        canAddFiles = !clearInProgress,
                        canUpload = state.isConnected && hasUploadableFiles && !state.isTransferActive && !clearInProgress,
                        enableHaptics = state.settings.enableHaptics,
                        onAddFiles = { picker.launch(arrayOf("*/*")) },
                        onUploadAll = onUploadAll,
                    )
                }

                if (uploadedQueue.isNotEmpty()) {
                    item(key = "uploaded-section") {
                        AnimatedRemovalItem(
                            clearTrigger = clearTrigger,
                            clearInProgress = clearInProgress,
                            staggerIndex = 0,
                            modifier = Modifier.animateItem(
                                fadeInSpec = QueueFadeInSpec,
                                fadeOutSpec = QueueFadeOutSpec,
                                placementSpec = QueuePlacementSpec
                            ),
                            onRemoved = {},
                        ) {
                            UploadedSection(
                                items = uploadedQueue,
                                enableHaptics = state.settings.enableHaptics,
                            )
                        }
                    }
                }

                if (state.errorMessage != null) {
                    item {
                        Text(
                            text = state.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                item {
                    QueueHeader(
                        count = activeQueue.size,
                        canBatchRenameManga = canBatchRenameManga,
                        onBatchRenameManga = { showMangaBatchEditor = true },
                    )
                }

                if (activeQueue.isEmpty()) {
                    item {
                        EmptyQueue()
                    }
                } else {
                    itemsIndexed(
                        items = activeRows,
                        key = { _, row -> row.key },
                        contentType = { _, _ -> "upload_item" }
                    ) { index, row ->
                        val item = row.item
                        AnimatedRemovalItem(
                            clearTrigger = clearTrigger,
                            clearInProgress = clearInProgress,
                            staggerIndex = animatedUploadedSectionCount + index,
                            modifier = Modifier.animateItem(
                                fadeInSpec = QueueFadeInSpec,
                                fadeOutSpec = QueueFadeOutSpec,
                                placementSpec = QueuePlacementSpec
                            ),
                            onRemoved = { onRemoveItem(item.id) },
                        ) { triggerRemove ->
                            UploadItemRow(
                                item = item,
                                documentsTags = state.documentsTags,
                                mangaSeriesSuggestions = state.mangaSeriesSuggestions,
                                enableHaptics = state.settings.enableHaptics,
                                settings = state.settings,
                                onRemove = triggerRemove,
                                onCategoryChanged = { category -> onCategoryChanged(item.id, category) },
                                onDocumentsTagChanged = { tag -> onDocumentsTagChanged(item.id, tag) },
                                onMangaSeriesChanged = { series -> onMangaSeriesChanged(item.id, series) },
                            )
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(if (state.isTransferActive) 112.dp else 8.dp))
                }
            }

            AnimatedVisibility(
                visible = state.isTransferActive,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                enter = fadeIn() + slideInVertically { height -> height / 2 },
                exit = fadeOut() + slideOutVertically { height -> height / 2 },
            ) {
                val transferQueue = remember(state.queue, state.activeTransferItemIds) {
                    state.queue.filter { item -> item.id in state.activeTransferItemIds }
                }
                UploadProgressOverlay(queue = transferQueue)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Анимация удаления: slide вправо + collapse + fade
// Режим каскада (clearTrigger): все элементы анимируются без вызова onRemoved —
// ViewModel не трогается во время анимации, что избегает recomposition-помех.
// onClearQueue() вызывается один раз из SendScreen после последней exit-анимации.
// Режим одиночного удаления: onRemoved() вызывается после окончания анимации.
// ---------------------------------------------------------------------------
@Composable
private fun AnimatedRemovalItem(
    modifier: Modifier = Modifier,
    clearTrigger: Int = 0,
    clearInProgress: Boolean = false,
    staggerIndex: Int = 0,
    onRemoved: () -> Unit,
    content: @Composable (() -> Unit) -> Unit,
) {
    var visible by remember { mutableStateOf(!clearInProgress) }
    var lastSeenClearTrigger by remember { mutableStateOf(clearTrigger) }
    var triggeredByClear by remember { mutableStateOf(clearInProgress) }

    LaunchedEffect(clearTrigger, clearInProgress) {
        if (clearTrigger > lastSeenClearTrigger && visible && clearInProgress) {
            lastSeenClearTrigger = clearTrigger
            delay(minOf(staggerIndex, QueueClearMaxStaggeredRows).toLong() * QueueClearStaggerMillis)
            triggeredByClear = true
            visible = false
        } else if (clearInProgress && !visible) {
            triggeredByClear = true
        }
    }

    // Одиночное удаление: ждём конца анимации, затем уведомляем родителя.
    LaunchedEffect(visible) {
        if (!visible && !triggeredByClear) {
            delay(QueueSingleRemoveMillis)
            onRemoved()
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = slideOutHorizontally(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            targetOffsetX = { it },
        ) + shrinkVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            shrinkTowards = Alignment.Top,
        ) + fadeOut(tween(300)),
    ) {
        content { visible = false }
    }
}

@Composable
private fun MangaBatchEditorDialog(
    count: Int,
    currentSeries: String,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var series by remember(currentSeries) { mutableStateOf(currentSeries) }

    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.sendRenameMangaTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = strings.get("send_batch_rename_desc", count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = series,
                    onValueChange = { series = it },
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
                                selected = series.equals(suggestion, ignoreCase = true),
                                onClick = { series = suggestion },
                                label = { Text(suggestion) },
                            )
                        }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(series.trim()) },
                enabled = series.isNotBlank(),
            ) {
                Text(strings.sendRenameMangaApply)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.sendRenameMangaCancel)
            }
        },
    )
}

@Composable
private fun ConnectionPanel(
    state: TransferUiState,
    onFtpInputChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onQrScanned: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.WifiTethering,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (state.isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            state.isConnected -> strings.sendMsgConnected
                            state.isConnecting -> strings.sendStatusCheckingFtp
                            else -> strings.sendHeaderConnectPocketbook
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = state.connectedDevice?.ftpUrl ?: strings.sendScanQrDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.isConnected) {
                    IconButton(onClick = {
                        view.performHapticIfAllowed(context, state.settings.enableHaptics, HapticFeedbackConstants.REJECT)
                        onDisconnect()
                    }) {
                        Icon(Icons.Outlined.Close, contentDescription = strings.sendBtnDisconnect)
                    }
                }
            }

            if (!state.isConnected) {
                OutlinedTextField(
                    value = state.ftpInput,
                    onValueChange = onFtpInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.sendLabelFtp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
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

private data class QueueRow(
    val key: String,
    val item: UploadItem,
)

private fun List<UploadItem>.withStableLazyKeys(): List<QueueRow> {
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
private fun ActionRow(
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
private fun QueueHeader(
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
private fun EmptyQueue() {
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
private fun UploadItemRow(
    item: UploadItem,
    modifier: Modifier = Modifier,
    documentsTags: List<String>,
    mangaSeriesSuggestions: List<String>,
    enableHaptics: Boolean,
    settings: com.cybercat.pocketbooksender.model.AppSettings,
    onRemove: () -> Unit,
    onCategoryChanged: (BookCategory) -> Unit,
    onDocumentsTagChanged: (String) -> Unit,
    onMangaSeriesChanged: (String) -> Unit,
) {
    var detailsExpanded by remember(item.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current

    ElevatedCard(modifier.fillMaxWidth()) {
        Column {
            Column(
                modifier = Modifier.padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BookCover(item)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.originalName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = {
                            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.REJECT)
                            onRemove()
                        },
                        enabled = item.status != UploadStatus.Uploading && item.status != UploadStatus.Uploaded,
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = strings.sendBtnRemove)
                    }
                }

                Spacer(Modifier.height(12.dp))

                ItemTypeSummary(
                    item = item,
                    expanded = detailsExpanded,
                    onToggle = {
                        detailsExpanded = !detailsExpanded
                        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    settings = settings,
                )

                // Отступ top=12.dp помещён ВНУТРИ AnimatedVisibility: он схлопывается
                // вместе с контентом — нет резкого прыжка до разделителя.
                AnimatedVisibility(
                    visible = detailsExpanded,
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
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CategorySelector(
                            selected = item.category,
                            lockedToManga = item.extension == "cbr" || item.extension == "cbz",
                            onCategoryChanged = { cat ->
                                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                onCategoryChanged(cat)
                            },
                            settings = settings,
                        )

                        if (item.category == BookCategory.Documents) {
                            DocumentsTagEditor(
                                selectedTag = item.documentsTag.orEmpty(),
                                suggestions = documentsTags,
                                onTagChanged = onDocumentsTagChanged,
                                categoryName = settings.documentsFolderName,
                            )
                        }

                        if (item.category == BookCategory.Manga) {
                            MangaSeriesEditor(
                                selectedSeries = item.mangaSeries.orEmpty(),
                                suggestions = mangaSeriesSuggestions,
                                onSeriesChanged = onMangaSeriesChanged,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.plannedPath,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))

                    val statusColor = when (item.status) {
                        UploadStatus.Uploaded -> MaterialTheme.colorScheme.primary
                        UploadStatus.Failed -> MaterialTheme.colorScheme.error
                        UploadStatus.Uploading -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val statusText = when (item.status) {
                        UploadStatus.Uploading -> strings.get("send_status_uploading", (item.progress * 100).toInt())
                        UploadStatus.Pending -> strings.sendStatusPending
                        UploadStatus.Preparing -> strings.sendStatusPreparing
                        UploadStatus.Uploaded -> strings.sendStatusUploaded
                        UploadStatus.Failed -> strings.sendStatusFailed
                        UploadStatus.Skipped -> strings.sendStatusSkipped
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                }
            }

            val isUploading = item.status == UploadStatus.Uploading
            val progressHeight by animateDpAsState(
                targetValue = if (isUploading) 4.dp else 0.dp,
                label = "ProgressHeight"
            )

            if (progressHeight > 0.dp) {
                SmoothProgressIndicator(
                    progress = item.progress,
                    modifier = Modifier.fillMaxWidth().height(progressHeight),
                )
            }
        }
    }
}

@Composable
private fun SmoothProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "SmoothProgress"
    )
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier,
    )
}

@Composable
private fun UploadedSection(
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

            // Здесь нет элементов после AnimatedVisibility — gap не прыгает.
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
private fun ItemTypeSummary(
    item: UploadItem,
    expanded: Boolean,
    onToggle: () -> Unit,
    settings: com.cybercat.pocketbooksender.model.AppSettings,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = item.category.iconFor(item.extension),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.category.label(settings),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                item.typeDetail()?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val rotationState by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "ChevronRotation"
            )
            val strings = LocalStrings.current
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) strings.sendActionHideDetails else strings.sendActionEditDetails,
                    modifier = Modifier.rotate(rotationState)
                )
            }
        }
    }
}

@Composable
private fun DocumentsTagEditor(
    selectedTag: String,
    suggestions: List<String>,
    onTagChanged: (String) -> Unit,
    categoryName: String,
) {
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = selectedTag,
            onValueChange = onTagChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(strings.get("send_tag_field", categoryName)) },
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
                        selected = selectedTag.equals(suggestion, ignoreCase = true),
                        onClick = { onTagChanged(suggestion) },
                        label = { Text(suggestion) },
                    )
                }
        }
    }
}

@Composable
private fun MangaSeriesEditor(
    selectedSeries: String,
    suggestions: List<String>,
    onSeriesChanged: (String) -> Unit,
) {
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        onClick = { onSeriesChanged(suggestion) },
                        label = { Text(suggestion) },
                    )
                }
        }
    }
}

@Composable
private fun BookCover(item: UploadItem) {
    Surface(
        modifier = Modifier.size(width = 58.dp, height = 78.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        val preview = item.preview
        if (preview != null) {
            ComposeImage(
                bitmap = preview.asImageBitmap(),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = item.category.iconFor(item.extension),
                contentDescription = null,
                modifier = Modifier.padding(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategorySelector(
    selected: BookCategory,
    lockedToManga: Boolean,
    onCategoryChanged: (BookCategory) -> Unit,
    settings: com.cybercat.pocketbooksender.model.AppSettings,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BookCategory.entries.forEach { category ->
            FilterChip(
                selected = selected == category,
                enabled = !lockedToManga || category == BookCategory.Manga,
                onClick = { onCategoryChanged(category) },
                label = { Text(category.label(settings)) },
                leadingIcon = {
                    Icon(
                        imageVector = category.iconFor(""),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

private fun BookCategory.label(settings: com.cybercat.pocketbooksender.model.AppSettings): String = when (this) {
    BookCategory.Books -> settings.booksFolderName
    BookCategory.Documents -> settings.documentsFolderName
    BookCategory.Manga -> settings.mangaFolderName
}

private fun UploadItem.typeDetail(): String? = when (category) {
    BookCategory.Books -> author?.takeIf { it.isNotBlank() }
    BookCategory.Documents -> documentsTag?.takeIf { it.isNotBlank() }
    BookCategory.Manga -> mangaSeries?.takeIf { it.isNotBlank() }
}

private fun BookCategory.iconFor(extension: String): ImageVector = when {
    this == BookCategory.Manga -> Icons.Outlined.Image
    extension == "pdf" -> Icons.Outlined.PictureAsPdf
    this == BookCategory.Documents -> Icons.Outlined.Code
    else -> Icons.AutoMirrored.Outlined.MenuBook
}

private fun List<UploadItem>.commonMangaSeries(): String {
    val series = mapNotNull { item -> item.mangaSeries?.takeIf(String::isNotBlank) }
        .distinctBy { it.lowercase() }
    return if (series.size == 1) series.first() else ""
}

@Composable
private fun UploadProgressOverlay(
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

private val QueueFadeInSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private val QueueFadeOutSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

private val QueuePlacementSpec = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private fun queueClearDelayMillis(rowCount: Int): Long {
    if (rowCount <= 0) return 0L
    val stagger = minOf(rowCount - 1, QueueClearMaxStaggeredRows).toLong() * QueueClearStaggerMillis
    return stagger + QueueClearExitSettleMillis
}

private const val QueueClearMaxStaggeredRows = 8
private const val QueueClearStaggerMillis = 45L
private const val QueueSingleRemoveMillis = 560L
private const val QueueClearExitSettleMillis = QueueSingleRemoveMillis + 120L

// File ended here

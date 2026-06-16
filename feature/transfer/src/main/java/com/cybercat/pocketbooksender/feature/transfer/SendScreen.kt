package com.cybercat.pocketbooksender.feature.transfer

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cybercat.pocketbooksender.localization.LocalStrings
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.UploadStatus
import com.cybercat.pocketbooksender.ui.LocalAdaptiveLayoutInfo
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
    onQueuedMangaSeriesChanged: (String?, String) -> Unit,
    onUploadAll: () -> Unit,
) {
    val strings = LocalStrings.current
    val adaptiveLayout = LocalAdaptiveLayoutInfo.current
    var clearTrigger by remember { mutableStateOf(0) }
    var clearInProgress by remember { mutableStateOf(false) }
    var clearAnimatedRowCount by remember { mutableStateOf(0) }
    val queue = state.queue
    val activeTransferItemIds = state.activeTransferItemIds
    val transferQueue = remember(queue, activeTransferItemIds) {
        queue.filter { item -> item.id in activeTransferItemIds }
    }
    val isTransferProgressVisible = state.isTransferActive &&
        transferQueue.any { item -> item.status != UploadStatus.Uploaded }
    val activeQueue = remember(queue) {
        queue.filterNot { it.status == UploadStatus.Uploaded }
    }
    var visuallyRemovedActiveItemIds by remember { mutableStateOf(emptySet<String>()) }
    val activeItemIds = remember(activeQueue) { activeQueue.mapTo(mutableSetOf()) { item -> item.id } }
    val displayedActiveQueue = remember(activeQueue, visuallyRemovedActiveItemIds, clearInProgress) {
        if (clearInProgress) {
            emptyList()
        } else {
            activeQueue.filterNot { item -> item.id in visuallyRemovedActiveItemIds }
        }
    }
    val activeRows = remember(activeQueue) { activeQueue.withStableLazyKeys() }
    val activeMangaQueue = remember(displayedActiveQueue) {
        displayedActiveQueue.filter { it.category == BookCategory.Manga }
    }
    val canBatchRenameManga = activeMangaQueue.size > 1 && !state.isTransferActive && !clearInProgress
    var showMangaBatchEditor by remember { mutableStateOf(false) }
    val uploadedQueue = remember(queue) {
        queue.filter { it.status == UploadStatus.Uploaded }
    }
    val animatedUploadedSectionCount = if (uploadedQueue.isNotEmpty()) 1 else 0
    val hasUploadableFiles = remember(displayedActiveQueue) {
        displayedActiveQueue.any {
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
            activeMangaQueue = activeMangaQueue,
            suggestions = state.mangaSeriesSuggestions,
            enableHaptics = state.settings.enableHaptics,
            onDismiss = { showMangaBatchEditor = false },
            onApply = { oldSeries, series ->
                onQueuedMangaSeriesChanged(oldSeries, series)
            },
        )
    }

    LaunchedEffect(clearTrigger) {
        if (clearTrigger > 0) {
            delay(queueClearDelayMillis(clearAnimatedRowCount))
            onClearQueue()
        }
    }

    LaunchedEffect(activeItemIds) {
        visuallyRemovedActiveItemIds = visuallyRemovedActiveItemIds intersect activeItemIds
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
                    .padding(horizontal = adaptiveLayout.screenHorizontalPadding),
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
                        count = displayedActiveQueue.size,
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
                                progress = state.uploadProgressById[item.id] ?: item.progress,
                                documentsTags = state.documentsTags,
                                mangaSeriesSuggestions = state.mangaSeriesSuggestions,
                                enableHaptics = state.settings.enableHaptics,
                                settings = state.settings,
                                onRemove = {
                                    visuallyRemovedActiveItemIds = visuallyRemovedActiveItemIds + item.id
                                    triggerRemove()
                                },
                                onCategoryChanged = { category -> onCategoryChanged(item.id, category) },
                                onDocumentsTagChanged = { tag -> onDocumentsTagChanged(item.id, tag) },
                                onMangaSeriesChanged = { series -> onMangaSeriesChanged(item.id, series) },
                            )
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(if (isTransferProgressVisible) 112.dp else 8.dp))
                }
            }

            AnimatedVisibility(
                visible = isTransferProgressVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                enter = fadeIn() + slideInVertically { height -> height / 2 },
                exit = fadeOut() + slideOutVertically { height -> height / 2 },
            ) {
                UploadProgressOverlay(
                    queue = transferQueue,
                    progressById = state.uploadProgressById,
                )
            }
        }
    }
}

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

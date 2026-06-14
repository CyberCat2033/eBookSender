package com.cybercat.pocketbooksender.ui.screens

import android.text.format.DateUtils
import android.view.HapticFeedbackConstants
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.cybercat.pocketbooksender.localization.LocalStrings
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.cybercat.pocketbooksender.domain.bookTitleWithoutExtension
import com.cybercat.pocketbooksender.model.CatalogFile
import com.cybercat.pocketbooksender.model.CatalogGroup
import com.cybercat.pocketbooksender.model.DeviceCatalog
import com.cybercat.pocketbooksender.model.MangaSeriesGroup
import com.cybercat.pocketbooksender.ui.CatalogUiState
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CatalogScreen(
    state: CatalogUiState,
    isConnected: Boolean,
    enableHaptics: Boolean,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onSetEditMode: (Boolean) -> Unit,
    onToggleFileSelection: (String) -> Unit,
    onSetFileSelection: (String, Boolean) -> Unit,
    onToggleGroupSelection: (List<String>, Boolean) -> Unit,
    onDeleteSelectedFiles: () -> Unit,
    onClearDeleteError: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val catalog = state.deviceCatalog
    val selectedFilePathsState = rememberUpdatedState(state.selectedFilePaths)
    val isEditModeState = rememberUpdatedState(state.isEditMode)
    val onSetEditModeState = rememberUpdatedState(onSetEditMode)
    val onSetFileSelectionState = rememberUpdatedState(onSetFileSelection)
    val expandedGroupPaths = remember { mutableStateMapOf<String, Boolean>() }
    val expandedGroupPathSet = expandedGroupPaths
        .filterValues { it }
        .keys
        .toSet()
    val fileTargets = remember(catalog, expandedGroupPathSet) {
        catalog.fileSelectionTargets(expandedGroupPathSet)
    }
    val targetByPath = remember(fileTargets) { fileTargets.associateBy { it.path } }
    val fileRowBounds = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    var listBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var suppressSelectionClickUntilMillis by remember { mutableStateOf(0L) }

    fun suppressSelectionClicks() {
        suppressSelectionClickUntilMillis = SystemClock.uptimeMillis() + SuppressSelectionClickMillis
    }

    fun suppressSelectionClicksUntilGestureEnds() {
        suppressSelectionClickUntilMillis = Long.MAX_VALUE
    }

    fun selectionClickSuppressed(): Boolean =
        SystemClock.uptimeMillis() < suppressSelectionClickUntilMillis

    fun scrollToGroup(key: String, itemCount: Int) {
        scope.launch {
            val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.key == key } ?: return@launch
            val viewportHeight = listState.layoutInfo.viewportSize.height
            
            // Estimated height of an item is ~72dp (64dp + padding).
            val itemHeightPx = with(density) { 72.dp.toPx() }
            val estimatedExpandedHeight = itemInfo.size + (itemCount * itemHeightPx)
            
            val itemBottom = itemInfo.offset + estimatedExpandedHeight
            val overflow = itemBottom - viewportHeight
            
            if (overflow > 0) {
                // Scroll by the overflow amount to make the bottom visible,
                // but don't scroll the item past the top of the screen.
                val maxScroll = itemInfo.offset.toFloat()
                val scrollAmount = minOf(overflow, maxScroll)
                if (scrollAmount > 0) {
                    val animationDuration = minOf(750, 250 + (itemCount * 35))
                    listState.animateScrollBy(scrollAmount, tween(durationMillis = animationDuration))
                }
            }
        }
    }

    BackHandler(enabled = state.isEditMode) {
        onSetEditMode(false)
    }

    LaunchedEffect(catalog) {
        val knownGroupPaths = catalog.groupPaths()
        (expandedGroupPaths.keys - knownGroupPaths).forEach(expandedGroupPaths::remove)
    }

    LaunchedEffect(fileTargets) {
        val visiblePaths = fileTargets.mapTo(mutableSetOf()) { it.path }
        (fileRowBounds.keys - visiblePaths).forEach(fileRowBounds::remove)
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(strings.catalogDeleteTitle) },
            text = { Text(strings.get("catalog_delete_body", state.selectedFilePaths.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.CONFIRM)
                        showDeleteConfirm = false
                        onDeleteSelectedFiles()
                    }
                ) {
                    Text(strings.catalogDeleteBtn, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                        showDeleteConfirm = false
                    }
                ) {
                    Text(strings.catalogDeleteCancel)
                }
            }
        )
    }

    if (state.deleteErrorMessage != null) {
        AlertDialog(
            onDismissRequest = onClearDeleteError,
            title = { Text(strings.catalogDeleteErrorTitle) },
            text = { Text(state.deleteErrorMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                        onClearDeleteError()
                    }
                ) {
                    Text(strings.catalogDeleteErrorBtn)
                }
            }
        )
    }

    if (state.isDeleting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(strings.catalogDeletingState, style = MaterialTheme.typography.titleMedium)
                }
            },
            dismissButton = null
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = state.isEditMode,
                        label = "CatalogTopBarTitle",
                    ) { isEditMode ->
                        if (isEditMode) {
                            Text(strings.get("catalog_selected_count", state.selectedFilePaths.size))
                        } else {
                            Text(strings.catalogTitle)
                        }
                    }
                },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    AnimatedVisibility(
                        visible = state.isEditMode,
                        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
                    ) {
                        IconButton(
                            onClick = {
                                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                onSetEditMode(false)
                            }
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = strings.catalogActionExitEdit)
                        }
                    }
                },
                actions = {
                    AnimatedContent(
                        targetState = state.isEditMode,
                        label = "CatalogTopBarActions",
                    ) { isEditMode ->
                        if (isEditMode) {
                            IconButton(
                                onClick = {
                                    view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.LONG_PRESS)
                                    showDeleteConfirm = true
                                },
                                enabled = state.selectedFilePaths.isNotEmpty() && !catalog.isLoading,
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = strings.catalogActionDeleteSelected)
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                    onSetEditMode(true)
                                },
                                enabled = isConnected && !catalog.isEmpty && !catalog.isLoading,
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = strings.catalogActionEnterEdit)
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        @Composable
        fun ContentWrapper(content: @Composable () -> Unit) {
            if (isConnected) {
                PullToRefreshBox(
                    isRefreshing = catalog.isLoading,
                    onRefresh = {
                        if (!state.isEditMode) {
                            onRefresh()
                        }
                    },
                    modifier = contentModifier
                ) {
                    content()
                }
            } else {
                Box(modifier = contentModifier) {
                    content()
                }
            }
        }

        ContentWrapper {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        listBounds = coordinates.boundsInRoot()
                    }
                    .pointerInput(fileTargets, catalog.isLoading, state.isDeleting) {
                        if (catalog.isLoading || state.isDeleting || fileTargets.isEmpty()) {
                            return@pointerInput
                        }

                        coroutineScope {
                            var selectionActive = false
                            var targetSelected = true
                            var currentY = 0f
                            var anchorIndex: Int? = null
                            var baselineSelectedPaths = emptySet<String>()
                            var autoScrollJob: Job? = null
                            val appliedSelectedByPath = mutableMapOf<String, Boolean>()

                            fun fileTargetAt(y: Float): CatalogPointerTarget? {
                                val rootY = (listBounds?.top ?: return null) + y
                                val path = fileRowBounds.entries
                                    .firstOrNull { (_, bounds) ->
                                        rootY >= bounds.top && rootY <= bounds.bottom
                                    }
                                    ?.key
                                    ?: return null
                                return targetByPath[path]
                            }

                            fun applySelectionAt(y: Float) {
                                val target = fileTargetAt(y) ?: return
                                val anchor = anchorIndex ?: target.index
                                val startIndex = anchor.coerceAtMost(target.index)
                                val endIndex = anchor.coerceAtLeast(target.index)

                                fileTargets.forEach { fileTarget ->
                                    val desiredSelected = if (fileTarget.index in startIndex..endIndex) {
                                        targetSelected
                                    } else {
                                        fileTarget.path in baselineSelectedPaths
                                    }
                                    val currentSelected = appliedSelectedByPath[fileTarget.path]
                                        ?: (fileTarget.path in baselineSelectedPaths)
                                    if (currentSelected != desiredSelected) {
                                        appliedSelectedByPath[fileTarget.path] = desiredSelected
                                        onSetFileSelectionState.value(fileTarget.path, desiredSelected)
                                        view.performHapticIfAllowed(
                                            context,
                                            enableHaptics,
                                            HapticFeedbackConstants.CLOCK_TICK,
                                            ignoreDnd = true,
                                        )
                                    }
                                }
                            }

                            fun autoScrollDelta(): Float {
                                val edgeSize = 84.dp.toPx()
                                val viewportHeight = size.height.toFloat()
                                if (viewportHeight <= 0f) return 0f

                                return when {
                                    currentY < edgeSize -> {
                                        val distance = edgeSize - currentY
                                        val ratio = distance / edgeSize
                                        val maxSpeed = 120f
                                        val speed = (maxSpeed * ratio * ratio).coerceIn(5f, maxSpeed)
                                        -speed
                                    }
                                    currentY > viewportHeight - edgeSize -> {
                                        val distance = currentY - (viewportHeight - edgeSize)
                                        val ratio = distance / edgeSize
                                        val maxSpeed = 120f
                                        val speed = (maxSpeed * ratio * ratio).coerceIn(5f, maxSpeed)
                                        speed
                                    }
                                    else -> 0f
                                }
                            }

                            fun startAutoScroll() {
                                autoScrollJob?.cancel()
                                autoScrollJob = launch {
                                    while (isActive) {
                                        val delta = autoScrollDelta()
                                        if (delta != 0f) {
                                            listState.scrollBy(delta)
                                            applySelectionAt(currentY)
                                        }
                                        delay(16L)
                                    }
                                }
                            }

                            fun stopSelection() {
                                selectionActive = false
                                autoScrollJob?.cancel()
                                autoScrollJob = null
                                anchorIndex = null
                                baselineSelectedPaths = emptySet()
                                appliedSelectedByPath.clear()
                            }

                            detectDragGesturesAfterQuickLongPress(
                                onDragStart = { offset ->
                                    currentY = offset.y
                                    val target = fileTargetAt(currentY)
                                    if (target == null) {
                                        stopSelection()
                                    } else {
                                        selectionActive = true
                                        baselineSelectedPaths = selectedFilePathsState.value
                                        targetSelected = target.path !in baselineSelectedPaths
                                        anchorIndex = target.index
                                        appliedSelectedByPath.clear()
                                        suppressSelectionClicksUntilGestureEnds()
                                        if (!isEditModeState.value) {
                                            onSetEditModeState.value(true)
                                        }
                                        view.performHapticIfAllowed(
                                            context,
                                            enableHaptics,
                                            HapticFeedbackConstants.LONG_PRESS,
                                            ignoreDnd = true,
                                        )
                                        applySelectionAt(currentY)
                                        startAutoScroll()
                                    }
                                },
                                onDrag = { change, _ ->
                                    currentY = change.position.y
                                    if (selectionActive) {
                                        change.consume()
                                        applySelectionAt(currentY)
                                    }
                                },
                                onDragEnd = {
                                    suppressSelectionClicks()
                                    stopSelection()
                                },
                                onDragCancel = {
                                    suppressSelectionClicks()
                                    stopSelection()
                                },
                            )
                        }
                    }
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!isConnected) {
                    item {
                        CatalogMessage(
                            title = strings.catalogNotConnected,
                            text = strings.catalogConnectPrompt,
                        )
                    }
                    return@LazyColumn
                }

                if (catalog.isLoading && catalog.isEmpty) {
                    item {
                        CatalogMessage(
                            title = strings.catalogReadingTitle,
                            text = strings.catalogReadingDesc,
                            isLoading = true,
                        )
                    }
                }

                catalog.errorMessage?.let { message ->
                    item {
                        CatalogMessage(
                            title = strings.catalogStatusCannotRead,
                            text = message,
                            isError = true,
                        )
                    }
                }

                if (!catalog.isLoading && catalog.isEmpty) {
                    item {
                        CatalogMessage(
                            title = strings.catalogMsgEmpty,
                            text = strings.get("catalog_no_books_found", state.settings.booksFolderName, state.settings.documentsFolderName, state.settings.mangaFolderName),
                        )
                    }
                }

                if (catalog.books.isNotEmpty()) {
                    item(
                        key = "section:books",
                        contentType = "catalog_section_title",
                    ) {
                        SectionTitle(
                            title = state.settings.booksFolderName,
                            count = catalog.books.sumOf { it.files.size },
                        )
                    }
                    items(
                        items = catalog.books,
                        key = { "books:${it.path}" },
                        contentType = { "catalog_group" }
                    ) { group ->
                        CatalogGroupCard(
                            group = group,
                            expanded = group.path in expandedGroupPathSet,
                            isEditMode = state.isEditMode,
                            selectedFilePaths = state.selectedFilePaths,
                            enableHaptics = enableHaptics,
                            onToggleFileSelection = onToggleFileSelection,
                            onToggleGroupSelection = onToggleGroupSelection,
                            onEnterEditMode = { onSetEditMode(true) },
                            selectionClickSuppressed = ::selectionClickSuppressed,
                            onExpandedChange = { expanded ->
                                if (expanded) {
                                    expandedGroupPaths[group.path] = true
                                    scrollToGroup("books:${group.path}", group.files.size)
                                } else {
                                    expandedGroupPaths.remove(group.path)
                                }
                            },
                            onFileBoundsChanged = { path, bounds ->
                                if (bounds == null) {
                                    fileRowBounds.remove(path)
                                } else {
                                    fileRowBounds[path] = bounds
                                }
                            },
                        )
                    }
                }

                if (catalog.documents.isNotEmpty()) {
                    item(
                        key = "section:documents",
                        contentType = "catalog_section_title",
                    ) {
                        SectionTitle(
                            title = state.settings.documentsFolderName,
                            count = catalog.documents.sumOf { it.files.size },
                        )
                    }
                    items(
                        items = catalog.documents,
                        key = { "documents:${it.path}" },
                        contentType = { "catalog_group" }
                    ) { group ->
                        CatalogGroupCard(
                            group = group,
                            expanded = group.path in expandedGroupPathSet,
                            isEditMode = state.isEditMode,
                            selectedFilePaths = state.selectedFilePaths,
                            enableHaptics = enableHaptics,
                            onToggleFileSelection = onToggleFileSelection,
                            onToggleGroupSelection = onToggleGroupSelection,
                            onEnterEditMode = { onSetEditMode(true) },
                            selectionClickSuppressed = ::selectionClickSuppressed,
                            onExpandedChange = { expanded ->
                                if (expanded) {
                                    expandedGroupPaths[group.path] = true
                                    scrollToGroup("documents:${group.path}", group.files.size)
                                } else {
                                    expandedGroupPaths.remove(group.path)
                                }
                            },
                            onFileBoundsChanged = { path, bounds ->
                                if (bounds == null) {
                                    fileRowBounds.remove(path)
                                } else {
                                    fileRowBounds[path] = bounds
                                }
                            },
                        )
                    }
                }

                if (catalog.manga.isNotEmpty()) {
                    item(
                        key = "section:manga",
                        contentType = "catalog_section_title",
                    ) {
                        SectionTitle(
                            title = state.settings.mangaFolderName,
                            count = catalog.manga.size,
                        )
                    }
                    items(
                        items = catalog.manga,
                        key = { "manga:${it.path}" },
                        contentType = { "manga_series_group" }
                    ) { group ->
                        MangaSeriesCard(
                            group = group,
                            expanded = group.path in expandedGroupPathSet,
                            isEditMode = state.isEditMode,
                            selectedFilePaths = state.selectedFilePaths,
                            enableHaptics = enableHaptics,
                            onToggleFileSelection = onToggleFileSelection,
                            onToggleGroupSelection = onToggleGroupSelection,
                            onEnterEditMode = { onSetEditMode(true) },
                            selectionClickSuppressed = ::selectionClickSuppressed,
                            onExpandedChange = { expanded ->
                                if (expanded) {
                                    expandedGroupPaths[group.path] = true
                                    scrollToGroup("manga:${group.path}", group.files.size)
                                } else {
                                    expandedGroupPaths.remove(group.path)
                                }
                            },
                            onFileBoundsChanged = { path, bounds ->
                                if (bounds == null) {
                                    fileRowBounds.remove(path)
                                } else {
                                    fileRowBounds[path] = bounds
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogMessage(
    title: String,
    text: String,
    isError: Boolean = false,
    isLoading: Boolean = false,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (isLoading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .zIndex(CatalogSectionTitleZIndex)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectionSlot(
    visible: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val width by animateDpAsState(
        targetValue = if (visible) SelectionSlotWidth else 0.dp,
        animationSpec = tween(
            durationMillis = SelectionMotionDurationMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "SelectionSlotWidth",
    )
    val checkboxAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = SelectionMotionDurationMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "SelectionCheckboxAlpha",
    )
    val checkboxScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.86f,
        animationSpec = tween(
            durationMillis = SelectionMotionDurationMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "SelectionCheckboxScale",
    )

    Box(
        modifier = Modifier
            .width(width)
            .height(SelectionControlSize)
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart,
    ) {
        CompactCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.graphicsLayer {
                alpha = checkboxAlpha
                scaleX = checkboxScale
                scaleY = checkboxScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            },
        )
    }
}

@Composable
private fun CompactCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier.size(SelectionControlSize),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogGroupCard(
    group: CatalogGroup,
    expanded: Boolean,
    isEditMode: Boolean,
    selectedFilePaths: Set<String>,
    enableHaptics: Boolean,
    onToggleFileSelection: (String) -> Unit,
    onToggleGroupSelection: (List<String>, Boolean) -> Unit,
    onEnterEditMode: () -> Unit,
    selectionClickSuppressed: () -> Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onFileBoundsChanged: (String, androidx.compose.ui.geometry.Rect?) -> Unit,
    modifier: Modifier = Modifier
) {
    val filePaths = remember(group.files) { group.files.map { it.path } }
    val isGroupFullySelected = remember(selectedFilePaths, filePaths) {
        filePaths.isNotEmpty() && filePaths.all { it in selectedFilePaths }
    }

    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current

    fun toggleGroup(checked: Boolean = !isGroupFullySelected) {
        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
        onToggleGroupSelection(filePaths, checked)
    }

    fun selectGroupFromLongPress() {
        if (filePaths.isEmpty() || selectionClickSuppressed()) return
        view.performHapticIfAllowed(
            context,
            enableHaptics,
            HapticFeedbackConstants.LONG_PRESS,
            ignoreDnd = true,
        )
        if (!isEditMode) {
            onEnterEditMode()
        }
        onToggleGroupSelection(filePaths, true)
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectionSlot(
                    visible = isEditMode,
                    checked = isGroupFullySelected,
                    onCheckedChange = ::toggleGroup,
                )
                ExpandableHeader(
                    title = group.name,
                    subtitle = group.files.summary(strings),
                    expanded = expanded,
                    enableHaptics = enableHaptics,
                    onToggle = { onExpandedChange(!expanded) },
                    titleClickEnabled = isEditMode,
                    onTitleClick = {
                        if (!selectionClickSuppressed()) {
                            toggleGroup()
                        }
                    },
                    onTitleLongClick = ::selectGroupFromLongPress,
                    modifier = Modifier.weight(1f)
                )
            }
            val animationDuration = remember(group.files.size) {
                minOf(750, 250 + (group.files.size * 35))
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(durationMillis = animationDuration)) + fadeIn(animationSpec = tween(durationMillis = animationDuration)),
                exit = shrinkVertically(animationSpec = tween(durationMillis = animationDuration)) + fadeOut(animationSpec = tween(durationMillis = animationDuration))
            ) {
                FileList(
                    files = group.files,
                    isEditMode = isEditMode,
                    selectedFilePaths = selectedFilePaths,
                    enableHaptics = enableHaptics,
                    onToggleFileSelection = onToggleFileSelection,
                    selectionClickSuppressed = selectionClickSuppressed,
                    onFileBoundsChanged = onFileBoundsChanged,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaSeriesCard(
    group: MangaSeriesGroup,
    expanded: Boolean,
    isEditMode: Boolean,
    selectedFilePaths: Set<String>,
    enableHaptics: Boolean,
    onToggleFileSelection: (String) -> Unit,
    onToggleGroupSelection: (List<String>, Boolean) -> Unit,
    onEnterEditMode: () -> Unit,
    selectionClickSuppressed: () -> Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onFileBoundsChanged: (String, androidx.compose.ui.geometry.Rect?) -> Unit,
    modifier: Modifier = Modifier
) {
    val filePaths = remember(group.files) { group.files.map { it.path } }
    val isGroupFullySelected = remember(selectedFilePaths, filePaths) {
        filePaths.isNotEmpty() && filePaths.all { it in selectedFilePaths }
    }

    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current

    fun toggleGroup(checked: Boolean = !isGroupFullySelected) {
        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
        onToggleGroupSelection(filePaths, checked)
    }

    fun selectGroupFromLongPress() {
        if (filePaths.isEmpty() || selectionClickSuppressed()) return
        view.performHapticIfAllowed(
            context,
            enableHaptics,
            HapticFeedbackConstants.LONG_PRESS,
            ignoreDnd = true,
        )
        if (!isEditMode) {
            onEnterEditMode()
        }
        onToggleGroupSelection(filePaths, true)
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectionSlot(
                    visible = isEditMode,
                    checked = isGroupFullySelected,
                    onCheckedChange = ::toggleGroup,
                )
                ExpandableHeader(
                    title = group.name,
                    subtitle = group.latestFile?.let { file ->
                        strings.get("catalog_label_latest", "${file.mangaDisplayTitle()}${file.progressSuffix(strings)}")
                    } ?: strings.catalogNoFiles,
                    subtitleMaxLines = 3,
                    expanded = expanded,
                    enableHaptics = enableHaptics,
                    onToggle = { onExpandedChange(!expanded) },
                    titleClickEnabled = isEditMode,
                    onTitleClick = {
                        if (!selectionClickSuppressed()) {
                            toggleGroup()
                        }
                    },
                    onTitleLongClick = ::selectGroupFromLongPress,
                    modifier = Modifier.weight(1f)
                )
            }
            val animationDuration = remember(group.files.size) {
                minOf(750, 250 + (group.files.size * 35))
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(durationMillis = animationDuration)) + fadeIn(animationSpec = tween(durationMillis = animationDuration)),
                exit = shrinkVertically(animationSpec = tween(durationMillis = animationDuration)) + fadeOut(animationSpec = tween(durationMillis = animationDuration))
            ) {
                FileList(
                    files = group.files,
                    showProgress = false,
                    isEditMode = isEditMode,
                    selectedFilePaths = selectedFilePaths,
                    enableHaptics = enableHaptics,
                    onToggleFileSelection = onToggleFileSelection,
                    selectionClickSuppressed = selectionClickSuppressed,
                    onFileBoundsChanged = onFileBoundsChanged,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpandableHeader(
    title: String,
    subtitle: String,
    subtitleMaxLines: Int = 1,
    expanded: Boolean,
    enableHaptics: Boolean,
    onToggle: () -> Unit,
    titleClickEnabled: Boolean = false,
    onTitleClick: () -> Unit = {},
    onTitleLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val titleInteractionModifier = if (titleClickEnabled) {
        Modifier.combinedClickable(
            onClick = onTitleClick,
            onLongClick = onTitleLongClick,
        )
    } else {
        Modifier.pointerInput(onTitleLongClick) {
            detectTapGestures(
                onLongPress = {
                    onTitleLongClick()
                },
            )
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .then(titleInteractionModifier)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = subtitleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val rotationState by animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "ChevronRotation"
        )
        IconButton(onClick = {
            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
            onToggle()
        }) {
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) strings.catalogActionCollapse else strings.catalogActionExpand,
                modifier = Modifier.rotate(rotationState)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileList(
    files: List<CatalogFile>,
    showProgress: Boolean = true,
    isEditMode: Boolean = false,
    selectedFilePaths: Set<String> = emptySet(),
    enableHaptics: Boolean = false,
    onToggleFileSelection: (String) -> Unit = {},
    selectionClickSuppressed: () -> Boolean = { false },
    onFileBoundsChanged: (String, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current

    var currentFiles by remember { mutableStateOf(files) }
    val deletedPaths = remember { mutableStateListOf<String>() }

    LaunchedEffect(files) {
        val newPaths = files.map { it.path }.toSet()
        val removed = currentFiles.filter { it.path !in newPaths && it.path !in deletedPaths }
        if (removed.isNotEmpty()) {
            deletedPaths.addAll(removed.map { it.path })
            currentFiles = currentFiles.map { current ->
                files.firstOrNull { it.path == current.path } ?: current
            }
        } else {
            deletedPaths.clear()
            currentFiles = files
        }
    }

    Column(
        modifier = Modifier.padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        currentFiles.forEach { file ->
            val isExiting = file.path in deletedPaths
            val isSelected = file.path in selectedFilePaths

            DisposableEffect(file.path) {
                onDispose {
                    onFileBoundsChanged(file.path, null)
                }
            }

            AnimatedVisibility(
                visible = !isExiting,
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(
                        durationMillis = RemovalMotionDurationMillis,
                        easing = FastOutSlowInEasing,
                    ),
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = RemovalMotionDurationMillis,
                        easing = FastOutSlowInEasing,
                    ),
                ),
                label = "FileRemoval"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            onFileBoundsChanged(file.path, coordinates.boundsInRoot())
                        }
                        .clickable(enabled = isEditMode) {
                            if (selectionClickSuppressed()) {
                                return@clickable
                            }
                            view.performHapticIfAllowed(
                                context,
                                enableHaptics,
                                HapticFeedbackConstants.VIRTUAL_KEY,
                            )
                            onToggleFileSelection(file.path)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SelectionSlot(
                        visible = isEditMode,
                        checked = isSelected,
                        onCheckedChange = {
                            if (!selectionClickSuppressed()) {
                                view.performHapticIfAllowed(
                                    context,
                                    enableHaptics,
                                    HapticFeedbackConstants.VIRTUAL_KEY,
                                )
                                onToggleFileSelection(file.path)
                            }
                        },
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (showProgress) file.displayTitle() else file.mangaDisplayTitle(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (showProgress) {
                            val progressDetailText = when {
                                file.completed -> strings.catalogStatusCompleted
                                file.currentPage != null && file.currentPage > 0 -> {
                                    if (file.totalPages != null && file.totalPages > 0) {
                                        strings.get("catalog_page_ratio", file.currentPage, file.totalPages)
                                    } else {
                                        strings.get("catalog_page_current", file.currentPage)
                                    }
                                }
                                else -> strings.catalogStatusNotStarted
                            }

                            val relativeTime = file.lastOpenedAtMillis?.let { time ->
                                DateUtils.getRelativeTimeSpanString(
                                    time,
                                    System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS,
                                    DateUtils.FORMAT_ABBREV_RELATIVE
                                ).toString()
                            }
                            val lastReadText = relativeTime?.let { relative ->
                                strings.get("catalog_label_last_read", relative)
                            }

                            val subtitleParts = buildList {
                                if (!file.series.isNullOrBlank()) {
                                    add(strings.get("catalog_label_series", file.series))
                                }
                                add(progressDetailText)
                                if (lastReadText != null) {
                                    add(lastReadText)
                                }
                            }

                            Text(
                                text = subtitleParts.joinToString(" | "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (showProgress) {
                        val percent = file.readProgressPercent
                        if (percent != null && percent > 0) {
                            Spacer(Modifier.width(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                CircularProgressIndicator(
                                    progress = { percent / 100f },
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.5.dp,
                                    color = if (file.completed) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.secondary
                                    },
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Text(
                                    text = "$percent%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
            LaunchedEffect(isExiting) {
                if (isExiting) {
                    delay(RemovalMotionDurationMillis.toLong())
                    currentFiles = currentFiles.filter { it.path != file.path }
                    deletedPaths.remove(file.path)
                }
            }
        }
    }
}

private fun DeviceCatalog.groupPaths(): Set<String> = buildSet {
    books.forEach { group -> add(group.path) }
    documents.forEach { group -> add(group.path) }
    manga.forEach { group -> add(group.path) }
}

private fun DeviceCatalog.fileSelectionTargets(
    expandedGroupPaths: Set<String>,
): List<CatalogPointerTarget> = buildList {
    fun addFiles(groupPath: String, files: List<CatalogFile>) {
        if (groupPath !in expandedGroupPaths) return
        files.forEach { file ->
            add(
                CatalogPointerTarget(
                    index = size,
                    path = file.path,
                )
            )
        }
    }

    books.forEach { group -> addFiles(group.path, group.files) }
    documents.forEach { group -> addFiles(group.path, group.files) }
    manga.forEach { group -> addFiles(group.path, group.files) }
}

private suspend fun PointerInputScope.detectDragGesturesAfterQuickLongPress(
    onDragStart: (Offset) -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var currentChange = down
        val touchSlop = viewConfiguration.touchSlop

        val longPressReached: Boolean = withTimeoutOrNull<Boolean>(CatalogLongPressMillis) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                    ?: return@withTimeoutOrNull false
                if (!change.pressed || change.isConsumed) return@withTimeoutOrNull false
                if ((change.position - down.position).getDistance() > touchSlop) {
                    return@withTimeoutOrNull false
                }
                currentChange = change
            }
            true
        } ?: true

        if (!longPressReached) return@awaitEachGesture

        onDragStart(currentChange.position)
        currentChange.consume()

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                onDragCancel()
                break
            }
            if (!change.pressed) {
                change.consume()
                onDragEnd()
                break
            }

            val dragAmount = change.positionChange()
            if (dragAmount != Offset.Zero) {
                onDrag(change, dragAmount)
                change.consume()
            }
        }
    }
}

private data class CatalogPointerTarget(
    val index: Int,
    val path: String,
)

private fun List<CatalogFile>.summary(strings: com.cybercat.pocketbooksender.localization.AppStrings): String {
    val withProgress = count { it.readProgressPercent != null }
    val completed = count(CatalogFile::completed)
    val fileCount = size
    return buildList {
        add(strings.get("catalog_group_files_count", fileCount))
        if (withProgress > 0) add(strings.get("catalog_group_progress_count", withProgress))
        if (completed > 0) add(strings.get("catalog_group_completed_count", completed))
    }.joinToString(", ")
}

private fun CatalogFile.displayTitle(): String =
    title?.takeIf { it.isNotBlank() } ?: name

private fun CatalogFile.mangaDisplayTitle(): String =
    name.bookTitleWithoutExtension().ifBlank { displayTitle() }

private fun MangaSeriesGroup.subtitle(strings: com.cybercat.pocketbooksender.localization.AppStrings): String =
    lastReadFile?.let { file ->
        strings.get("catalog_label_last_read", "${file.mangaDisplayTitle()}${file.progressSuffix(strings)}")
    } ?: latestFile?.let { file ->
        strings.get("catalog_label_latest", file.mangaDisplayTitle())
    } ?: strings.catalogNoFiles

private fun CatalogFile.progressSuffix(strings: com.cybercat.pocketbooksender.localization.AppStrings): String =
    progressText(strings)?.let { " | $it" }.orEmpty()

private fun CatalogFile.progressText(strings: com.cybercat.pocketbooksender.localization.AppStrings): String? =
    when {
        completed -> strings.catalogStatusCompleted
        readProgressPercent != null -> strings.get("catalog_read_percentage", readProgressPercent)
        else -> null
    }

private const val CatalogLongPressMillis = 300L
private const val SuppressSelectionClickMillis = 250L
private const val SelectionMotionDurationMillis = 220
private const val RemovalMotionDurationMillis = 260
private const val CatalogSectionTitleZIndex = 1f
private val SelectionSlotWidth = 36.dp
private val SelectionControlSize = 24.dp

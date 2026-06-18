package com.cybercat.pocketbooksender.feature.catalog

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.cybercat.pocketbooksender.localization.LocalStrings
import com.cybercat.pocketbooksender.ui.AnimatedAlertDialog
import com.cybercat.pocketbooksender.ui.LocalAdaptiveLayoutInfo
import com.cybercat.pocketbooksender.ui.LocalDismissDialog
import com.cybercat.pocketbooksender.util.AppHapticFeedback
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import com.cybercat.pocketbooksender.util.pointerInputDragSelection
import com.cybercat.pocketbooksender.util.rememberClickSuppressionState
import com.cybercat.pocketbooksender.util.rememberDragSelectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    onClearDeleteError: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val adaptiveLayout = LocalAdaptiveLayoutInfo.current
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
    val clickSuppression = rememberClickSuppressionState()

    val dragSelectionState = rememberDragSelectionState(
        lazyListState = listState,
        hapticView = view,
        context = context,
        enableHaptics = enableHaptics,
        getTargetAt = { y ->
            val rootY = (listBounds?.top ?: return@rememberDragSelectionState null) + y
            val path = fileRowBounds.entries
                .firstOrNull { (_, bounds) ->
                    rootY >= bounds.top && rootY <= bounds.bottom
                }
                ?.key
                ?: return@rememberDragSelectionState null
            targetByPath[path]
        },
        getTargetIndex = { it.index },
        getTargetId = { it.path },
        getInitialSelection = { selectedFilePathsState.value },
        getAllTargets = { fileTargets },
        onSetSelected = { path, selected -> onSetFileSelectionState.value(path, selected) },
        edgeSizePx = with(density) { 84.dp.toPx() },
        onDragStarted = {
            clickSuppression.suppressUntilGestureEnds()
            if (!isEditModeState.value) {
                onSetEditModeState.value(true)
            }
        }
    )

    fun scrollToGroup(key: String, itemCount: Int) {
        scope.launch {
            val itemInfo =
                listState.layoutInfo.visibleItemsInfo.find { it.key == key } ?: return@launch
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
                    listState.animateScrollBy(
                        scrollAmount,
                        tween(durationMillis = animationDuration)
                    )
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
    var deleteAfterDismiss by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AnimatedAlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                if (deleteAfterDismiss) {
                    deleteAfterDismiss = false
                    onDeleteSelectedFiles()
                }
            },
            title = { Text(strings.catalogDeleteTitle) },
            text = { Text(strings.get("catalog_delete_body", state.selectedFilePaths.size)) },
            confirmButton = {
                val dismiss = LocalDismissDialog.current
                TextButton(
                    onClick = {
                        view.performHapticIfAllowed(
                            context,
                            enableHaptics,
                            AppHapticFeedback.Confirm
                        )
                        deleteAfterDismiss = true
                        dismiss()
                    }
                ) {
                    Text(strings.catalogDeleteBtn, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                val dismiss = LocalDismissDialog.current
                TextButton(
                    onClick = {
                        view.performHapticIfAllowed(context, enableHaptics, AppHapticFeedback.Press)
                        deleteAfterDismiss = false
                        dismiss()
                    }
                ) {
                    Text(strings.catalogDeleteCancel)
                }
            }
        )
    }

    if (state.deleteErrorMessage != null) {
        AnimatedAlertDialog(
            onDismissRequest = onClearDeleteError,
            title = { Text(strings.catalogDeleteErrorTitle) },
            text = { Text(state.deleteErrorMessage) },
            confirmButton = {
                val dismiss = LocalDismissDialog.current
                TextButton(
                    onClick = {
                        view.performHapticIfAllowed(context, enableHaptics, AppHapticFeedback.Press)
                        dismiss()
                    }
                ) {
                    Text(strings.catalogDeleteErrorBtn)
                }
            }
        )
    }

    if (state.isDeleting) {
        AnimatedAlertDialog(
            onDismissRequest = {},
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(strings.catalogDeletingState, style = MaterialTheme.typography.titleMedium)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = state.isEditMode,
                        label = "CatalogTopBarTitle"
                    ) { isEditMode ->
                        if (isEditMode) {
                            Text(
                                strings.get("catalog_selected_count", state.selectedFilePaths.size)
                            )
                        } else {
                            Text(strings.catalogTitle)
                        }
                    }
                },
                navigationIcon = {
                    AnimatedVisibility(
                        visible = state.isEditMode,
                        enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                        exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
                    ) {
                        IconButton(
                            onClick = {
                                view.performHapticIfAllowed(
                                    context,
                                    enableHaptics,
                                    AppHapticFeedback.Press
                                )
                                onSetEditMode(false)
                            }
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = strings.catalogActionExitEdit
                            )
                        }
                    }
                },
                actions = {
                    AnimatedContent(
                        targetState = state.isEditMode,
                        label = "CatalogTopBarActions"
                    ) { isEditMode ->
                        if (isEditMode) {
                            IconButton(
                                onClick = {
                                    view.performHapticIfAllowed(
                                        context,
                                        enableHaptics,
                                        AppHapticFeedback.LongPress
                                    )
                                    showDeleteConfirm = true
                                },
                                enabled = state.selectedFilePaths.isNotEmpty() && !catalog.isLoading
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = strings.catalogActionDeleteSelected
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    view.performHapticIfAllowed(
                                        context,
                                        enableHaptics,
                                        AppHapticFeedback.Press
                                    )
                                    onSetEditMode(true)
                                },
                                enabled = isConnected && !catalog.isEmpty && !catalog.isLoading
                            ) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = strings.catalogActionEnterEdit
                                )
                            }
                        }
                    }
                }
            )
        }
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
                    .pointerInputDragSelection(
                        dragSelectionState,
                        clickSuppression,
                        !catalog.isLoading && !state.isDeleting && fileTargets.isNotEmpty(),
                        fileTargets,
                        catalog.isLoading,
                        state.isDeleting
                    )
                    .padding(horizontal = adaptiveLayout.screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isConnected) {
                    item {
                        CatalogMessage(
                            title = strings.catalogNotConnected,
                            text = strings.catalogConnectPrompt
                        )
                    }
                    return@LazyColumn
                }

                if (catalog.isLoading && catalog.isEmpty) {
                    item {
                        CatalogMessage(
                            title = strings.catalogReadingTitle,
                            text = strings.catalogReadingDesc,
                            isLoading = true
                        )
                    }
                }

                catalog.errorMessage?.let { message ->
                    item {
                        CatalogMessage(
                            title = strings.catalogStatusCannotRead,
                            text = message,
                            isError = true
                        )
                    }
                }

                if (!catalog.isLoading && catalog.isEmpty) {
                    item {
                        CatalogMessage(
                            title = strings.catalogMsgEmpty,
                            text = strings.get(
                                "catalog_no_books_found",
                                state.settings.booksFolderName,
                                state.settings.documentsFolderName,
                                state.settings.mangaFolderName
                            )
                        )
                    }
                }

                if (catalog.books.isNotEmpty()) {
                    item(
                        key = "section:books",
                        contentType = "catalog_section_title"
                    ) {
                        SectionTitle(
                            title = state.settings.booksFolderName,
                            count = catalog.books.sumOf { it.files.size }
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
                            selectionClickSuppressed = { clickSuppression.isSuppressed() },
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
                            }
                        )
                    }
                }

                if (catalog.documents.isNotEmpty()) {
                    item(
                        key = "section:documents",
                        contentType = "catalog_section_title"
                    ) {
                        SectionTitle(
                            title = state.settings.documentsFolderName,
                            count = catalog.documents.sumOf { it.files.size }
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
                            selectionClickSuppressed = { clickSuppression.isSuppressed() },
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
                            }
                        )
                    }
                }

                if (catalog.manga.isNotEmpty()) {
                    item(
                        key = "section:manga",
                        contentType = "catalog_section_title"
                    ) {
                        SectionTitle(
                            title = state.settings.mangaFolderName,
                            count = catalog.manga.size
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
                            selectionClickSuppressed = { clickSuppression.isSuppressed() },
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
                            }
                        )
                    }
                }
            }
        }
    }
}

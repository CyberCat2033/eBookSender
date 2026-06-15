package com.cybercat.pocketbooksender.feature.manga

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.cybercat.pocketbooksender.data.manga.MangaChapter
import com.cybercat.pocketbooksender.data.manga.MangaSeriesBookmark
import com.cybercat.pocketbooksender.data.manga.MangaSeriesSearchResult
import com.cybercat.pocketbooksender.localization.LocalStrings
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import com.cybercat.pocketbooksender.util.calculateAutoScrollDelta
import com.cybercat.pocketbooksender.util.detectDragGesturesAfterQuickLongPress
import com.cybercat.pocketbooksender.ui.StatusMessageHost
import com.cybercat.pocketbooksender.ui.LoadingCard


import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun MangaPane(
    state: MangaUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    onSearchChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenBrowser: () -> Unit,
    onCloseBrowser: () -> Unit,
    onWebPageLoaded: (String, String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onToggleChapter: (String, Boolean) -> Unit,
    onSetMangaSeriesFavorite: (Boolean) -> Unit,
    onSetMangaSeriesSubscribed: (Boolean) -> Unit,
    onCheckSubscriptions: () -> Unit,
    onDownloadSelected: () -> Unit,
    onToggleSubscriptionUpdateChapter: (String, Boolean) -> Unit,
    onSelectAllSubscriptionUpdateChapters: () -> Unit,
    onClearSubscriptionUpdateChapters: () -> Unit,
    onDownloadSubscriptionUpdates: () -> Unit,
    onCloseSubscriptionUpdates: () -> Unit,
    enableHaptics: Boolean,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val strings = LocalStrings.current
    val selectedChapterIdsState = rememberUpdatedState(state.selectedChapterIds)
    val onToggleChapterState = rememberUpdatedState(onToggleChapter)
    val selectedSeriesId = state.selectedSeries?.seriesId
    var handledSeriesScrollRequest by rememberSaveable { mutableStateOf(state.selectedSeriesScrollRequest) }
    val chapterTargets = remember(state.chapters) {
        state.chapters.mapIndexed { index, chapter ->
            chapterItemKey(chapter) to ChapterPointerTarget(
                index = index,
                chapterId = chapter.chapterId,
            )
        }.toMap()
    }

    LaunchedEffect(state.selectedSeriesScrollRequest) {
        if (
            selectedSeriesId != null &&
            state.selectedSeriesScrollRequest > handledSeriesScrollRequest
        ) {
            handledSeriesScrollRequest = state.selectedSeriesScrollRequest
            listState.animateScrollToItem(state.selectedSeriesItemIndex())
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.chapters, state.isDownloading) {
                    if (state.isDownloading || state.chapters.isEmpty()) return@pointerInput

                    coroutineScope {
                        var selectionActive = false
                        var targetSelected = true
                        var currentY = 0f
                        var anchorIndex: Int? = null
                        var baselineSelectedIds = emptySet<String>()
                        var autoScrollJob: Job? = null
                        val appliedSelectedById = mutableMapOf<String, Boolean>()

                        fun chapterTargetAt(y: Float): ChapterPointerTarget? {
                            val pointerY = y.toInt()
                            return listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { item ->
                                    pointerY >= item.offset && pointerY <= item.offset + item.size
                                }
                                ?.key
                                ?.let { key -> chapterTargets[key] }
                        }

                        fun applySelectionAt(y: Float) {
                            val target = chapterTargetAt(y) ?: return
                            val anchor = anchorIndex ?: target.index
                            val startIndex = anchor.coerceAtMost(target.index)
                            val endIndex = anchor.coerceAtLeast(target.index)

                            state.chapters.forEachIndexed { index, chapter ->
                                val desiredSelected = if (index in startIndex..endIndex) {
                                    targetSelected
                                } else {
                                    chapter.chapterId in baselineSelectedIds
                                }
                                val currentSelected = appliedSelectedById[chapter.chapterId]
                                    ?: (chapter.chapterId in baselineSelectedIds)
                                if (currentSelected != desiredSelected) {
                                    appliedSelectedById[chapter.chapterId] = desiredSelected
                                    onToggleChapterState.value(chapter.chapterId, desiredSelected)
                                    view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.CLOCK_TICK, ignoreDnd = true)
                                }
                            }
                        }

                        fun autoScrollDelta(): Float {
                            return calculateAutoScrollDelta(
                                currentY = currentY,
                                viewportHeight = size.height.toFloat(),
                                edgeSizePx = 84.dp.toPx()
                            )
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
                            baselineSelectedIds = emptySet()
                            appliedSelectedById.clear()
                        }

                        detectDragGesturesAfterQuickLongPress(
                            onDragStart = { offset ->
                                currentY = offset.y
                                val target = chapterTargetAt(currentY)
                                if (target == null) {
                                    stopSelection()
                                } else {
                                    selectionActive = true
                                    baselineSelectedIds = selectedChapterIdsState.value
                                    targetSelected = target.chapterId !in baselineSelectedIds
                                    anchorIndex = target.index
                                    appliedSelectedById.clear()
                                    view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.LONG_PRESS, ignoreDnd = true)
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
                            onDragEnd = { stopSelection() },
                            onDragCancel = { stopSelection() },
                        )
                    }
                },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "manga-top") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MangaSearchPanel(
                        state = state,
                        onSearchChanged = onSearchChanged,
                        onSearch = onSearch,
                        onOpenBrowser = onOpenBrowser,
                        enableHaptics = enableHaptics,
                    )

                    if (state.savedSeries.isNotEmpty()) {
                        SavedMangaPanel(
                            savedSeries = state.savedSeries,
                            isCheckingSubscriptions = state.isCheckingSubscriptions,
                            enabled = !state.isLoading && !state.isDownloading,
                            enableHaptics = enableHaptics,
                            onOpenSeries = onOpenSeries,
                            onCheckSubscriptions = onCheckSubscriptions,
                        )
                    }
                }
            }

            item(key = "manga-error-host") {
                StatusMessageHost(text = state.errorMessage, isError = true)
            }

            item(key = "manga-status-host") {
                StatusMessageHost(text = state.statusMessage, isError = false)
            }

            if (state.isLoading) {
                item(key = "manga-loading") {
                    LoadingCard(strings.mangaLoadingSource)
                }
            }

            val series = state.selectedSeries
            if (series != null) {
                val savedSeries = state.savedSeries.firstOrNull { saved ->
                    saved.sourceId == series.sourceId && saved.seriesId == series.seriesId
                }
                val lastDownloadedChapter = state.downloadedChapters.firstOrNull { downloaded ->
                    downloaded.sourceId == series.sourceId && downloaded.seriesId == series.seriesId
                }?.chapterTitle

                item(key = "manga-selected-series") {
                    SelectedSeriesCard(
                        title = series.title,
                        description = series.description,
                        coverUrl = series.coverUrl,
                        isFavorite = savedSeries?.favorite == true,
                        isSubscribed = savedSeries?.subscribed == true,
                        lastDownloadedChapter = lastDownloadedChapter,
                        lastReadChapter = state.lastReadChapterText,
                        chapters = state.chapters,
                        selectedCount = state.selectedChapterIds.size,
                        newCount = state.chapters.count { chapter ->
                            chapter.stableKey !in state.downloadedStableKeys
                        },
                        isDownloading = state.isDownloading,
                        enableHaptics = enableHaptics,
                        onSetFavorite = onSetMangaSeriesFavorite,
                        onSetSubscribed = onSetMangaSeriesSubscribed,
                    )
                }

                items(
                    items = state.chapters,
                    key = { chapter -> chapterItemKey(chapter) },
                    contentType = { "chapter" }
                ) { chapter ->
                    MangaChapterRow(
                        chapter = chapter,
                        selected = chapter.chapterId in state.selectedChapterIds,
                        downloaded = chapter.stableKey in state.downloadedStableKeys,
                        enabled = !state.isDownloading,
                        enableHaptics = enableHaptics,
                        modifier = Modifier.animateItem(),
                        onToggle = { id, selected ->
                            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.LONG_PRESS)
                            onToggleChapter(id, selected)
                        },
                    )
                }
            }

            if (state.searchResults.isNotEmpty()) {
                item {
                    MangaSectionTitle(strings.mangaSearchResults, state.searchResults.size)
                }
                items(
                    items = state.searchResults,
                    key = { result -> result.seriesId },
                    contentType = { "search_result" }
                ) { result ->
                    MangaSearchResultCard(
                        result = result,
                        enabled = !state.isLoading && !state.isDownloading,
                        enableHaptics = enableHaptics,
                        modifier = Modifier.animateItem(),
                        onOpenSeries = onOpenSeries,
                    )
                }
            }

            item {
                Spacer(Modifier.height(if (state.isDownloading) 104.dp else 8.dp))
            }
        }

        AnimatedVisibility(
            visible = state.isDownloading,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
            enter = fadeIn() + slideInVertically { height -> height / 2 },
            exit = fadeOut() + slideOutVertically { height -> height / 2 },
        ) {
            MangaDownloadProgressOverlay(
                progressInfo = state.downloadProgress,
                selectedCount = state.selectedChapterIds.size,
            )
        }
    }

    if (state.subscriptionUpdatesVisible && state.subscriptionUpdates.isNotEmpty()) {
        MangaSubscriptionUpdatesDialog(
            updates = state.subscriptionUpdates,
            selectedChapterKeys = state.selectedSubscriptionUpdateChapterKeys,
            onToggleChapter = onToggleSubscriptionUpdateChapter,
            onSelectAll = onSelectAllSubscriptionUpdateChapters,
            onClearAll = onClearSubscriptionUpdateChapters,
            onDownload = onDownloadSubscriptionUpdates,
            onClose = onCloseSubscriptionUpdates,
            enableHaptics = enableHaptics,
        )
    }

    if (state.browserVisible) {
        MangaBrowserCard(
            url = state.browserUrl,
            currentUrl = state.currentWebUrl,
            sourceHomeUrl = state.sources
                .firstOrNull { source -> source.id == state.selectedSourceId }
                ?.homeUrl
                ?: state.browserUrl,
            userAgent = state.sources
                .firstOrNull { source -> source.id == state.selectedSourceId }
                ?.browserUserAgent,
            enableHaptics = enableHaptics,
            onClose = onCloseBrowser,
            onWebPageLoaded = onWebPageLoaded,
        )
    }
}

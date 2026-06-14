package com.cybercat.pocketbooksender.ui.screens

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import com.cybercat.pocketbooksender.ui.BitmapCache
import com.cybercat.pocketbooksender.ui.loadCachedRemoteBitmap
import com.cybercat.pocketbooksender.localization.LocalStrings
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import com.cybercat.pocketbooksender.data.manga.MangaChapter
import com.cybercat.pocketbooksender.data.manga.MangaSeriesBookmark
import com.cybercat.pocketbooksender.data.manga.MangaSeriesSearchResult
import com.cybercat.pocketbooksender.ui.MangaDownloadUiProgress
import com.cybercat.pocketbooksender.ui.MangaUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import java.net.URLEncoder

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
            MangaStatusMessageHost(text = state.errorMessage, isError = true)
        }

        item(key = "manga-status-host") {
            MangaStatusMessageHost(text = state.statusMessage, isError = false)
        }

        if (state.isLoading) {
            item(key = "manga-loading") {
                MangaLoadingCard(strings.mangaLoadingSource)
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
            selectedChapterIds = state.selectedSubscriptionUpdateChapterIds,
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

@Composable
private fun MangaSearchPanel(
    state: MangaUiState,
    onSearchChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenBrowser: () -> Unit,
    enableHaptics: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val configuration = LocalConfiguration.current
            val isWideScreen = configuration.screenWidthDp >= 640

            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MangaSearchField(
                        state = state,
                        onSearchChanged = onSearchChanged,
                        enableHaptics = enableHaptics,
                        modifier = Modifier.weight(1f),
                    )
                    MangaSearchButton(
                        state = state,
                        onSearch = onSearch,
                        enableHaptics = enableHaptics,
                        modifier = Modifier.width(180.dp),
                    )
                    if (!state.isAuthorized) {
                        MangaLoginButton(
                            state = state,
                            enableHaptics = enableHaptics,
                            onOpenBrowser = onOpenBrowser,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MangaSearchField(
                        state = state,
                        onSearchChanged = onSearchChanged,
                        enableHaptics = enableHaptics,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MangaSearchButton(
                            state = state,
                            onSearch = onSearch,
                            enableHaptics = enableHaptics,
                            modifier = Modifier.weight(1f),
                        )
                        if (!state.isAuthorized) {
                            MangaLoginButton(
                                state = state,
                                enableHaptics = enableHaptics,
                                onOpenBrowser = onOpenBrowser,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaSearchField(
    state: MangaUiState,
    onSearchChanged: (String) -> Unit,
    enableHaptics: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val enabled = !state.isLoading && !state.isDownloading
    OutlinedTextField(
        value = state.searchInput,
        onValueChange = onSearchChanged,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        label = { Text(strings.mangaSearchManga) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (state.searchInput.isNotEmpty() && enabled) {
                IconButton(onClick = {
                    view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                    onSearchChanged("")
                }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear")
                }
            }
        }
    )
}

@Composable
private fun MangaSearchButton(
    state: MangaUiState,
    onSearch: () -> Unit,
    enableHaptics: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    Button(
        onClick = {
            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.CONFIRM)
            onSearch()
        },
        enabled = state.searchInput.isNotBlank() && !state.isLoading && !state.isDownloading,
        modifier = modifier,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(strings.mangaSearchMangaPlaceholder)
    }
}

@Composable
private fun MangaLoginButton(
    state: MangaUiState,
    enableHaptics: Boolean,
    onOpenBrowser: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    OutlinedButton(
        onClick = {
            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
            onOpenBrowser()
        },
        enabled = !state.isDownloading,
    ) {
        Icon(Icons.Outlined.OpenInBrowser, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(strings.mangaBtnLogin)
    }
}

@Composable
private fun MangaDownloadProgressOverlay(
    progressInfo: MangaDownloadUiProgress?,
    selectedCount: Int,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val titleText = progressInfo?.title ?: strings.mangaDownloadPreparing
    val detailText = progressInfo?.detail ?: when (selectedCount) {
        0 -> strings.mangaDownloadPreparingChapters
        1 -> strings.mangaDownloadOneChapter
        else -> strings.get("manga_download_chapters_count", selectedCount)
    }
    val chapterText = progressInfo?.currentChapterTitle
    val progress = progressInfo?.progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "MangaDownloadProgress",
    )
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = contentColor,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (progress == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = contentColor,
                        strokeWidth = 3.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = contentColor,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = detailText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!chapterText.isNullOrBlank()) {
                        Text(
                            text = chapterText,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (progress == null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.24f),
                )
            } else {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.24f),
                )
            }
        }
    }
}

@Composable
private fun SavedMangaPanel(
    savedSeries: List<MangaSeriesBookmark>,
    isCheckingSubscriptions: Boolean,
    enabled: Boolean,
    enableHaptics: Boolean,
    onOpenSeries: (String) -> Unit,
    onCheckSubscriptions: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val subscribedCount = savedSeries.count { series -> series.subscribed }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = strings.mangaHeaderSaved,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedButton(
                    onClick = {
                        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                        onCheckSubscriptions()
                    },
                    enabled = enabled && subscribedCount > 0 && !isCheckingSubscriptions,
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isCheckingSubscriptions) strings.mangaBtnChecking else strings.mangaBtnCheckNew)
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                savedSeries.forEach { series ->
                    AssistChip(
                        onClick = {
                            if (enabled) {
                                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                onOpenSeries(series.seriesId)
                            }
                        },
                        modifier = Modifier.widthIn(max = 280.dp),
                        label = {
                            Text(
                                text = series.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            val icon = if (series.subscribed) {
                                Icons.Filled.Notifications
                            } else {
                                Icons.Outlined.Favorite
                            }
                            Icon(icon, contentDescription = null)
                        },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun MangaBrowserCard(
    url: String,
    currentUrl: String?,
    sourceHomeUrl: String,
    userAgent: String?,
    enableHaptics: Boolean,
    onClose: () -> Unit,
    onWebPageLoaded: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showLoginDialog by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, top = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = currentUrl ?: url,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = {
                        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                        showLoginDialog = true
                    }) {
                        Text(strings.mangaBtnLogin)
                    }
                    IconButton(onClick = {
                        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                        onClose()
                    }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close browser")
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { context ->
                        WebView(context).apply {
                            webViewRef = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            userAgent?.takeIf { it.isNotBlank() }?.let { value ->
                                settings.userAgentString = value
                            }
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, loadedUrl: String) {
                                    super.onPageFinished(view, loadedUrl)
                                    CookieManager.getInstance().flush()

                                    view.postDelayed(
                                        {
                                            view.extractHtml { html ->
                                                onWebPageLoaded(loadedUrl, html)
                                            }
                                        },
                                        HtmlExtractDelayMillis,
                                    )
                                }
                            }
                            loadUrl(url)
                        }
                    },
                    update = { webView ->
                        if (url.isNotBlank() && webView.url != url) {
                            webView.loadUrl(url)
                        }
                    },
                )
            }
        }
    }

    if (showLoginDialog) {
        ComxNativeLoginDialog(
            onDismiss = { showLoginDialog = false },
            onSubmit = { loginName, loginPassword, doNotRemember ->
                val targetUrl = webViewRef?.url
                    ?.takeIf { loadedUrl -> loadedUrl.startsWith(sourceHomeUrl) }
                    ?: sourceHomeUrl
                webViewRef?.postUrl(
                    targetUrl,
                    buildComxLoginPostBody(loginName, loginPassword, doNotRemember),
                )
                showLoginDialog = false
            },
        )
    }
}

@Composable
private fun ComxNativeLoginDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, Boolean) -> Unit,
) {
    var loginName by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var doNotRemember by remember { mutableStateOf(false) }
    val canSubmit = loginName.isNotBlank() && loginPassword.isNotBlank()

    val strings = LocalStrings.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = strings.mangaLoginTitle,
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = loginName,
                    onValueChange = { loginName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.mangaUsername) },
                )
                OutlinedTextField(
                    value = loginPassword,
                    onValueChange = { loginPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.mangaPassword) },
                    visualTransformation = PasswordVisualTransformation(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = doNotRemember,
                        onCheckedChange = { doNotRemember = it },
                    )
                    Text(strings.mangaDoNotRemember)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.opdsBtnCancel)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSubmit(loginName.trim(), loginPassword, doNotRemember) },
                        enabled = canSubmit,
                    ) {
                        Text(strings.mangaBtnLogin)
                    }
                }
            }
        }
    }
}

private fun buildComxLoginPostBody(
    loginName: String,
    loginPassword: String,
    doNotRemember: Boolean,
): ByteArray {
    val fields = buildList {
        add("login_name" to loginName)
        add("login_password" to loginPassword)
        if (doNotRemember) add("login_not_save" to "1")
        add("login" to "submit")
    }
    return fields.joinToString("&") { (key, value) ->
        "${key.formEncode()}=${value.formEncode()}"
    }.toByteArray(Charsets.UTF_8)
}

private fun String.formEncode(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name())

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

        val longPressReached: Boolean = withTimeoutOrNull<Boolean>(MangaLongPressMillis) {
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

@Composable
private fun MangaSearchResultCard(
    result: MangaSeriesSearchResult,
    enabled: Boolean,
    enableHaptics: Boolean,
    modifier: Modifier = Modifier,
    onOpenSeries: (String) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    ElevatedCard(
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                onOpenSeries(result.seriesId)
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MangaCover(
                coverUrl = result.coverUrl,
                title = result.title,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                result.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val strings = LocalStrings.current
            TextButton(
                onClick = { onOpenSeries(result.seriesId) },
                enabled = enabled,
            ) {
                Text(strings.mangaBtnOpen)
            }
        }
    }
}

@Composable
private fun SelectedSeriesCard(
    title: String,
    description: String?,
    coverUrl: String?,
    isFavorite: Boolean,
    isSubscribed: Boolean,
    lastDownloadedChapter: String?,
    lastReadChapter: String?,
    chapters: List<MangaChapter>,
    selectedCount: Int,
    newCount: Int,
    isDownloading: Boolean,
    enableHaptics: Boolean,
    onSetFavorite: (Boolean) -> Unit,
    onSetSubscribed: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MangaCover(coverUrl = coverUrl, title = title)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = strings.get("manga_series_chapters_summary", chapters.size, newCount, selectedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    lastDownloadedChapter?.let { chapter ->
                        Text(
                            text = strings.get("manga_last_downloaded", chapter),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    lastReadChapter?.let { chapter ->
                        Text(
                            text = strings.get("manga_last_read", chapter),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            description?.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                        onSetFavorite(!isFavorite)
                    },
                    enabled = !isDownloading,
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isFavorite) strings.mangaBtnFavorite else strings.mangaBtnAddFavorite)
                }
                OutlinedButton(
                    onClick = {
                        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                        onSetSubscribed(!isSubscribed)
                    },
                    enabled = !isDownloading,
                ) {
                    Icon(
                        imageVector = if (isSubscribed) {
                            Icons.Filled.Notifications
                        } else {
                            Icons.Outlined.NotificationsNone
                        },
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isSubscribed) strings.mangaBtnSubscribed else strings.mangaBtnSubscribe)
                }
            }
        }
    }
}

@Composable
private fun MangaChapterRow(
    chapter: MangaChapter,
    selected: Boolean,
    downloaded: Boolean,
    enabled: Boolean,
    enableHaptics: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    ElevatedCard(
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                onToggle(chapter.chapterId, !selected)
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = {
                    view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                    onToggle(chapter.chapterId, it)
                },
                enabled = enabled,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                chapter.numberForSort?.let { number ->
                    Text(
                        text = strings.get("manga_chapter_number", number.formatChapterNumber()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (downloaded) {
                AssistChip(
                    onClick = {},
                    label = { Text(LocalStrings.current.mangaStatusDone) },
                    enabled = false,
                )
            }
        }
    }
}

@Composable
private fun MangaCover(
    coverUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(coverUrl) { mutableStateOf<Bitmap?>(coverUrl?.let { BitmapCache.getFromMemory(it) }) }

    LaunchedEffect(coverUrl) {
        if (coverUrl != null && bitmap == null) {
            delay(CoverLoadDelayMillis)
            val cookie = withContext(Dispatchers.IO) {
                runCatching { CookieManager.getInstance().getCookie(coverUrl) }.getOrNull()
            }
            if (bitmap == null) {
                bitmap = loadCachedRemoteBitmap(
                    context = context,
                    url = coverUrl,
                    cookie = cookie,
                    reqWidth = CoverRequestWidth,
                    reqHeight = CoverRequestHeight,
                )
            }
        }
    }

    Surface(
        modifier = modifier.size(width = 58.dp, height = 78.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        val cover = bitmap
        if (cover != null) {
            ComposeImage(
                bitmap = cover.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MangaSectionTitle(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = count.toString(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun MangaStatusMessage(
    text: String,
    isError: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isError) Icons.Outlined.Close else Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            )
        }
    }
}

@Composable
private fun MangaStatusMessageHost(
    text: String?,
    isError: Boolean,
) {
    var lastText by remember { mutableStateOf(text.orEmpty()) }

    LaunchedEffect(text) {
        if (!text.isNullOrBlank()) {
            lastText = text
        }
    }

    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn() + expandVertically() + slideInVertically { height -> -height / 4 },
        exit = fadeOut() + shrinkVertically() + slideOutVertically { height -> -height / 4 },
    ) {
        MangaStatusMessage(
            text = lastText,
            isError = isError,
        )
    }
}

@Composable
private fun MangaLoadingCard(text: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun WebView.extractHtml(onHtml: (String) -> Unit) {
    evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { encoded ->
        val html = runCatching {
            JSONArray("[$encoded]").getString(0)
        }.getOrDefault("")
        if (html.isNotBlank()) {
            onHtml(html)
        }
    }
}

private data class ChapterPointerTarget(
    val index: Int,
    val chapterId: String,
)

private fun chapterItemKey(chapter: MangaChapter): String =
    "chapter:${chapter.stableKey}"

private fun Double.formatChapterNumber(): String {
    if (!java.lang.Double.isFinite(this)) return toString()
    val wholeNumber = toLong()
    return if (this == wholeNumber.toDouble()) {
        wholeNumber.toString()
    } else {
        toString()
    }
}

internal fun MangaUiState.selectedSeriesItemIndex(): Int {
    var index = 1
    if (errorMessage != null) index++
    if (statusMessage != null) index++
    if (isLoading) index++
    return index
}

private const val MangaLongPressMillis = 300L
private const val CoverLoadDelayMillis = 120L
private const val CoverRequestWidth = 160
private const val CoverRequestHeight = 220

private const val HtmlExtractDelayMillis = 900L

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MangaSubscriptionUpdatesDialog(
    updates: List<com.cybercat.pocketbooksender.data.manga.MangaSubscriptionCheckResult>,
    selectedChapterIds: Set<String>,
    onToggleChapter: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onDownload: () -> Unit,
    onClose: () -> Unit,
    enableHaptics: Boolean,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val selectedCount = selectedChapterIds.size

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = strings.mangaUpdatesTitle,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AssistChip(
                        onClick = {
                            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                            onSelectAll()
                        },
                        label = { Text(strings.mangaUpdatesSelectAll) }
                    )
                    AssistChip(
                        onClick = {
                            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                            onClearAll()
                        },
                        label = { Text(strings.mangaUpdatesDeselectAll) }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    updates.forEach { update ->
                        val series = update.page.details
                        item(key = series.seriesId) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    MangaCover(
                                        coverUrl = series.coverUrl,
                                        title = series.title,
                                        modifier = Modifier.size(40.dp, 60.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = series.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                update.newChapters.forEach { chapter ->
                                    val isSelected = chapter.chapterId in selectedChapterIds
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                                onToggleChapter(chapter.chapterId, !isSelected)
                                            }
                                            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                                onToggleChapter(chapter.chapterId, checked)
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = chapter.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onClose) {
                        Text(strings.mangaUpdatesBtnCancel)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.CONFIRM)
                            onDownload()
                        },
                        enabled = selectedCount > 0
                    ) {
                        Text(strings.get("manga_updates_btn_download", selectedCount))
                    }
                }
            }
        }
    }
}

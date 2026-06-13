package com.cybercat.pocketbooksender.ui.screens

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import com.cybercat.pocketbooksender.ui.BitmapCache
import com.cybercat.pocketbooksender.ui.loadCachedRemoteBitmap
import com.cybercat.pocketbooksender.data.manga.MangaChapter
import com.cybercat.pocketbooksender.data.manga.MangaSeriesBookmark
import com.cybercat.pocketbooksender.data.manga.MangaSeriesSearchResult
import com.cybercat.pocketbooksender.ui.MangaUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray

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
    onSetSeriesFavorite: (Boolean) -> Unit,
    onSetSeriesSubscribed: (Boolean) -> Unit,
    onCheckSubscriptions: () -> Unit,
    onDownloadSelected: () -> Unit,
) {
    val selectedChapterIdsState = rememberUpdatedState(state.selectedChapterIds)
    val onToggleChapterState = rememberUpdatedState(onToggleChapter)
    val selectedSeriesId = state.selectedSeries?.seriesId
    val chapterTargets = remember(state.chapters) {
        state.chapters.mapIndexed { index, chapter ->
            chapterItemKey(chapter) to ChapterPointerTarget(
                index = index,
                chapterId = chapter.chapterId,
            )
        }.toMap()
    }

    LaunchedEffect(selectedSeriesId) {
        if (selectedSeriesId != null) {
            listState.animateScrollToItem(state.selectedSeriesItemIndex())
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
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

                    detectDragGesturesAfterLongPress(
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
        item {
            MangaSearchPanel(
                state = state,
                onSearchChanged = onSearchChanged,
                onSearch = onSearch,
                onOpenBrowser = onOpenBrowser,
            )
        }

        if (state.savedSeries.isNotEmpty()) {
            item {
                SavedMangaPanel(
                    savedSeries = state.savedSeries,
                    isCheckingSubscriptions = state.isCheckingSubscriptions,
                    enabled = !state.isLoading && !state.isDownloading,
                    onOpenSeries = onOpenSeries,
                    onCheckSubscriptions = onCheckSubscriptions,
                )
            }
        }

        if (state.browserVisible) {
            item {
                MangaBrowserCard(
                    url = state.browserUrl,
                    currentUrl = state.currentWebUrl,
                    onClose = onCloseBrowser,
                    onWebPageLoaded = onWebPageLoaded,
                )
            }
        }

        state.errorMessage?.let { message ->
            item {
                MangaStatusMessage(text = message, isError = true)
            }
        }

        state.statusMessage?.let { message ->
            item {
                MangaStatusMessage(text = message, isError = false)
            }
        }

        if (state.isLoading) {
            item {
                MangaLoadingCard("Loading manga source")
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

            item {
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
                    downloadProgressText = state.downloadProgressText,
                    onSetFavorite = onSetSeriesFavorite,
                    onSetSubscribed = onSetSeriesSubscribed,
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
                    onToggle = onToggleChapter,
                )
            }
        }

        if (state.searchResults.isNotEmpty()) {
            item {
                MangaSectionTitle("Search results", state.searchResults.size)
            }
            items(
                items = state.searchResults,
                key = { result -> result.seriesId },
                contentType = { "search_result" }
            ) { result ->
                MangaSearchResultCard(
                    result = result,
                    enabled = !state.isLoading && !state.isDownloading,
                    onOpenSeries = onOpenSeries,
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MangaSearchPanel(
    state: MangaUiState,
    onSearchChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenBrowser: () -> Unit,
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
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 640.dp) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MangaSearchField(
                            state = state,
                            onSearchChanged = onSearchChanged,
                            modifier = Modifier.weight(1f),
                        )
                        MangaSearchButton(
                            state = state,
                            onSearch = onSearch,
                            modifier = Modifier.width(180.dp),
                        )
                        if (!state.isAuthorized) {
                            MangaLoginButton(
                                state = state,
                                onOpenBrowser = onOpenBrowser,
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MangaSearchField(
                            state = state,
                            onSearchChanged = onSearchChanged,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MangaSearchButton(
                                state = state,
                                onSearch = onSearch,
                                modifier = Modifier.weight(1f),
                            )
                            if (!state.isAuthorized) {
                                MangaLoginButton(
                                    state = state,
                                    onOpenBrowser = onOpenBrowser,
                                )
                            }
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
    modifier: Modifier = Modifier,
) {
    val enabled = !state.isLoading && !state.isDownloading
    OutlinedTextField(
        value = state.searchInput,
        onValueChange = onSearchChanged,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        label = { Text("Search manga") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (state.searchInput.isNotEmpty() && enabled) {
                IconButton(onClick = { onSearchChanged("") }) {
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
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onSearch,
        enabled = state.searchInput.isNotBlank() && !state.isLoading && !state.isDownloading,
        modifier = modifier,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Search")
    }
}

@Composable
private fun MangaLoginButton(
    state: MangaUiState,
    onOpenBrowser: () -> Unit,
) {
    OutlinedButton(
        onClick = onOpenBrowser,
        enabled = !state.isDownloading,
    ) {
        Icon(Icons.Outlined.OpenInBrowser, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Login")
    }
}

@Composable
private fun SavedMangaPanel(
    savedSeries: List<MangaSeriesBookmark>,
    isCheckingSubscriptions: Boolean,
    enabled: Boolean,
    onOpenSeries: (String) -> Unit,
    onCheckSubscriptions: () -> Unit,
) {
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
                    text = "Saved manga",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedButton(
                    onClick = onCheckSubscriptions,
                    enabled = enabled && subscribedCount > 0 && !isCheckingSubscriptions,
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isCheckingSubscriptions) "Checking" else "Check new")
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                savedSeries.forEach { series ->
                    AssistChip(
                        onClick = { if (enabled) onOpenSeries(series.seriesId) },
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
    onClose: () -> Unit,
    onWebPageLoaded: (String, String) -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 10.dp, end = 6.dp),
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
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close browser")
                }
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp, max = 520.dp),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
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

@Composable
private fun MangaSearchResultCard(
    result: MangaSeriesSearchResult,
    enabled: Boolean,
    onOpenSeries: (String) -> Unit,
) {
    ElevatedCard(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onOpenSeries(result.seriesId) },
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
            TextButton(
                onClick = { onOpenSeries(result.seriesId) },
                enabled = enabled,
            ) {
                Text("Open")
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
    downloadProgressText: String?,
    onSetFavorite: (Boolean) -> Unit,
    onSetSubscribed: (Boolean) -> Unit,
) {
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
                        text = "${chapters.size} chapters · $newCount new · $selectedCount selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    lastDownloadedChapter?.let { chapter ->
                        Text(
                            text = "Last downloaded: $chapter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    lastReadChapter?.let { chapter ->
                        Text(
                            text = "Last read: $chapter",
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
                    onClick = { onSetFavorite(!isFavorite) },
                    enabled = !isDownloading,
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isFavorite) "Favorite" else "Favorite")
                }
                OutlinedButton(
                    onClick = { onSetSubscribed(!isSubscribed) },
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
                    Text(if (isSubscribed) "Subscribed" else "Subscribe")
                }
            }



            if (isDownloading) {
                downloadProgressText?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LinearProgressIndicator(Modifier.fillMaxWidth())
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
    onToggle: (String, Boolean) -> Unit,
) {
    ElevatedCard(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onToggle(chapter.chapterId, !selected) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle(chapter.chapterId, it) },
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
                        text = "Chapter $number",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (downloaded) {
                AssistChip(
                    onClick = {},
                    label = { Text("Done") },
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
        modifier = Modifier.size(width = 58.dp, height = 78.dp),
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
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
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

internal fun MangaUiState.selectedSeriesItemIndex(): Int {
    var index = 1 // Search panel is always the first list item.
    if (browserVisible) index++
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

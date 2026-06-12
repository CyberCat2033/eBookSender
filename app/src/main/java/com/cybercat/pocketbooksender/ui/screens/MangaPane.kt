package com.cybercat.pocketbooksender.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image as ComposeImage
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cybercat.pocketbooksender.data.manga.MangaChapter
import com.cybercat.pocketbooksender.data.manga.MangaSeriesSearchResult
import com.cybercat.pocketbooksender.ui.MangaUiState
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Composable
fun MangaPane(
    state: MangaUiState,
    modifier: Modifier = Modifier,
    onSearchChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenBrowser: () -> Unit,
    onCloseBrowser: () -> Unit,
    onWebPageLoaded: (String, String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onToggleChapter: (String, Boolean) -> Unit,
    onSelectNewChapters: () -> Unit,
    onSelectAllChapters: () -> Unit,
    onClearChapterSelection: () -> Unit,
    onDownloadSelected: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
            item {
                SelectedSeriesCard(
                    title = series.title,
                    description = series.description,
                    coverUrl = series.coverUrl,
                    chapters = state.chapters,
                    selectedCount = state.selectedChapterIds.size,
                    newCount = state.chapters.count { chapter ->
                        chapter.stableKey !in state.downloadedStableKeys
                    },
                    isDownloading = state.isDownloading,
                    downloadProgressText = state.downloadProgressText,
                    onSelectNewChapters = onSelectNewChapters,
                    onSelectAllChapters = onSelectAllChapters,
                    onClearChapterSelection = onClearChapterSelection,
                    onDownloadSelected = onDownloadSelected,
                )
            }

            items(state.chapters, key = { chapter -> chapter.stableKey }) { chapter ->
                MangaChapterRow(
                    chapter = chapter,
                    selected = chapter.chapterId in state.selectedChapterIds,
                    downloaded = chapter.stableKey in state.downloadedStableKeys,
                    enabled = !state.isDownloading,
                    onToggle = { selected -> onToggleChapter(chapter.chapterId, selected) },
                )
            }
        }

        if (state.searchResults.isNotEmpty()) {
            item {
                MangaSectionTitle("Search results", state.searchResults.size)
            }
            items(state.searchResults, key = { result -> result.seriesId }) { result ->
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
                        MangaLoginButton(
                            state = state,
                            onOpenBrowser = onOpenBrowser,
                        )
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

@Composable
private fun MangaSearchField(
    state: MangaUiState,
    onSearchChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = state.searchInput,
        onValueChange = onSearchChanged,
        modifier = modifier,
        enabled = !state.isLoading && !state.isDownloading,
        singleLine = true,
        label = { Text("Search manga") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
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
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
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
    chapters: List<MangaChapter>,
    selectedCount: Int,
    newCount: Int,
    isDownloading: Boolean,
    downloadProgressText: String?,
    onSelectNewChapters: () -> Unit,
    onSelectAllChapters: () -> Unit,
    onClearChapterSelection: () -> Unit,
    onDownloadSelected: () -> Unit,
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
                    onClick = onSelectNewChapters,
                    enabled = !isDownloading && newCount > 0,
                ) {
                    Icon(Icons.Outlined.Checklist, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New")
                }
                OutlinedButton(
                    onClick = onSelectAllChapters,
                    enabled = !isDownloading && chapters.isNotEmpty(),
                ) {
                    Icon(Icons.Outlined.SelectAll, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("All")
                }
                OutlinedButton(
                    onClick = onClearChapterSelection,
                    enabled = !isDownloading && selectedCount > 0,
                ) {
                    Text("Clear")
                }
            }

            Button(
                onClick = onDownloadSelected,
                enabled = !isDownloading && selectedCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(Modifier.size(18.dp))
                } else {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(if (isDownloading) "Downloading" else "Download selected")
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
    onToggle: (Boolean) -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = onToggle,
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
    var bitmap by remember(coverUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(coverUrl) {
        bitmap = coverUrl?.let { loadMangaBitmap(it) }
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

private suspend fun loadMangaBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "image/*")
            setRequestProperty("User-Agent", "PocketBookSender/0.1")
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { cookie ->
                setRequestProperty("Cookie", cookie)
            }
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.use(BitmapFactory::decodeStream)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}

private const val HtmlExtractDelayMillis = 900L

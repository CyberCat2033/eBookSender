package com.cybercat.pocketbooksender.feature.manga

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cybercat.pocketbooksender.data.manga.MangaChapter
import com.cybercat.pocketbooksender.data.manga.MangaChapterDownload
import com.cybercat.pocketbooksender.data.manga.MangaSeriesBookmark
import com.cybercat.pocketbooksender.data.manga.MangaSeriesDetails
import com.cybercat.pocketbooksender.data.manga.MangaSeriesSearchResult
import com.cybercat.pocketbooksender.data.manga.MangaSourceSummary
import com.cybercat.pocketbooksender.data.manga.MangaSubscriptionCheckResult
import com.cybercat.pocketbooksender.data.manga.buildComxLoginPostBody
import com.cybercat.pocketbooksender.localization.LocalStrings
import com.cybercat.pocketbooksender.ui.AnimatedAlertDialog
import com.cybercat.pocketbooksender.ui.BitmapCache
import com.cybercat.pocketbooksender.ui.LocalDismissDialog
import com.cybercat.pocketbooksender.ui.loadCachedRemoteBitmap
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray

@Composable
internal fun MangaSearchPanel(
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
internal fun MangaSearchField(
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
internal fun MangaSearchButton(
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
internal fun MangaLoginButton(
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
internal fun MangaDownloadProgressOverlay(
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
internal fun SavedMangaPanel(
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
internal fun MangaBrowserCard(
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
internal fun ComxNativeLoginDialog(
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

@Composable
internal fun MangaSearchResultCard(
    result: MangaSeriesSearchResult,
    enabled: Boolean,
    enableHaptics: Boolean,
    modifier: Modifier = Modifier,
    onOpenSeries: (String) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    ElevatedCard(
        onClick = {
            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
            onOpenSeries(result.seriesId)
        },
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
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
internal fun SelectedSeriesCard(
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
internal fun MangaChapterRow(
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
        onClick = {
            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
            onToggle(chapter.chapterId, !selected)
        },
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
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
internal fun MangaCover(
    coverUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    com.cybercat.pocketbooksender.ui.RemoteCover(
        coverUrl = coverUrl,
        title = title,
        modifier = modifier,
        useCookies = true,
    )
}

@Composable
internal fun MangaSectionTitle(title: String, count: Int) {
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


internal fun WebView.extractHtml(onHtml: (String) -> Unit) {
    evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { encoded ->
        val html = runCatching {
            JSONArray("[$encoded]").getString(0)
        }.getOrDefault("")
        if (html.isNotBlank()) {
            onHtml(html)
        }
    }
}

internal data class ChapterPointerTarget(
    val index: Int,
    val chapterId: String,
)

internal fun chapterItemKey(chapter: MangaChapter): String =
    "chapter:${chapter.stableKey}"

internal fun Double.formatChapterNumber(): String {
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun MangaSubscriptionUpdatesDialog(
    updates: List<MangaSubscriptionCheckResult>,
    selectedChapterKeys: Set<String>,
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
    val selectedCount = selectedChapterKeys.size
    val seriesKeys = remember(updates) {
        updates.map { update -> update.page.details.subscriptionUpdateSeriesKey() }
    }
    val updatesStateKey = remember(seriesKeys) { seriesKeys.joinToString(separator = "\n") }
    var collapsedSeriesKeys by rememberSaveable(updatesStateKey) { mutableStateOf<List<String>>(emptyList()) }
    var downloadAfterDismiss by remember { mutableStateOf(false) }

    fun toggleSeriesCollapsed(seriesKey: String) {
        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
        collapsedSeriesKeys = if (seriesKey in collapsedSeriesKeys) {
            collapsedSeriesKeys - seriesKey
        } else {
            collapsedSeriesKeys + seriesKey
        }
    }

    AnimatedAlertDialog(
        onDismissRequest = {
            if (downloadAfterDismiss) {
                downloadAfterDismiss = false
                onDownload()
            } else {
                onClose()
            }
        },
        modifier = Modifier.heightIn(max = 520.dp),
        title = {
            val dismiss = LocalDismissDialog.current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = strings.mangaUpdatesTitle,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                        dismiss()
                    }
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AssistChip(
                        onClick = {
                            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                            onSelectAll()
                        },
                        label = { Text(strings.mangaUpdatesSelectAll) },
                    )
                    AssistChip(
                        onClick = {
                            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                            onClearAll()
                        },
                        label = { Text(strings.mangaUpdatesDeselectAll) },
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    updates.forEach { update ->
                        val series = update.page.details
                        val seriesKey = series.subscriptionUpdateSeriesKey()
                        val collapsed = seriesKey in collapsedSeriesKeys

                        item(key = seriesKey) {
                            val seriesInteractionSource = remember(seriesKey) { MutableInteractionSource() }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = seriesInteractionSource,
                                            indication = null,
                                        ) {
                                            toggleSeriesCollapsed(seriesKey)
                                        }
                                        .padding(vertical = 4.dp),
                                ) {
                                    MangaCover(
                                        coverUrl = series.coverUrl,
                                        title = series.title,
                                        modifier = Modifier.size(40.dp, 60.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = series.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    val rotationState by animateFloatAsState(
                                        targetValue = if (collapsed) 0f else 180f,
                                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                        label = "SubscriptionUpdateSeriesChevronRotation",
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.ExpandMore,
                                        contentDescription = if (collapsed) {
                                            strings.catalogActionExpand
                                        } else {
                                            strings.catalogActionCollapse
                                        },
                                        modifier = Modifier.rotate(rotationState),
                                    )
                                }

                                AnimatedVisibility(
                                    visible = !collapsed,
                                    enter = expandVertically(
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        expandFrom = Alignment.Top,
                                    ) + fadeIn(
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    ),
                                    exit = shrinkVertically(
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        shrinkTowards = Alignment.Top,
                                    ) + fadeOut(
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(top = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        update.newChapters.forEach { chapter ->
                                            val chapterKey = chapter.subscriptionUpdateSelectionKey()
                                            val isSelected = chapterKey in selectedChapterKeys

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        view.performHapticIfAllowed(
                                                            context,
                                                            enableHaptics,
                                                            HapticFeedbackConstants.VIRTUAL_KEY,
                                                        )
                                                        onToggleChapter(chapterKey, !isSelected)
                                                    }
                                                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                                            ) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = { checked ->
                                                        view.performHapticIfAllowed(
                                                            context,
                                                            enableHaptics,
                                                            HapticFeedbackConstants.VIRTUAL_KEY,
                                                        )
                                                        onToggleChapter(chapterKey, checked)
                                                    },
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = chapter.title,
                                                    style = MaterialTheme.typography.bodyMedium,
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
                }
            }
        },
        dismissButton = {
            val dismiss = LocalDismissDialog.current
            TextButton(
                onClick = {
                    view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                    downloadAfterDismiss = false
                    dismiss()
                }
            ) {
                Text(strings.mangaUpdatesBtnCancel)
            }
        },
        confirmButton = {
            val dismiss = LocalDismissDialog.current
            Button(
                onClick = {
                    view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.CONFIRM)
                    downloadAfterDismiss = true
                    dismiss()
                },
                enabled = selectedCount > 0
            ) {
                Text(strings.get("manga_updates_btn_download", selectedCount))
            }
        }
    )
}

private const val CoverLoadDelayMillis = 120L
private const val CoverRequestWidth = 160
private const val CoverRequestHeight = 220
private const val HtmlExtractDelayMillis = 900L

@Composable
fun MangaSelectionActions(
    enabled: Boolean,
    hasNewChapters: Boolean,
    hasChapters: Boolean,
    enableHaptics: Boolean,
    onSelectNew: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    IconButton(
        onClick = {
            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
            onSelectNew()
        },
        enabled = enabled && hasNewChapters,
    ) {
        Icon(Icons.Outlined.Checklist, contentDescription = "Select new chapters")
    }
    IconButton(
        onClick = {
            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
            onSelectAll()
        },
        enabled = enabled && hasChapters,
    ) {
        Icon(Icons.Outlined.SelectAll, contentDescription = "Select all chapters")
    }
    IconButton(
        onClick = {
            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
            onClear()
        },
        enabled = enabled,
    ) {
        Icon(Icons.Outlined.Close, contentDescription = "Clear chapter selection")
    }
}

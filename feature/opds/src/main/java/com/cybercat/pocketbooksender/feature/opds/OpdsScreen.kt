package com.cybercat.pocketbooksender.feature.opds

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cybercat.pocketbooksender.data.opds.OpdsAcquisition
import com.cybercat.pocketbooksender.data.opds.OpdsEntry
import com.cybercat.pocketbooksender.data.opds.OpdsLink
import com.cybercat.pocketbooksender.data.opds.OpdsSource
import com.cybercat.pocketbooksender.localization.LocalStrings
import com.cybercat.pocketbooksender.ui.LoadingCard
import com.cybercat.pocketbooksender.ui.LocalAdaptiveLayoutInfo
import com.cybercat.pocketbooksender.ui.StatusMessage
import com.cybercat.pocketbooksender.ui.StatusMessageHost
import com.cybercat.pocketbooksender.ui.theme.EmphasizedEasing
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import kotlinx.coroutines.launch

private const val WEB_MODE_ENTER_DURATION_MILLIS = 220
private const val WEB_MODE_EXIT_DURATION_MILLIS = 140
private const val OPDS_CONTENT_ENTER_DURATION_MILLIS = 220
private const val OPDS_CONTENT_FADE_DURATION_MILLIS = 160

private val OPDS_FEED_FADE_IN_SPEC = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private val OPDS_FEED_FADE_OUT_SPEC = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

private val OPDS_FEED_PLACEMENT_SPEC = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsScreen(
    state: OpdsUiState,
    opdsListState: LazyListState,
    onSearchChanged: (String) -> Unit,
    onWebModeSelected: (WebContentMode) -> Unit,
    onSaveSource: (String, String, String?, String?) -> Unit,
    onRemoveSource: (String) -> Unit,
    onOpenSource: (String) -> Unit,
    onOpenLink: (OpdsLink) -> Unit,
    onBack: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onSearch: () -> Unit,
    onDownload: (OpdsEntry, OpdsAcquisition) -> Unit,
    onCancelDownload: () -> Unit,
    onAuthUsernameChanged: (String) -> Unit,
    onAuthPasswordChanged: (String) -> Unit,
    onDismissAuthDialog: () -> Unit,
    onSaveCredentials: () -> Unit,
    onOpenCredentialsEdit: (OpdsSource) -> Unit,
    enableHaptics: Boolean,

    // Slots for Manga to keep modules independent
    mangaPane: @Composable () -> Unit,
    mangaTopBarActions: @Composable () -> Unit,
    mangaTopBarNavigationIcon: @Composable () -> Unit,
    mangaFloatingActionButton: @Composable () -> Unit,
    isMangaSelectionActive: Boolean,
    mangaSelectedChapterCount: Int,
    onClearMangaSelection: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val adaptiveLayout = LocalAdaptiveLayoutInfo.current
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var newSourceUrl by remember { mutableStateOf("") }
    var newSourceTitle by remember { mutableStateOf("") }
    var newSourceUsername by remember { mutableStateOf("") }
    var newSourcePassword by remember { mutableStateOf("") }
    val webMode = state.webMode
    val canHandleOpdsBack =
        webMode == WebContentMode.Opds && state.canGoBack && !state.isLoading &&
            !state.isDownloading
    val opdsStartLink = remember(webMode, state.catalog, state.currentUrl, state.sources) {
        if (webMode != WebContentMode.Opds) {
            null
        } else {
            state.catalog
                ?.links
                ?.firstOrNull { link ->
                    link.isStartLink() &&
                        !link.isRedundantStartLink(
                            currentUrl = state.currentUrl,
                            sources = state.sources
                        )
                }
        }
    }

    BackHandler(enabled = canHandleOpdsBack) {
        onBack()
    }

    BackHandler(enabled = webMode == WebContentMode.Manga && isMangaSelectionActive) {
        onClearMangaSelection()
    }

    if (showAddSourceDialog) {
        AddSourceDialog(
            url = newSourceUrl,
            title = newSourceTitle,
            username = newSourceUsername,
            password = newSourcePassword,
            onUrlChanged = { newSourceUrl = it },
            onTitleChanged = { newSourceTitle = it },
            onUsernameChanged = { newSourceUsername = it },
            onPasswordChanged = { newSourcePassword = it },
            onDismiss = { showAddSourceDialog = false },
            onSaveSource = {
                onSaveSource(
                    newSourceTitle,
                    newSourceUrl,
                    newSourceUsername.ifBlank {
                        null
                    },
                    newSourcePassword.ifBlank { null }
                )
            }
        )
    }

    val authDialog = state.authDialog
    var credentialsDialogVisible by remember { mutableStateOf(false) }
    LaunchedEffect(authDialog.isVisible) {
        if (authDialog.isVisible) credentialsDialogVisible = true
    }

    if (credentialsDialogVisible) {
        OpdsCredentialsDialog(
            sourceTitle = authDialog.sourceTitle,
            username = authDialog.username,
            password = authDialog.password,
            onUsernameChanged = onAuthUsernameChanged,
            onPasswordChanged = onAuthPasswordChanged,
            onDismiss = {
                credentialsDialogVisible = false
                onDismissAuthDialog()
            },
            onSave = onSaveCredentials
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isMangaSelectionActive) {
                            strings.get("generic_selected_count", mangaSelectedChapterCount)
                        } else {
                            strings.navWeb
                        }
                    )
                },
                navigationIcon = {
                    if (webMode == WebContentMode.Opds && state.canGoBack) {
                        IconButton(
                            onClick = {
                                view.performHapticIfAllowed(
                                    context,
                                    enableHaptics,
                                    HapticFeedbackConstants.VIRTUAL_KEY
                                )
                                onBack()
                            },
                            enabled = canHandleOpdsBack
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = strings.get("action_back")
                            )
                        }
                    } else if (webMode == WebContentMode.Manga) {
                        mangaTopBarNavigationIcon()
                    }
                },
                actions = {
                    if (isMangaSelectionActive) {
                        mangaTopBarActions()
                    } else if (webMode == WebContentMode.Opds) {
                        opdsStartLink?.let { startLink ->
                            IconButton(
                                onClick = {
                                    view.performHapticIfAllowed(
                                        context,
                                        enableHaptics,
                                        HapticFeedbackConstants.VIRTUAL_KEY
                                    )
                                    onOpenLink(startLink)
                                },
                                enabled = !state.isLoading && !state.isDownloading
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Home,
                                    contentDescription = strings.opdsRelStart
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                newSourceUrl = ""
                                newSourceTitle = ""
                                newSourceUsername = ""
                                newSourcePassword = ""
                                showAddSourceDialog = true
                            },
                            enabled = !state.isDownloading
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = strings.get("opds_action_add_source")
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (webMode == WebContentMode.Manga) {
                mangaFloatingActionButton()
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val contentMaxWidth = if (maxWidth >= 900.dp) 980.dp else maxWidth
            Column(
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = adaptiveLayout.screenHorizontalPadding)
            ) {
                WebModeSelector(
                    selectedMode = webMode,
                    enableHaptics = enableHaptics,
                    onModeSelected = onWebModeSelected,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Box(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = webMode,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            val direction = if (targetState.ordinal >
                                initialState.ordinal
                            ) {
                                1
                            } else {
                                -1
                            }
                            (
                                slideInHorizontally(
                                    animationSpec = tween(
                                        durationMillis = WEB_MODE_ENTER_DURATION_MILLIS,
                                        easing = EmphasizedEasing
                                    ),
                                    initialOffsetX = { width -> direction * width / 5 }
                                ) + fadeIn(
                                    animationSpec = tween(
                                        durationMillis = WEB_MODE_ENTER_DURATION_MILLIS
                                    )
                                )
                                ).togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(
                                        durationMillis = WEB_MODE_EXIT_DURATION_MILLIS,
                                        easing = EmphasizedEasing
                                    ),
                                    targetOffsetX = { width -> -direction * width / 8 }
                                ) + fadeOut(
                                    animationSpec = tween(
                                        durationMillis = WEB_MODE_EXIT_DURATION_MILLIS
                                    )
                                )
                            )
                        },
                        label = "WebModeContentAnimation"
                    ) { targetMode ->
                        when (targetMode) {
                            WebContentMode.Manga -> mangaPane()

                            WebContentMode.Opds -> OpdsCatalogContent(
                                state = state,
                                opdsListState = opdsListState,
                                enableHaptics = enableHaptics,
                                onOpenSource = onOpenSource,
                                onRemoveSource = onRemoveSource,
                                onOpenCredentialsEdit = onOpenCredentialsEdit,
                                onSearchChanged = onSearchChanged,
                                onSearch = onSearch,
                                onOpenLink = onOpenLink,
                                onDownload = onDownload
                            )
                        }
                    }

                    if (webMode == WebContentMode.Opds) {
                        if (!state.isDownloading) {
                            OpdsPaginationBar(
                                paging = state.paging,
                                enabled = !state.isLoading,
                                enableHaptics = enableHaptics,
                                onPreviousPage = onPreviousPage,
                                onNextPage = onNextPage,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp)
                            )
                        }
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(12.dp)
                        ) {
                            AnimatedVisibility(
                                visible = state.isDownloading,
                                enter = fadeIn() + slideInVertically { height -> height / 2 },
                                exit = fadeOut() + slideOutVertically { height -> height / 2 }
                            ) {
                                OpdsDownloadProgressOverlay(
                                    progressInfo = state.downloadProgress,
                                    enableHaptics = enableHaptics,
                                    onCancel = onCancelDownload
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
private fun OpdsCatalogContent(
    state: OpdsUiState,
    opdsListState: LazyListState,
    enableHaptics: Boolean,
    onOpenSource: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onOpenCredentialsEdit: (OpdsSource) -> Unit,
    onSearchChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenLink: (OpdsLink) -> Unit,
    onDownload: (OpdsEntry, OpdsAcquisition) -> Unit
) {
    val strings = LocalStrings.current
    val density = LocalDensity.current
    val catalog = state.catalog
    val entryRows = remember(catalog) { catalog?.entries?.withStableLazyKeys() ?: emptyList() }
    val feedLinks = remember(catalog, state.currentUrl, state.sources) {
        catalog?.links
            ?.filter(OpdsLink::isBrowsableFeedLink)
            ?.filterNot(OpdsLink::isStartLink)
            ?.filterNot(OpdsLink::isPageNavigationLink)
            ?.filterNot { link ->
                link.isRedundantStartLink(
                    currentUrl = state.currentUrl,
                    sources = state.sources
                )
            }
            ?: emptyList()
    }
    val hasSearch = remember(catalog) { catalog?.hasSearch() ?: false }
    val contentMotionKey = remember(state.currentUrl, catalog, state.isLoading, feedLinks.size) {
        state.opdsContentMotionKey(feedLinks.size)
    }
    val contentAlpha = remember { Animatable(1f) }
    val contentOffsetY = remember { Animatable(0f) }
    val contentEnterOffsetPx = with(density) { 14.dp.toPx() }
    var skipInitialAnimation by remember { mutableStateOf(true) }

    LaunchedEffect(contentMotionKey, contentEnterOffsetPx) {
        if (skipInitialAnimation) {
            skipInitialAnimation = false
            contentAlpha.snapTo(1f)
            contentOffsetY.snapTo(0f)
            return@LaunchedEffect
        }

        contentAlpha.snapTo(0f)
        contentOffsetY.snapTo(contentEnterOffsetPx)
        launch {
            contentAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = OPDS_CONTENT_FADE_DURATION_MILLIS)
            )
        }
        launch {
            contentOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = OPDS_CONTENT_ENTER_DURATION_MILLIS,
                    easing = EmphasizedEasing
                )
            )
        }
    }

    LazyColumn(
        state = opdsListState,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = contentAlpha.value
                translationY = contentOffsetY.value
            },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SourcePicker(
                state = state,
                enableHaptics = enableHaptics,
                onOpenSource = onOpenSource,
                onRemoveSource = onRemoveSource,
                onEditCredentials = onOpenCredentialsEdit
            )
        }

        state.errorMessage?.let { message ->
            item {
                StatusMessage(
                    text = message,
                    isError = true
                )
            }
        }

        item {
            StatusMessageHost(text = state.statusMessage)
        }

        if (state.isLoading) {
            item {
                LoadingCard(strings.opdsStatusOpening)
            }
        }

        if (catalog != null) {
            item {
                SearchPanel(
                    query = state.searchInput,
                    isSearchAvailable = hasSearch,
                    enabled = !state.isLoading && !state.isDownloading,
                    enableHaptics = enableHaptics,
                    onSearchChanged = onSearchChanged,
                    onSearch = onSearch
                )
            }

            if (feedLinks.isNotEmpty()) {
                item {
                    FeedLinksRow(
                        links = feedLinks,
                        enabled = !state.isLoading && !state.isDownloading,
                        enableHaptics = enableHaptics,
                        onOpenLink = onOpenLink
                    )
                }
            }

            if (catalog.entries.isEmpty() && !state.isLoading) {
                item {
                    StatusMessage(
                        text = strings.opdsCatalogEmpty,
                        isError = false
                    )
                }
            }

            itemsIndexed(
                entryRows,
                key = { _, row -> row.key },
                contentType = { _, _ -> "entry" }
            ) { _, row ->
                OpdsEntryCard(
                    entry = row.entry,
                    enabled = !state.isLoading && !state.isDownloading,
                    enableHaptics = enableHaptics,
                    onOpenLink = onOpenLink,
                    onDownload = onDownload,
                    modifier = Modifier.animateItem(
                        fadeInSpec = OPDS_FEED_FADE_IN_SPEC,
                        fadeOutSpec = OPDS_FEED_FADE_OUT_SPEC,
                        placementSpec = OPDS_FEED_PLACEMENT_SPEC
                    )
                )
            }

            if (feedLinks.isNotEmpty()) {
                item {
                    FeedLinksRow(
                        links = feedLinks,
                        enabled = !state.isLoading && !state.isDownloading,
                        enableHaptics = enableHaptics,
                        onOpenLink = onOpenLink,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }

        item {
            Spacer(
                Modifier.height(
                    when {
                        state.isDownloading -> 112.dp
                        state.paging.shouldShow -> 80.dp
                        else -> 8.dp
                    }
                )
            )
        }
    }
}

private data class OpdsContentMotionKey(
    val currentUrl: String?,
    val catalogTitle: String?,
    val entryCount: Int,
    val feedLinkCount: Int,
    val isLoadingWithoutCatalog: Boolean
)

private fun OpdsUiState.opdsContentMotionKey(feedLinkCount: Int): OpdsContentMotionKey {
    val catalog = catalog
    return OpdsContentMotionKey(
        currentUrl = currentUrl,
        catalogTitle = catalog?.title,
        entryCount = catalog?.entries?.size ?: 0,
        feedLinkCount = feedLinkCount,
        isLoadingWithoutCatalog = isLoading && catalog == null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebModeSelector(
    selectedMode: WebContentMode,
    enableHaptics: Boolean,
    onModeSelected: (WebContentMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val options = listOf(
            WebContentMode.Opds to "OPDS",
            WebContentMode.Manga to "Manga"
        )
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = selectedMode == mode,
                onClick = {
                    view.performHapticIfAllowed(
                        context,
                        enableHaptics,
                        HapticFeedbackConstants.VIRTUAL_KEY
                    )
                    onModeSelected(mode)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {
                    Icon(
                        imageVector = when (mode) {
                            WebContentMode.Opds -> Icons.AutoMirrored.Outlined.MenuBook
                            WebContentMode.Manga -> Icons.Outlined.Image
                        },
                        contentDescription = null
                    )
                }
            ) {
                Text(
                    text = label,
                    maxLines = 1
                )
            }
        }
    }
}

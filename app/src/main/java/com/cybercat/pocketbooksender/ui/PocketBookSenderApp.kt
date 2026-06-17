package com.cybercat.pocketbooksender.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cybercat.pocketbooksender.feature.catalog.CatalogScreen
import com.cybercat.pocketbooksender.feature.catalog.CatalogViewModel
import com.cybercat.pocketbooksender.feature.manga.MangaViewModel
import com.cybercat.pocketbooksender.feature.opds.OpdsScreen
import com.cybercat.pocketbooksender.feature.opds.OpdsViewModel
import com.cybercat.pocketbooksender.feature.settings.SettingsScreen
import com.cybercat.pocketbooksender.feature.settings.SettingsViewModel
import com.cybercat.pocketbooksender.feature.transfer.SendScreen
import com.cybercat.pocketbooksender.feature.transfer.TransferViewModel
import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.AppTheme
import com.cybercat.pocketbooksender.ui.navigation.MainDestination
import com.cybercat.pocketbooksender.ui.navigation.MainDestinations
import com.cybercat.pocketbooksender.ui.theme.PocketBookSenderTheme
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val NAV_ENTER_DURATION_MILLIS = 200
private const val NAV_EXIT_DURATION_MILLIS = 80
private val LANDSCAPE_NAVIGATION_RAIL_WIDTH = 80.dp

private class NavigationClickGate(
    private val debounceMillis: Long = NAV_ENTER_DURATION_MILLIS.toLong()
) {
    private var lastRoute: String? = null
    private var lastClickUptimeMillis: Long = 0L

    fun shouldHandle(route: String): Boolean {
        val now = SystemClock.uptimeMillis()
        val isDuplicateClick = route == lastRoute &&
            now - lastClickUptimeMillis < debounceMillis

        if (isDuplicateClick) return false

        lastRoute = route
        lastClickUptimeMillis = now
        return true
    }
}

@Composable
fun PocketBookSenderApp(
    sharedUris: List<Uri>,
    onSharedUrisConsumed: () -> Unit,
    rootViewModel: RootViewModel = hiltViewModel()
) {
    val settings by rootViewModel.settings.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val coroutineScope = rememberCoroutineScope()
    val sendListState = rememberLazyListState()
    val catalogListState = rememberLazyListState()
    val opdsListState = rememberLazyListState()
    val mangaListState = rememberLazyListState()
    val settingsScrollState = rememberScrollState()
    val navigationClickGate = remember { NavigationClickGate() }
    var topScrollJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(sharedUris) {
        if (sharedUris.isNotEmpty()) {
            rootViewModel.addUris(sharedUris)
            onSharedUrisConsumed()
        }
    }

    fun scrollDestinationToTop(route: String) {
        topScrollJob?.cancel()
        topScrollJob = coroutineScope.launch {
            when (route) {
                MainDestination.Send.route -> sendListState.animateScrollToTop()

                MainDestination.Catalog.route -> catalogListState.animateScrollToTop()

                MainDestination.Opds.route -> {
                    opdsListState.animateScrollToTop()
                    mangaListState.animateScrollToTop()
                }

                MainDestination.Settings.route -> settingsScrollState.animateScrollTo(0)
            }
        }
    }

    val darkTheme = when (settings.theme) {
        AppTheme.Light -> false
        AppTheme.Dark -> true
        AppTheme.System -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    PocketBookSenderTheme(
        darkTheme = darkTheme,
        useDynamicColor = settings.useDynamicColor
    ) {
        SyncSystemBarsWithTheme(darkTheme = darkTheme)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalAdaptiveLayoutInfo provides currentAdaptiveLayoutInfo(maxWidth)
            ) {
                val destinationLabels = MainDestinations.associateWith { destination ->
                    destination.translatedLabel()
                }
                val navigationLayoutType = currentNavigationSuiteLayoutType()
                val useFullHeightNavigationRail =
                    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                        navigationLayoutType == NavigationSuiteType.NavigationRail
                val onNavigationDestinationClick: (
                    MainDestination,
                    Boolean
                ) -> Unit = { destination, selected ->
                    if (navigationClickGate.shouldHandle(destination.route)) {
                        if (selected) {
                            scrollDestinationToTop(destination.route)
                        } else {
                            navController.navigateSingleTop(destination.route)
                        }
                    }
                }
                val content: @Composable () -> Unit = {
                    AppNavHost(
                        navController = navController,
                        appSettings = settings,
                        sendListState = sendListState,
                        catalogListState = catalogListState,
                        opdsListState = opdsListState,
                        mangaListState = mangaListState,
                        settingsScrollState = settingsScrollState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (useFullHeightNavigationRail) {
                    LandscapeNavigationRailScaffold(
                        currentRoute = currentRoute,
                        destinationLabels = destinationLabels,
                        onDestinationClick = onNavigationDestinationClick,
                        modifier = Modifier.fillMaxSize(),
                        content = content
                    )
                } else {
                    NavigationSuiteScaffold(
                        navigationSuiteItems = {
                            MainDestinations.forEach { destination ->
                                val selected = currentRoute == destination.route
                                val label = destinationLabels.getValue(destination)
                                item(
                                    selected = selected,
                                    onClick = {
                                        onNavigationDestinationClick(destination, selected)
                                    },
                                    icon = { Icon(destination.icon, contentDescription = label) },
                                    label = { Text(label) }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        layoutType = navigationLayoutType,
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        content = { content() }
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeNavigationRailScaffold(
    currentRoute: String?,
    destinationLabels: Map<MainDestination, String>,
    onDestinationClick: (MainDestination, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(modifier) {
        NavigationRail(
            modifier = Modifier
                .width(LANDSCAPE_NAVIGATION_RAIL_WIDTH)
                .fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            MainDestinations.forEach { destination ->
                val selected = currentRoute == destination.route
                val label = destinationLabels.getValue(destination)
                NavigationRailItem(
                    selected = selected,
                    onClick = { onDestinationClick(destination, selected) },
                    icon = { Icon(destination.icon, contentDescription = label) },
                    label = { Text(label) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            content()
        }
    }
}

@Composable
private fun currentNavigationSuiteLayoutType(): NavigationSuiteType =
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        NavigationSuiteType.NavigationRail
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    }

@Composable
@Suppress("DEPRECATION")
private fun SyncSystemBarsWithTheme(darkTheme: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme

    if (view.isInEditMode) return

    SideEffect {
        val window = context.findActivity()?.window ?: return@SideEffect
        val background = colorScheme.background.toArgb()

        window.statusBarColor = background
        window.navigationBarColor = colorScheme.surface.toArgb()
        window.decorView.setBackgroundColor(background)
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private suspend fun LazyListState.animateScrollToTop() {
    animateScrollToItem(index = 0, scrollOffset = 0)
    if (firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0) {
        scrollToItem(index = 0, scrollOffset = 0)
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    appSettings: AppSettings,
    sendListState: LazyListState,
    catalogListState: LazyListState,
    opdsListState: LazyListState,
    mangaListState: LazyListState,
    settingsScrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = MainDestination.Send.route,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(NAV_ENTER_DURATION_MILLIS)) +
                scaleIn(initialScale = 0.96f, animationSpec = tween(NAV_ENTER_DURATION_MILLIS))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(NAV_EXIT_DURATION_MILLIS))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(NAV_ENTER_DURATION_MILLIS)) +
                scaleIn(initialScale = 0.96f, animationSpec = tween(NAV_ENTER_DURATION_MILLIS))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(NAV_EXIT_DURATION_MILLIS))
        }
    ) {
        composable(MainDestination.Send.route) {
            val transferViewModel: TransferViewModel = hiltViewModel()
            val transferState by transferViewModel.uiState.collectAsStateWithLifecycle()
            SendScreen(
                state = transferState,
                listState = sendListState,
                onFtpInputChanged = transferViewModel::onFtpInputChanged,
                onConnect = transferViewModel::connect,
                onQrScanned = transferViewModel::connectTo,
                onDisconnect = transferViewModel::disconnect,
                onAddUris = transferViewModel::addUris,
                onRemoveItem = transferViewModel::removeItem,
                onClearQueue = transferViewModel::clearQueueAfterDelay,
                onCategoryChanged = transferViewModel::updateCategory,
                onDocumentsTagChanged = transferViewModel::updateDocumentsTag,
                onMangaSeriesChanged = transferViewModel::updateMangaSeries,
                onQueuedMangaSeriesChanged = transferViewModel::updateQueuedMangaSeries,
                onUploadAll = transferViewModel::uploadAll
            )
        }
        composable(MainDestination.Catalog.route) {
            val catalogViewModel: CatalogViewModel = hiltViewModel()
            val catalogState by catalogViewModel.uiState.collectAsStateWithLifecycle()
            CatalogScreen(
                state = catalogState,
                isConnected = catalogState.connectedDevice != null,
                enableHaptics = appSettings.enableHaptics,
                listState = catalogListState,
                onRefresh = catalogViewModel::reloadDeviceCatalog,
                onSetEditMode = catalogViewModel::setEditMode,
                onToggleFileSelection = catalogViewModel::toggleFileSelection,
                onSetFileSelection = catalogViewModel::setFileSelection,
                onToggleGroupSelection = catalogViewModel::toggleGroupSelection,
                onDeleteSelectedFiles = catalogViewModel::deleteSelectedFiles,
                onClearDeleteError = catalogViewModel::clearDeleteError
            )
        }
        composable(MainDestination.Opds.route) {
            val opdsViewModel: OpdsViewModel = hiltViewModel()
            val opdsState by opdsViewModel.uiState.collectAsStateWithLifecycle()
            val mangaViewModel: MangaViewModel = hiltViewModel()
            val mangaState by mangaViewModel.uiState.collectAsStateWithLifecycle()

            val strings = com.cybercat.pocketbooksender.localization.LocalStrings.current
            val view = androidx.compose.ui.platform.LocalView.current
            val context = androidx.compose.ui.platform.LocalContext.current
            val enableHaptics = appSettings.enableHaptics

            OpdsScreen(
                state = opdsState,
                opdsListState = opdsListState,
                onSearchChanged = opdsViewModel::onSearchInputChanged,
                onWebModeSelected = opdsViewModel::setWebContentMode,
                onSaveSource = { title, url, username, password ->
                    opdsViewModel.saveOpdsSource(title, url, username, password)
                },
                onRemoveSource = opdsViewModel::removeOpdsSource,
                onOpenSource = opdsViewModel::openOpdsSource,
                onOpenLink = opdsViewModel::openOpdsLink,
                onBack = opdsViewModel::goBackOpds,
                onPreviousPage = opdsViewModel::goPreviousOpdsPage,
                onNextPage = opdsViewModel::goNextOpdsPage,
                onSearch = opdsViewModel::searchOpds,
                onDownload = opdsViewModel::downloadOpdsAcquisition,
                onCancelDownload = opdsViewModel::cancelOpdsDownload,
                onAuthUsernameChanged = opdsViewModel::onAuthUsernameChanged,
                onAuthPasswordChanged = opdsViewModel::onAuthPasswordChanged,
                onDismissAuthDialog = opdsViewModel::dismissCredentialsDialog,
                onSaveCredentials = opdsViewModel::saveCredentials,
                onOpenCredentialsEdit = opdsViewModel::openCredentialsDialog,
                enableHaptics = enableHaptics,
                mangaPane = {
                    com.cybercat.pocketbooksender.feature.manga.MangaPane(
                        state = mangaState,
                        enableHaptics = enableHaptics,
                        listState = mangaListState,
                        onSearchChanged = mangaViewModel::onMangaSearchChanged,
                        onSearch = mangaViewModel::searchManga,
                        onSelectSource = mangaViewModel::selectMangaSource,
                        onOpenBrowser = mangaViewModel::openMangaBrowser,
                        onCloseBrowser = mangaViewModel::closeMangaBrowser,
                        onWebPageLoaded = mangaViewModel::syncMangaWebPage,
                        onOpenSeries = mangaViewModel::openMangaSeries,
                        onToggleChapter = mangaViewModel::toggleMangaChapter,
                        onSetMangaSeriesFavorite =
                            mangaViewModel::setSelectedMangaFavorite,
                        onSetMangaSeriesSubscribed =
                            mangaViewModel::setSelectedMangaSubscribed,
                        onCheckSubscriptions =
                            mangaViewModel::checkMangaSubscriptions,
                        onDownloadSelected = mangaViewModel::downloadSelectedMangaChapters,
                        onToggleSubscriptionUpdateChapter =
                            mangaViewModel::toggleSubscriptionUpdateChapter,
                        onSelectAllSubscriptionUpdateChapters =
                            mangaViewModel::selectAllSubscriptionUpdateChapters,
                        onClearSubscriptionUpdateChapters =
                            mangaViewModel::clearSubscriptionUpdateChapters,
                        onDownloadSubscriptionUpdates = mangaViewModel::downloadSubscriptionUpdates,
                        onCloseSubscriptionUpdates = mangaViewModel::closeSubscriptionUpdates,
                        onRefreshAuthState = mangaViewModel::refreshMangaAuthState,
                        onCancelDownload = mangaViewModel::cancelMangaDownload,
                        onNativeLoginSubmit = mangaViewModel::performNativeLogin,
                        onLoginPostExecuted = mangaViewModel::clearPendingLoginPost
                    )
                },
                mangaTopBarNavigationIcon = {
                    if (mangaState.selectedSeries != null) {
                        IconButton(
                            onClick = {
                                view.performHapticIfAllowed(
                                    context,
                                    enableHaptics,
                                    android.view.HapticFeedbackConstants.VIRTUAL_KEY
                                )
                                mangaViewModel.goBackManga()
                            },
                            enabled = !mangaState.isLoading && !mangaState.isDownloading
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = strings.get("action_back")
                            )
                        }
                    }
                },
                mangaTopBarActions = {
                    com.cybercat.pocketbooksender.feature.manga.MangaSelectionActions(
                        enabled = !mangaState.isDownloading,
                        hasNewChapters = mangaState.hasNewChapters,
                        hasChapters = mangaState.chapters.isNotEmpty(),
                        enableHaptics = enableHaptics,
                        onSelectNew = mangaViewModel::selectNewMangaChapters,
                        onSelectAll = mangaViewModel::selectAllMangaChapters,
                        onClear = mangaViewModel::clearMangaChapterSelection
                    )
                },
                mangaFloatingActionButton = {
                    val selectedCount = mangaState.selectedChapterIds.size
                    androidx.compose.animation.AnimatedVisibility(
                        visible = selectedCount > 0 && !mangaState.isDownloading,
                        enter =
                            androidx.compose.animation.fadeIn() +
                                androidx.compose.animation.slideInVertically { height -> height },
                        exit =
                            androidx.compose.animation.fadeOut() +
                                androidx.compose.animation.slideOutVertically { height -> height }
                    ) {
                        androidx.compose.material3.ExtendedFloatingActionButton(
                            onClick = {
                                view.performHapticIfAllowed(
                                    context,
                                    enableHaptics,
                                    android.view.HapticFeedbackConstants.CONFIRM
                                )
                                mangaViewModel.downloadSelectedMangaChapters()
                            },
                            icon = {
                                Icon(
                                    Icons.Outlined.Download,
                                    contentDescription = null
                                )
                            },
                            text = { Text(strings.get("opds_btn_download", selectedCount)) }
                        )
                    }
                },
                isMangaSelectionActive = mangaState.selectedChapterIds.isNotEmpty(),
                mangaSelectedChapterCount = mangaState.selectedChapterIds.size,
                onClearMangaSelection = {
                    view.performHapticIfAllowed(
                        context,
                        enableHaptics,
                        android.view.HapticFeedbackConstants.VIRTUAL_KEY
                    )
                    mangaViewModel.clearMangaChapterSelection()
                }
            )
        }
        composable(MainDestination.Settings.route) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                settingsViewModel.scanLocales()
            }
            SettingsScreen(
                state = settingsState,
                scrollState = settingsScrollState,
                onRootPathChanged = settingsViewModel::setRootPath,
                onBooksFolderNameChanged = settingsViewModel::setBooksFolderName,
                onDocumentsFolderNameChanged = settingsViewModel::setDocumentsFolderName,
                onMangaFolderNameChanged = settingsViewModel::setMangaFolderName,
                onDefaultDocumentsTagChanged = settingsViewModel::setDefaultDocumentsTag,
                onDefaultMangaSeriesChanged = settingsViewModel::setDefaultMangaSeries,
                onBookFileNameTemplateChanged = settingsViewModel::setBookFileNameTemplate,
                onDocumentsFileNameTemplateChanged =
                    settingsViewModel::setDocumentsFileNameTemplate,
                onMangaFileNameTemplateChanged = settingsViewModel::setMangaFileNameTemplate,
                onDynamicColorChanged = settingsViewModel::setUseDynamicColor,
                onHapticFeedbackEnabledChanged = settingsViewModel::setEnableHaptics,
                onBypassVpnForLocalConnectionsChanged =
                    settingsViewModel::setBypassVpnForLocalConnections,
                onClearDownloadCache = settingsViewModel::clearDownloadCache,
                onClearStatusMessage = settingsViewModel::clearStatusMessage,
                onThemeChanged = settingsViewModel::setTheme,
                onLanguageChanged = settingsViewModel::setLanguageCode,
                onWarnOnDisconnectedRenameChanged = settingsViewModel::setWarnOnDisconnectedRename,
                onConfirmPendingRename = settingsViewModel::confirmPendingRename,
                onCancelPendingRename = settingsViewModel::cancelPendingRename,
                onLogoutAll = settingsViewModel::logoutAll,
                onConfirmLogoutAll = settingsViewModel::confirmLogoutAll,
                onDismissLogoutWarning = settingsViewModel::dismissLogoutWarning
            )
        }
    }
}

@Composable
private fun MainDestination.translatedLabel(): String {
    val strings = com.cybercat.pocketbooksender.localization.LocalStrings.current
    return when (this) {
        MainDestination.Send -> strings.navSend
        MainDestination.Catalog -> strings.navCatalog
        MainDestination.Opds -> strings.navWeb
        MainDestination.Settings -> strings.navSettings
    }
}

private fun androidx.navigation.NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
    }
}

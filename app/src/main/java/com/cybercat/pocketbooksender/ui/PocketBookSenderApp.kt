package com.cybercat.pocketbooksender.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import com.cybercat.pocketbooksender.ui.navigation.MainDestination
import com.cybercat.pocketbooksender.ui.navigation.MainDestinations
import com.cybercat.pocketbooksender.feature.catalog.CatalogScreen
import com.cybercat.pocketbooksender.feature.catalog.CatalogViewModel
import com.cybercat.pocketbooksender.feature.catalog.CatalogUiState
import com.cybercat.pocketbooksender.feature.settings.SettingsScreen
import com.cybercat.pocketbooksender.feature.settings.SettingsViewModel
import com.cybercat.pocketbooksender.feature.settings.SettingsUiState
import com.cybercat.pocketbooksender.feature.transfer.SendScreen
import com.cybercat.pocketbooksender.feature.transfer.TransferViewModel
import com.cybercat.pocketbooksender.feature.transfer.TransferUiState
import com.cybercat.pocketbooksender.feature.opds.OpdsScreen
import com.cybercat.pocketbooksender.feature.opds.OpdsViewModel
import com.cybercat.pocketbooksender.feature.opds.OpdsUiState
import com.cybercat.pocketbooksender.feature.opds.WebContentMode
import com.cybercat.pocketbooksender.feature.manga.MangaViewModel
import com.cybercat.pocketbooksender.feature.manga.MangaUiState
import com.cybercat.pocketbooksender.ui.theme.PocketBookSenderTheme
import com.cybercat.pocketbooksender.model.AppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun PocketBookSenderApp(
    sharedUris: List<Uri>,
    onSharedUrisConsumed: () -> Unit,
    transferViewModel: TransferViewModel = hiltViewModel(),
    catalogViewModel: CatalogViewModel = hiltViewModel(),
    opdsViewModel: OpdsViewModel = hiltViewModel(),
    mangaViewModel: MangaViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val transferState by transferViewModel.uiState.collectAsStateWithLifecycle()
    val catalogState by catalogViewModel.uiState.collectAsStateWithLifecycle()
    val opdsState by opdsViewModel.uiState.collectAsStateWithLifecycle()
    val mangaState by mangaViewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val coroutineScope = rememberCoroutineScope()
    val sendListState = rememberLazyListState()
    val catalogListState = rememberLazyListState()
    val opdsListState = rememberLazyListState()
    val mangaListState = rememberLazyListState()
    val settingsScrollState = rememberScrollState()
    var topScrollJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(sharedUris) {
        if (sharedUris.isNotEmpty()) {
            transferViewModel.addUris(sharedUris)
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
                    if (opdsState.webMode == WebContentMode.Manga) {
                        mangaListState.animateScrollToTop()
                    } else {
                        opdsListState.animateScrollToTop()
                    }
                }
                MainDestination.Settings.route -> settingsScrollState.animateScrollTo(0)
            }
        }
    }

    val darkTheme = when (settingsState.settings.theme) {
        AppTheme.Light -> false
        AppTheme.Dark -> true
        AppTheme.System -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    PocketBookSenderTheme(
        darkTheme = darkTheme,
        useDynamicColor = settingsState.settings.useDynamicColor
    ) {
        SyncSystemBarsWithTheme(darkTheme = darkTheme)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalAdaptiveLayoutInfo provides currentAdaptiveLayoutInfo(maxWidth),
            ) {
                val destinationLabels = MainDestinations.associateWith { destination ->
                    destination.translatedLabel()
                }
                NavigationSuiteScaffold(
                    navigationSuiteItems = {
                        MainDestinations.forEach { destination ->
                            val selected = currentRoute == destination.route
                            val label = destinationLabels.getValue(destination)
                            item(
                                selected = selected,
                                onClick = {
                                    if (selected) {
                                        scrollDestinationToTop(destination.route)
                                    } else {
                                        navController.navigateSingleTop(destination.route)
                                    }
                                },
                                icon = { Icon(destination.icon, contentDescription = label) },
                                label = { Text(label) },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    AppNavHost(
                        navController = navController,
                        transferViewModel = transferViewModel,
                        catalogViewModel = catalogViewModel,
                        opdsViewModel = opdsViewModel,
                        mangaViewModel = mangaViewModel,
                        settingsViewModel = settingsViewModel,
                        transferState = transferState,
                        catalogState = catalogState,
                        opdsState = opdsState,
                        mangaState = mangaState,
                        settingsState = settingsState,
                        sendListState = sendListState,
                        catalogListState = catalogListState,
                        opdsListState = opdsListState,
                        mangaListState = mangaListState,
                        settingsScrollState = settingsScrollState,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
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
    transferViewModel: TransferViewModel,
    catalogViewModel: CatalogViewModel,
    opdsViewModel: OpdsViewModel,
    mangaViewModel: MangaViewModel,
    settingsViewModel: SettingsViewModel,
    transferState: TransferUiState,
    catalogState: CatalogUiState,
    opdsState: OpdsUiState,
    mangaState: MangaUiState,
    settingsState: SettingsUiState,
    sendListState: LazyListState,
    catalogListState: LazyListState,
    opdsListState: LazyListState,
    mangaListState: LazyListState,
    settingsScrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = MainDestination.Send.route,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(200, delayMillis = 80)) +
            scaleIn(initialScale = 0.96f, animationSpec = tween(200, delayMillis = 80))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(80))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(200, delayMillis = 80)) +
            scaleIn(initialScale = 0.96f, animationSpec = tween(200, delayMillis = 80))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(80))
        },
    ) {
        composable(MainDestination.Send.route) {
            SendScreen(
                state = transferState,
                listState = sendListState,
                onFtpInputChanged = transferViewModel::onFtpInputChanged,
                onConnect = transferViewModel::connect,
                onQrScanned = transferViewModel::connectTo,
                onDisconnect = transferViewModel::disconnect,
                onAddUris = transferViewModel::addUris,
                onRemoveItem = transferViewModel::removeItem,
                onClearQueue = transferViewModel::clearQueue,
                onCategoryChanged = transferViewModel::updateCategory,
                onDocumentsTagChanged = transferViewModel::updateDocumentsTag,
                onMangaSeriesChanged = transferViewModel::updateMangaSeries,
                onQueuedMangaSeriesChanged = transferViewModel::updateQueuedMangaSeries,
                onUploadAll = transferViewModel::uploadAll,
            )
        }
        composable(MainDestination.Catalog.route) {
            CatalogScreen(
                state = catalogState,
                isConnected = catalogState.connectedDevice != null,
                enableHaptics = catalogState.settings.enableHaptics,
                listState = catalogListState,
                onRefresh = catalogViewModel::reloadDeviceCatalog,
                onSetEditMode = catalogViewModel::setEditMode,
                onToggleFileSelection = catalogViewModel::toggleFileSelection,
                onSetFileSelection = catalogViewModel::setFileSelection,
                onToggleGroupSelection = catalogViewModel::toggleGroupSelection,
                onDeleteSelectedFiles = catalogViewModel::deleteSelectedFiles,
                onClearDeleteError = catalogViewModel::clearDeleteError,
            )
        }
        composable(MainDestination.Opds.route) {
            val strings = com.cybercat.pocketbooksender.localization.LocalStrings.current
            val view = androidx.compose.ui.platform.LocalView.current
            val context = androidx.compose.ui.platform.LocalContext.current
            val enableHaptics = settingsState.settings.enableHaptics

            OpdsScreen(
                state = opdsState,
                opdsListState = opdsListState,
                onSearchChanged = opdsViewModel::onSearchInputChanged,
                onWebModeSelected = opdsViewModel::setWebContentMode,
                onSaveSource = { title, url, username, password -> opdsViewModel.saveOpdsSource(title, url, username, password) },
                onRemoveSource = opdsViewModel::removeOpdsSource,
                onOpenSource = opdsViewModel::openOpdsUrl,
                onOpenLink = opdsViewModel::openOpdsLink,
                onBack = opdsViewModel::goBackOpds,
                onSearch = opdsViewModel::searchOpds,
                onDownload = opdsViewModel::downloadOpdsAcquisition,
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
                        onOpenBrowser = mangaViewModel::openMangaBrowser,
                        onCloseBrowser = mangaViewModel::closeMangaBrowser,
                        onWebPageLoaded = mangaViewModel::syncMangaWebPage,
                        onOpenSeries = mangaViewModel::openMangaSeries,
                        onToggleChapter = mangaViewModel::toggleMangaChapter,
                        onSetMangaSeriesFavorite = mangaViewModel::setSelectedMangaFavorite,
                        onSetMangaSeriesSubscribed = mangaViewModel::setSelectedMangaSubscribed,
                        onCheckSubscriptions = mangaViewModel::checkMangaSubscriptions,
                        onDownloadSelected = mangaViewModel::downloadSelectedMangaChapters,
                        onToggleSubscriptionUpdateChapter = mangaViewModel::toggleSubscriptionUpdateChapter,
                        onSelectAllSubscriptionUpdateChapters = mangaViewModel::selectAllSubscriptionUpdateChapters,
                        onClearSubscriptionUpdateChapters = mangaViewModel::clearSubscriptionUpdateChapters,
                        onDownloadSubscriptionUpdates = mangaViewModel::downloadSubscriptionUpdates,
                        onCloseSubscriptionUpdates = mangaViewModel::closeSubscriptionUpdates,
                    )
                },
                mangaTopBarNavigationIcon = {
                    if (mangaState.selectedSeries != null) {
                        IconButton(
                            onClick = {
                                view.performHapticIfAllowed(context, enableHaptics, android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                                mangaViewModel.goBackManga()
                            },
                            enabled = !mangaState.isLoading && !mangaState.isDownloading,
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = strings.get("action_back"),
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
                        onClear = mangaViewModel::clearMangaChapterSelection,
                    )
                },
                mangaFloatingActionButton = {
                    val selectedCount = mangaState.selectedChapterIds.size
                    androidx.compose.animation.AnimatedVisibility(
                        visible = selectedCount > 0 && !mangaState.isDownloading,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { height -> height },
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { height -> height },
                    ) {
                        androidx.compose.material3.ExtendedFloatingActionButton(
                            onClick = {
                                view.performHapticIfAllowed(context, enableHaptics, android.view.HapticFeedbackConstants.CONFIRM)
                                mangaViewModel.downloadSelectedMangaChapters()
                            },
                            icon = { Icon(androidx.compose.material.icons.Icons.Outlined.Download, contentDescription = null) },
                            text = { Text(strings.get("opds_btn_download", selectedCount)) },
                        )
                    }
                },
                isMangaSelectionActive = mangaState.selectedChapterIds.isNotEmpty(),
                mangaSelectedChapterCount = mangaState.selectedChapterIds.size,
                onClearMangaSelection = {
                    view.performHapticIfAllowed(context, enableHaptics, android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    mangaViewModel.clearMangaChapterSelection()
                },
            )
        }
        composable(MainDestination.Settings.route) {
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
                onDocumentsFileNameTemplateChanged = settingsViewModel::setDocumentsFileNameTemplate,
                onMangaFileNameTemplateChanged = settingsViewModel::setMangaFileNameTemplate,
                onDynamicColorChanged = settingsViewModel::setUseDynamicColor,
                onHapticFeedbackEnabledChanged = settingsViewModel::setEnableHaptics,
                onClearDownloadCache = settingsViewModel::clearDownloadCache,
                onClearStatusMessage = settingsViewModel::clearStatusMessage,
                onThemeChanged = settingsViewModel::setTheme,
                onLanguageChanged = settingsViewModel::setLanguageCode,
                onWarnOnDisconnectedRenameChanged = settingsViewModel::setWarnOnDisconnectedRename,
                onConfirmPendingRename = settingsViewModel::confirmPendingRename,
                onCancelPendingRename = settingsViewModel::cancelPendingRename,
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

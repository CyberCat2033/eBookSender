package com.cybercat.pocketbooksender.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.cybercat.pocketbooksender.ui.navigation.MainDestination
import com.cybercat.pocketbooksender.ui.navigation.MainDestinations
import com.cybercat.pocketbooksender.ui.screens.CatalogScreen
import com.cybercat.pocketbooksender.ui.screens.OpdsScreen
import com.cybercat.pocketbooksender.ui.screens.SendScreen
import com.cybercat.pocketbooksender.ui.screens.SettingsScreen
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
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val useRail = maxWidth >= 720.dp

            if (useRail) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    AppNavigationRail(
                        currentRoute = currentRoute,
                        onNavigate = { route -> navController.navigateSingleTop(route) },
                        onReselect = ::scrollDestinationToTop,
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
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
            } else {
                Scaffold(
                    bottomBar = {
                        AppNavigationBar(
                            currentRoute = currentRoute,
                            onNavigate = { route -> navController.navigateSingleTop(route) },
                            onReselect = ::scrollDestinationToTop,
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.background,
                ) { innerPadding ->
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }
        }
    }
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
            OpdsScreen(
                state = opdsState,
                mangaState = mangaState,
                opdsListState = opdsListState,
                mangaListState = mangaListState,
                onSearchChanged = opdsViewModel::onSearchInputChanged,
                onWebModeSelected = opdsViewModel::setWebContentMode,
                onSaveSource = opdsViewModel::saveOpdsSource,
                onRemoveSource = opdsViewModel::removeOpdsSource,
                onOpenSource = opdsViewModel::openOpdsUrl,
                onOpenLink = opdsViewModel::openOpdsLink,
                onBack = opdsViewModel::goBackOpds,
                onMangaBack = mangaViewModel::goBackManga,
                onSearch = opdsViewModel::searchOpds,
                onDownload = opdsViewModel::downloadOpdsAcquisition,
                onMangaSearchChanged = mangaViewModel::onMangaSearchChanged,
                onMangaSearch = mangaViewModel::searchManga,
                onOpenMangaBrowser = mangaViewModel::openMangaBrowser,
                onCloseMangaBrowser = mangaViewModel::closeMangaBrowser,
                onMangaWebPageLoaded = mangaViewModel::syncMangaWebPage,
                onOpenMangaSeries = mangaViewModel::openMangaSeries,
                onToggleMangaChapter = mangaViewModel::toggleMangaChapter,
                onSetMangaSeriesFavorite = mangaViewModel::setSelectedMangaFavorite,
                onSetMangaSeriesSubscribed = mangaViewModel::setSelectedMangaSubscribed,
                onCheckMangaSubscriptions = mangaViewModel::checkMangaSubscriptions,
                onSelectNewMangaChapters = mangaViewModel::selectNewMangaChapters,
                onSelectAllMangaChapters = mangaViewModel::selectAllMangaChapters,
                onClearMangaChapterSelection = mangaViewModel::clearMangaChapterSelection,
                onDownloadSelectedMangaChapters = mangaViewModel::downloadSelectedMangaChapters,
                enableHaptics = settingsState.settings.enableHaptics,
            )
        }
        composable(MainDestination.Settings.route) {
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
                onWarnOnDisconnectedRenameChanged = settingsViewModel::setWarnOnDisconnectedRename,
                onConfirmPendingRename = settingsViewModel::confirmPendingRename,
                onCancelPendingRename = settingsViewModel::cancelPendingRename,
            )
        }
    }
}

@Composable
private fun AppNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onReselect: (String) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        MainDestinations.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (selected) {
                        onReselect(destination.route)
                    } else {
                        onNavigate(destination.route)
                    }
                },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
private fun AppNavigationRail(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onReselect: (String) -> Unit,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        MainDestinations.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationRailItem(
                selected = selected,
                onClick = {
                    if (selected) {
                        onReselect(destination.route)
                    } else {
                        onNavigate(destination.route)
                    }
                },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
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

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun PocketBookSenderApp(
    sharedUris: List<Uri>,
    onSharedUrisConsumed: () -> Unit,
    viewModel: SenderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
            viewModel.addUris(sharedUris)
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
                    if (state.opds.webMode == WebContentMode.Manga) {
                        mangaListState.animateScrollToTop()
                    } else {
                        opdsListState.animateScrollToTop()
                    }
                }
                MainDestination.Settings.route -> settingsScrollState.animateScrollTo(0)
            }
        }
    }

    PocketBookSenderTheme(useDynamicColor = state.settings.useDynamicColor) {
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
                            state = state,
                            viewModel = viewModel,
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
                        state = state,
                        viewModel = viewModel,
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
    state: SenderUiState,
    viewModel: SenderViewModel,
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
                state = state,
                listState = sendListState,
                onFtpInputChanged = viewModel::onFtpInputChanged,
                onConnect = viewModel::connect,
                onQrScanned = viewModel::connectTo,
                onDisconnect = viewModel::disconnect,
                onAddUris = viewModel::addUris,
                onRemoveItem = viewModel::removeItem,
                onClearQueue = viewModel::clearQueue,
                onCategoryChanged = viewModel::updateCategory,
                onProgrammingTagChanged = viewModel::updateProgrammingTag,
                onMangaSeriesChanged = viewModel::updateMangaSeries,
                onQueuedMangaSeriesChanged = viewModel::updateQueuedMangaSeries,
                onUploadAll = viewModel::uploadAll,
            )
        }
        composable(MainDestination.Catalog.route) {
            CatalogScreen(
                catalog = state.deviceCatalog,
                isConnected = state.isConnected,
                enableHaptics = state.settings.enableHaptics,
                listState = catalogListState,
                onRefresh = viewModel::refreshDeviceCatalog,
            )
        }
        composable(MainDestination.Opds.route) {
            OpdsScreen(
                state = state.opds,
                mangaState = state.manga,
                opdsListState = opdsListState,
                mangaListState = mangaListState,
                onSearchChanged = viewModel::onOpdsSearchChanged,
                onWebModeSelected = viewModel::selectWebMode,
                onSaveSource = viewModel::saveOpdsSource,
                onRemoveSource = viewModel::removeOpdsSource,
                onOpenSource = viewModel::openOpdsUrl,
                onOpenLink = viewModel::openOpdsLink,
                onBack = viewModel::goBackOpds,
                onMangaBack = viewModel::goBackManga,
                onSearch = viewModel::searchOpds,
                onDownload = viewModel::downloadOpdsAcquisition,
                onMangaSearchChanged = viewModel::onMangaSearchChanged,
                onMangaSearch = viewModel::searchManga,
                onOpenMangaBrowser = viewModel::openMangaBrowser,
                onCloseMangaBrowser = viewModel::closeMangaBrowser,
                onMangaWebPageLoaded = viewModel::syncMangaWebPage,
                onOpenMangaSeries = viewModel::openMangaSeries,
                onToggleMangaChapter = viewModel::toggleMangaChapter,
                onSetMangaSeriesFavorite = viewModel::setSelectedMangaFavorite,
                onSetMangaSeriesSubscribed = viewModel::setSelectedMangaSubscribed,
                onCheckMangaSubscriptions = viewModel::checkMangaSubscriptions,
                onSelectNewMangaChapters = viewModel::selectNewMangaChapters,
                onSelectAllMangaChapters = viewModel::selectAllMangaChapters,
                onClearMangaChapterSelection = viewModel::clearMangaChapterSelection,
                onDownloadSelectedMangaChapters = viewModel::downloadSelectedMangaChapters,
                enableHaptics = state.settings.enableHaptics,
            )
        }
        composable(MainDestination.Settings.route) {
            SettingsScreen(
                state = state,
                scrollState = settingsScrollState,
                onRootPathChanged = viewModel::updateRootPath,
                onDefaultProgrammingTagChanged = viewModel::updateDefaultProgrammingTag,
                onDefaultMangaSeriesChanged = viewModel::updateDefaultMangaSeries,
                onBookFileNameTemplateChanged = viewModel::updateBookFileNameTemplate,
                onProgrammingFileNameTemplateChanged = viewModel::updateProgrammingFileNameTemplate,
                onMangaFileNameTemplateChanged = viewModel::updateMangaFileNameTemplate,
                onDynamicColorChanged = viewModel::updateDynamicColor,
                onHapticFeedbackEnabledChanged = viewModel::updateHapticFeedbackEnabled,
                onClearDownloadCache = viewModel::clearDownloadCache,
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

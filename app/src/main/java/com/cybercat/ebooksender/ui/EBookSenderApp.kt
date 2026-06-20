package com.cybercat.ebooksender.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.cybercat.ebooksender.data.transfer.SkippedUploadFile
import com.cybercat.ebooksender.data.transfer.UploadFileSkipReason
import com.cybercat.ebooksender.data.transfer.UploadFilesSkipped
import com.cybercat.ebooksender.data.transfer.UploadQueueEvent
import com.cybercat.ebooksender.data.update.AppUpdateState
import com.cybercat.ebooksender.data.update.AvailableAppUpdate
import com.cybercat.ebooksender.data.update.AvailablePocketBookServerUpdate
import com.cybercat.ebooksender.data.update.PocketBookServerUpdateState
import com.cybercat.ebooksender.localization.AppStrings
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.model.AppSettings
import com.cybercat.ebooksender.model.AppTheme
import com.cybercat.ebooksender.ui.navigation.MainDestination
import com.cybercat.ebooksender.ui.navigation.MainDestinations
import com.cybercat.ebooksender.ui.theme.EBookSenderTheme
import kotlinx.coroutines.CoroutineScope
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

private class AppDestinationScrollState(
    val sendListState: LazyListState,
    val catalogListState: LazyListState,
    val opdsListState: LazyListState,
    val mangaListState: LazyListState,
    val settingsScrollState: ScrollState,
    private val coroutineScope: CoroutineScope
) {
    private var topScrollJob: Job? = null

    fun scrollToTop(route: String) {
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
}

@Composable
private fun rememberAppDestinationScrollState(): AppDestinationScrollState {
    val coroutineScope = rememberCoroutineScope()
    val sendListState = rememberLazyListState()
    val catalogListState = rememberLazyListState()
    val opdsListState = rememberLazyListState()
    val mangaListState = rememberLazyListState()
    val settingsScrollState = rememberScrollState()

    return remember(
        sendListState,
        catalogListState,
        opdsListState,
        mangaListState,
        settingsScrollState,
        coroutineScope
    ) {
        AppDestinationScrollState(
            sendListState = sendListState,
            catalogListState = catalogListState,
            opdsListState = opdsListState,
            mangaListState = mangaListState,
            settingsScrollState = settingsScrollState,
            coroutineScope = coroutineScope
        )
    }
}

@Composable
fun EBookSenderApp(
    sharedUris: List<Uri>,
    onSharedUrisConsumed: () -> Unit,
    appUpdateState: AppUpdateState,
    onLoadUpdateChangelog: suspend (AvailableAppUpdate, String) -> String?,
    onInstallUpdate: () -> Unit,
    onCancelUpdateDownload: () -> Unit,
    pocketBookServerUpdateState: PocketBookServerUpdateState,
    onLoadPocketBookServerUpdateChangelog:
    suspend (AvailablePocketBookServerUpdate, String) -> String?,
    onInstallPocketBookServerUpdate: () -> Unit,
    onCancelPocketBookServerUpdate: () -> Unit,
    rootViewModel: RootViewModel = hiltViewModel()
) {
    val settings by rootViewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val destinationScrollState = rememberAppDestinationScrollState()
    val navigationClickGate = remember { NavigationClickGate() }

    LaunchedEffect(sharedUris) {
        if (sharedUris.isNotEmpty()) {
            rootViewModel.addUris(sharedUris)
            onSharedUrisConsumed()
        }
    }

    val darkTheme = when (settings.theme) {
        AppTheme.Light -> false
        AppTheme.Dark -> true
        AppTheme.System -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    EBookSenderTheme(
        darkTheme = darkTheme,
        useDynamicColor = settings.useDynamicColor
    ) {
        val strings = LocalStrings.current
        LaunchedEffect(rootViewModel, strings) {
            rootViewModel.uploadQueueEvents.collect { event ->
                event.toToastMessage(strings)?.let { message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        }

        SyncSystemBarsWithTheme(darkTheme = darkTheme)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            CompositionLocalProvider(
                LocalAdaptiveLayoutInfo provides currentAdaptiveLayoutInfo(maxWidth)
            ) {
                val destinationLabels = MainDestinations.associateWith { destination ->
                    destination.translatedLabel()
                }
                val compactDestinationLabels = MainDestinations.associateWith { destination ->
                    destination.translatedCompactLabel()
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
                            destinationScrollState.scrollToTop(destination.route)
                        } else {
                            navController.navigateSingleTop(destination.route)
                        }
                    }
                }
                val content: @Composable () -> Unit = {
                    Box(Modifier.fillMaxSize()) {
                        AppNavHost(
                            navController = navController,
                            appSettings = settings,
                            sendListState = destinationScrollState.sendListState,
                            catalogListState = destinationScrollState.catalogListState,
                            opdsListState = destinationScrollState.opdsListState,
                            mangaListState = destinationScrollState.mangaListState,
                            settingsScrollState = destinationScrollState.settingsScrollState,
                            modifier = Modifier.fillMaxSize()
                        )

                        UpdateOverlaysHost(
                            appUpdateState = appUpdateState,
                            onLoadUpdateChangelog = onLoadUpdateChangelog,
                            onInstallUpdate = onInstallUpdate,
                            onCancelUpdateDownload = onCancelUpdateDownload,
                            pocketBookServerUpdateState = pocketBookServerUpdateState,
                            onLoadPocketBookServerUpdateChangelog =
                            onLoadPocketBookServerUpdateChangelog,
                            onInstallPocketBookServerUpdate = onInstallPocketBookServerUpdate,
                            onCancelPocketBookServerUpdate = onCancelPocketBookServerUpdate,
                            enableHaptics = settings.enableHaptics
                        )
                    }
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
                                val compactLabel = compactDestinationLabels.getValue(destination)
                                item(
                                    selected = selected,
                                    onClick = {
                                        onNavigationDestinationClick(destination, selected)
                                    },
                                    icon = { Icon(destination.icon, contentDescription = label) },
                                    label = {
                                        AdaptiveSingleLineText(
                                            text = label,
                                            compactText = compactLabel
                                        )
                                    }
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

private fun UploadQueueEvent.toToastMessage(strings: AppStrings): String? = when (this) {
    is UploadFilesSkipped -> skippedFilesMessage(strings)
}

private fun UploadFilesSkipped.skippedFilesMessage(strings: AppStrings): String {
    val skippedFileLabels = files.map { file ->
        "${file.displayName} (${file.skipReasonLabel(strings, maxFileSizeMb)})"
    }
    val tooLargeOrUnsupported = strings.get(
        "send_skip_reason_unsupported_or_too_large",
        maxFileSizeMb
    )
    return if (skippedFileLabels.size == 1) {
        strings.get("send_skipped_file", skippedFileLabels.first())
    } else {
        strings.get("send_skipped_files_summary", skippedFileLabels.size, tooLargeOrUnsupported)
    }
}

private fun SkippedUploadFile.skipReasonLabel(strings: AppStrings, maxFileSizeMb: Int): String =
    when (reason) {
        UploadFileSkipReason.UnsupportedFormat ->
            strings.get("send_skip_reason_unsupported_format")

        UploadFileSkipReason.TooLarge ->
            strings.get("send_skip_reason_too_large", maxFileSizeMb)
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
                val compactLabel = destination.translatedCompactLabel()
                NavigationRailItem(
                    selected = selected,
                    onClick = { onDestinationClick(destination, selected) },
                    icon = { Icon(destination.icon, contentDescription = label) },
                    label = {
                        AdaptiveSingleLineText(
                            text = label,
                            compactText = compactLabel
                        )
                    },
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
            SendRoute(listState = sendListState)
        }
        composable(MainDestination.Catalog.route) {
            CatalogRoute(appSettings = appSettings, listState = catalogListState)
        }
        composable(MainDestination.Opds.route) {
            OpdsRoute(
                appSettings = appSettings,
                opdsListState = opdsListState,
                mangaListState = mangaListState
            )
        }
        composable(MainDestination.Settings.route) {
            SettingsRoute(scrollState = settingsScrollState)
        }
    }
}

@Composable
private fun MainDestination.translatedLabel(): String {
    val strings = com.cybercat.ebooksender.localization.LocalStrings.current
    return when (this) {
        MainDestination.Send -> strings.navSend
        MainDestination.Catalog -> strings.navCatalog
        MainDestination.Opds -> strings.navWeb
        MainDestination.Settings -> strings.navSettings
    }
}

@Composable
private fun MainDestination.translatedCompactLabel(): String {
    val strings = com.cybercat.ebooksender.localization.LocalStrings.current
    return when (this) {
        MainDestination.Send -> strings.navSend
        MainDestination.Catalog -> strings.navCatalog
        MainDestination.Opds -> strings.navWeb
        MainDestination.Settings -> strings.navSettingsCompact
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

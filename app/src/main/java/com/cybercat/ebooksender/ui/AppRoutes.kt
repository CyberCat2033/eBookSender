package com.cybercat.ebooksender.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybercat.ebooksender.feature.catalog.CatalogScreen
import com.cybercat.ebooksender.feature.catalog.CatalogViewModel
import com.cybercat.ebooksender.feature.manga.MangaPane
import com.cybercat.ebooksender.feature.manga.MangaSelectionActions
import com.cybercat.ebooksender.feature.manga.MangaViewModel
import com.cybercat.ebooksender.feature.opds.OpdsScreen
import com.cybercat.ebooksender.feature.opds.OpdsViewModel
import com.cybercat.ebooksender.feature.settings.SettingsScreen
import com.cybercat.ebooksender.feature.settings.SettingsViewModel
import com.cybercat.ebooksender.feature.transfer.SendScreen
import com.cybercat.ebooksender.feature.transfer.TransferViewModel
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.model.AppSettings
import com.cybercat.ebooksender.util.AppHapticFeedback
import com.cybercat.ebooksender.util.performHapticIfAllowed

@Composable
internal fun SendRoute(listState: LazyListState) {
    val transferViewModel: TransferViewModel = hiltViewModel()
    val transferState by transferViewModel.uiState.collectAsStateWithLifecycle()
    val transferRuntimeState by transferViewModel.transferRuntimeState.collectAsStateWithLifecycle()
    SendScreen(
        state = transferState,
        runtimeState = transferRuntimeState,
        listState = listState,
        onFtpInputChanged = transferViewModel::onFtpInputChanged,
        onConnect = transferViewModel::connect,
        onQrScanned = transferViewModel::connectTo,
        onDisconnect = transferViewModel::disconnect,
        onAddUris = transferViewModel::addUris,
        onVisibleQueueChanged = transferViewModel::prioritizeVisibleQueueItems,
        onRemoveItem = transferViewModel::removeItem,
        onClearQueue = transferViewModel::clearQueueAfterDelay,
        onCategoryChanged = transferViewModel::updateCategory,
        onDocumentsTagChanged = transferViewModel::updateDocumentsTag,
        onMangaSeriesChanged = transferViewModel::updateMangaSeries,
        onQueuedMangaSeriesChanged = transferViewModel::updateQueuedMangaSeries,
        onDismissVpnBypassDialog = transferViewModel::dismissVpnBypassDialog,
        onDisableVpnBypass = transferViewModel::disableVpnBypassForLocalConnections,
        onUploadAll = transferViewModel::uploadAll,
        onCancelUpload = transferViewModel::cancelUpload
    )
}

@Composable
internal fun CatalogRoute(appSettings: AppSettings, listState: LazyListState) {
    val catalogViewModel: CatalogViewModel = hiltViewModel()
    val catalogState by catalogViewModel.uiState.collectAsStateWithLifecycle()
    CatalogScreen(
        state = catalogState,
        isConnected = catalogState.connectedDevice != null,
        enableHaptics = appSettings.enableHaptics,
        listState = listState,
        onRefresh = catalogViewModel::reloadDeviceCatalog,
        onSetEditMode = catalogViewModel::setEditMode,
        onToggleFileSelection = catalogViewModel::toggleFileSelection,
        onSetFileSelection = catalogViewModel::setFileSelection,
        onToggleGroupSelection = catalogViewModel::toggleGroupSelection,
        onDeleteSelectedFiles = catalogViewModel::deleteSelectedFiles,
        onClearDeleteError = catalogViewModel::clearDeleteError
    )
}

@Composable
internal fun OpdsRoute(
    appSettings: AppSettings,
    opdsListState: LazyListState,
    mangaListState: LazyListState
) {
    val opdsViewModel: OpdsViewModel = hiltViewModel()
    val opdsState by opdsViewModel.uiState.collectAsStateWithLifecycle()
    val mangaViewModel: MangaViewModel = hiltViewModel()
    val mangaState by mangaViewModel.uiState.collectAsStateWithLifecycle()

    val strings = LocalStrings.current
    val view = LocalView.current
    val context = LocalContext.current
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
            MangaPane(
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
                onSetMangaSeriesFavorite = mangaViewModel::setSelectedMangaFavorite,
                onSetMangaSeriesSubscribed = mangaViewModel::setSelectedMangaSubscribed,
                onCheckSubscriptions = mangaViewModel::checkMangaSubscriptions,
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
                onBrowserSessionRefreshFinished =
                    mangaViewModel::finishBrowserSessionRefresh,
                onCancelDownload = mangaViewModel::cancelMangaDownload,
                onMangaLoginModeChanged = mangaViewModel::setMangaLoginMode,
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
                            AppHapticFeedback.Press
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
            MangaSelectionActions(
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
            AnimatedVisibility(
                visible = selectedCount > 0 && !mangaState.isDownloading,
                enter = fadeIn() + slideInVertically { height -> height },
                exit = fadeOut() + slideOutVertically { height -> height }
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        view.performHapticIfAllowed(
                            context,
                            enableHaptics,
                            AppHapticFeedback.Confirm
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
                AppHapticFeedback.Press
            )
            mangaViewModel.clearMangaChapterSelection()
        }
    )
}

@Composable
internal fun SettingsRoute(scrollState: ScrollState) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        settingsViewModel.scanLocales()
    }
    SettingsScreen(
        state = settingsState,
        scrollState = scrollState,
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
        onBypassVpnForLocalConnectionsChanged =
            settingsViewModel::setBypassVpnForLocalConnections,
        onMangaLoginModeChanged = settingsViewModel::setMangaLoginMode,
        onCheckForUpdates = settingsViewModel::checkForUpdates,
        onInstallUpdate = settingsViewModel::installUpdate,
        onCheckPocketBookServerUpdates = settingsViewModel::checkPocketBookServerUpdates,
        onInstallPocketBookServerUpdate = settingsViewModel::installPocketBookServerUpdate,
        onClearUpdateStatus = settingsViewModel::clearUpdateStatus,
        onClearDownloadCache = settingsViewModel::clearDownloadCache,
        onClearStatusMessage = settingsViewModel::clearStatusMessage,
        onThemeChanged = settingsViewModel::setTheme,
        onLanguageChanged = settingsViewModel::setLanguageCode,
        onWarnOnDisconnectedRenameChanged = settingsViewModel::setWarnOnDisconnectedRename,
        onConfirmPendingRename = settingsViewModel::confirmPendingRename,
        onCancelPendingRename = settingsViewModel::cancelPendingRename,
        onLogoutAll = settingsViewModel::logoutAll,
        onConfirmLogoutAll = settingsViewModel::confirmLogoutAll,
        onDismissLogoutWarning = settingsViewModel::dismissLogoutWarning,
        onResetSettings = settingsViewModel::resetSettings,
        onConfirmResetSettings = settingsViewModel::confirmResetSettings,
        onDismissResetWarning = settingsViewModel::dismissResetWarning
    )
}

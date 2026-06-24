package com.cybercat.ebooksender.feature.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybercat.ebooksender.data.catalog.DeviceCatalogRepository
import com.cybercat.ebooksender.data.manga.CheckMangaSubscriptionsUseCase
import com.cybercat.ebooksender.data.manga.MangaDownloadCoordinator
import com.cybercat.ebooksender.data.manga.MangaDownloadLauncher
import com.cybercat.ebooksender.data.manga.MangaRepository
import com.cybercat.ebooksender.data.settings.SettingsRepository
import com.cybercat.ebooksender.localization.LocalizationManager
import com.cybercat.ebooksender.model.MangaLoginMode
import com.cybercat.ebooksender.util.launchTemporaryStatus
import com.cybercat.ebooksender.util.onFailureRethrowing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MangaViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val settingsRepository: SettingsRepository,
    private val checkMangaSubscriptionsUseCase: CheckMangaSubscriptionsUseCase,
    private val catalogRepository: DeviceCatalogRepository,
    private val downloadCoordinator: MangaDownloadCoordinator,
    private val downloadLauncher: MangaDownloadLauncher,
    private val localizationManager: LocalizationManager
) : ViewModel() {

    private val mutableMangaState = MutableStateFlow(MangaUiState())
    private val browserController = MangaBrowserController(
        mangaRepository = mangaRepository,
        localizationManager = localizationManager,
        mangaState = mutableMangaState,
        scope = viewModelScope,
        showStatus = ::showMangaStatus
    )
    private val searchController = MangaSearchController(
        mangaRepository = mangaRepository,
        catalogRepository = catalogRepository,
        localizationManager = localizationManager,
        mangaState = mutableMangaState,
        scope = viewModelScope,
        showStatus = ::showMangaStatus,
        refreshAuthState = browserController::refreshAuthState,
        requestBrowserSessionRefresh = ::requestBrowserSessionRefresh
    )
    private val downloadController = MangaDownloadController(
        checkMangaSubscriptionsUseCase = checkMangaSubscriptionsUseCase,
        downloadCoordinator = downloadCoordinator,
        downloadLauncher = downloadLauncher,
        localizationManager = localizationManager,
        mangaState = mutableMangaState,
        scope = viewModelScope,
        showStatus = ::showMangaStatus,
        requestBrowserSessionRefresh = ::requestBrowserSessionRefresh
    )
    private var browserSessionRefreshRetry: (() -> Unit)? = null
    private var browserSessionRefreshRequestId = 0L
    private var selectedSourceInitialized = false

    private val mangaContentState = combine(
        mangaRepository.downloadedStableKeys,
        mangaRepository.downloadedChapters,
        mangaRepository.savedSeries,
        catalogRepository.catalog,
        mutableMangaState
    ) { keys, chapters, saved, catalog, mangaState ->
        val selected = mangaState.selectedSeries
        val strings = localizationManager.currentStrings.value
        val lastRead = if (selected != null) {
            MangaCatalogProgressFormatter.lastReadChapterText(selected, catalog, strings)
        } else {
            null
        }

        mangaState.copy(
            sources = mangaRepository.sources,
            downloadedStableKeys = keys,
            downloadedChapters = chapters,
            savedSeries = saved,
            lastReadChapterText = lastRead
        )
    }

    val uiState: StateFlow<MangaUiState> = combine(
        mangaContentState,
        settingsRepository.settings
    ) { mangaState, settings ->
        mangaState.copy(mangaLoginMode = settings.mangaLoginMode)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MangaUiState()
    )

    init {
        viewModelScope.launch {
            mangaRepository.normalizeFavoriteSubscribedState()
        }
        settingsRepository.settings
            .onEach { settings ->
                initializeSelectedMangaSource(settings.selectedMangaSourceId)
            }
            .launchIn(viewModelScope)
        downloadCoordinator.events
            .onEach(downloadController::handleMangaDownloadEvent)
            .launchIn(viewModelScope)
    }

    fun onMangaSearchChanged(value: String) = searchController.onSearchChanged(value)

    fun selectMangaSource(sourceId: String) {
        if (!searchController.selectSource(sourceId)) return
        viewModelScope.launch {
            settingsRepository.setSelectedMangaSourceId(sourceId)
        }
    }

    fun openMangaBrowser(url: String? = null) = browserController.openBrowser(url)

    fun setMangaLoginMode(mode: MangaLoginMode) {
        viewModelScope.launch { settingsRepository.setMangaLoginMode(mode) }
    }

    fun closeMangaBrowser() = browserController.closeBrowser()

    fun performNativeLogin(
        targetUrl: String,
        username: String,
        password: String,
        doNotRemember: Boolean
    ) = browserController.performNativeLogin(
        targetUrl = targetUrl,
        username = username,
        password = password,
        doNotRemember = doNotRemember
    )

    fun clearPendingLoginPost() = browserController.clearPendingLoginPost()

    fun goBackManga() = searchController.goBack()

    fun syncMangaWebPage(url: String, html: String) = browserController.syncWebPage(url)

    fun searchManga() = searchController.search()

    fun openMangaSeries(seriesId: String) = searchController.openSeries(seriesId)

    fun toggleMangaChapter(chapterId: String, selected: Boolean) {
        mutableMangaState.update { state ->
            val selectedIds = if (selected) {
                state.selectedChapterIds + chapterId
            } else {
                state.selectedChapterIds - chapterId
            }
            state.copy(selectedChapterIds = selectedIds)
        }
    }

    fun selectNewMangaChapters() {
        val selectedIds = uiState.value.newChapterIds
        mutableMangaState.update { state ->
            state.copy(selectedChapterIds = selectedIds)
        }
    }

    fun selectAllMangaChapters() {
        mutableMangaState.update { state ->
            state.copy(
                selectedChapterIds = state.chapters.mapTo(mutableSetOf()) { it.chapterId }
            )
        }
    }

    fun clearMangaChapterSelection() {
        mutableMangaState.update { state ->
            state.copy(selectedChapterIds = emptySet())
        }
    }

    fun setSelectedMangaFavorite(favorite: Boolean) {
        val series = mutableMangaState.value.selectedSeries ?: return
        viewModelScope.launch {
            runCatching {
                mangaRepository.setFavorite(series, favorite)
            }.onFailureRethrowing { error ->
                mutableMangaState.update { state ->
                    state.copy(
                        errorMessage =
                            error.message
                                ?: localizationManager.currentStrings.value
                                    .mangaErrorCannotUpdateFavorite,
                        statusMessage = null
                    )
                }
            }
        }
    }

    fun setSelectedMangaSubscribed(subscribed: Boolean) {
        val series = mutableMangaState.value.selectedSeries ?: return
        viewModelScope.launch {
            runCatching {
                mangaRepository.setSubscribed(series, subscribed)
            }.onFailureRethrowing { error ->
                mutableMangaState.update { state ->
                    state.copy(
                        errorMessage =
                            error.message
                                ?: localizationManager.currentStrings.value
                                    .mangaErrorCannotUpdateSubscription,
                        statusMessage = null
                    )
                }
            }
        }
    }

    fun checkMangaSubscriptions() = downloadController.checkMangaSubscriptions()

    fun toggleSubscriptionUpdateChapter(chapterKey: String, selected: Boolean) =
        downloadController.toggleSubscriptionUpdateChapter(chapterKey, selected)

    fun selectAllSubscriptionUpdateChapters() =
        downloadController.selectAllSubscriptionUpdateChapters()

    fun clearSubscriptionUpdateChapters() = downloadController.clearSubscriptionUpdateChapters()

    fun closeSubscriptionUpdates() = downloadController.closeSubscriptionUpdates()

    fun downloadSubscriptionUpdates() = downloadController.downloadSubscriptionUpdates()

    fun downloadSelectedMangaChapters() = downloadController.downloadSelectedMangaChapters()

    fun cancelMangaDownload() = downloadController.cancelActiveDownload()

    fun refreshMangaAuthState(closeBrowserOnAuthenticated: Boolean = false) =
        browserController.refreshAuthState(closeBrowserOnAuthenticated)

    fun finishBrowserSessionRefresh(requestId: Long, success: Boolean) {
        val snapshot = mutableMangaState.value
        if (snapshot.browserSessionRefreshRequest?.id != requestId) return

        val retry = browserSessionRefreshRetry
        browserSessionRefreshRetry = null
        mutableMangaState.update { state ->
            state.copy(
                browserSessionRefreshRequest = null,
                statusMessage = null,
                isLoading = if (success) state.isLoading else false,
                isCheckingSubscriptions = if (success) state.isCheckingSubscriptions else false,
                errorMessage = if (success) {
                    null
                } else {
                    localizationManager.currentStrings.value
                        .get("manga_error_browser_session_required")
                }
            )
        }
        if (success) {
            retry?.invoke()
        }
    }

    private fun showMangaStatus(message: String) {
        viewModelScope.launchTemporaryStatus(
            message = message,
            delayMillis = STATUS_MESSAGE_MILLIS,
            setMessage = { msg ->
                mutableMangaState.update { state ->
                    state.copy(statusMessage = msg, errorMessage = null)
                }
            },
            clearIfStillCurrent = { msg ->
                mutableMangaState.update { state ->
                    if (state.statusMessage == msg) {
                        state.copy(statusMessage = null)
                    } else {
                        state
                    }
                }
            }
        )
    }

    private fun requestBrowserSessionRefresh(url: String, retry: () -> Unit) {
        val requestId = ++browserSessionRefreshRequestId
        browserSessionRefreshRetry = retry
        mutableMangaState.update { state ->
            state.copy(
                browserSessionRefreshRequest = MangaBrowserSessionRefreshRequest(
                    id = requestId,
                    url = url.ifBlank {
                        mangaRepository.homeUrl(state.selectedSourceId)
                    }
                ),
                statusMessage = localizationManager.currentStrings.value
                    .get("manga_status_refreshing_session"),
                errorMessage = null
            )
        }
    }

    private fun initializeSelectedMangaSource(savedSourceId: String) {
        if (selectedSourceInitialized) return
        val sources = mangaRepository.sources
        if (sources.isEmpty()) return

        val selectedSourceId = savedSourceId.takeIf { id ->
            id.isNotBlank() && sources.any { source -> source.id == id }
        } ?: sources.first().id

        if (!searchController.selectSource(selectedSourceId)) return
        selectedSourceInitialized = true

        if (selectedSourceId != savedSourceId) {
            viewModelScope.launch {
                settingsRepository.setSelectedMangaSourceId(selectedSourceId)
            }
        }
    }

    private companion object {
        const val STATUS_MESSAGE_MILLIS = 5000L
    }
}

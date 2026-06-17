package com.cybercat.pocketbooksender.feature.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybercat.pocketbooksender.data.catalog.DeviceCatalogRepository
import com.cybercat.pocketbooksender.data.manga.MangaDownloadCoordinator
import com.cybercat.pocketbooksender.data.manga.MangaDownloadLauncher
import com.cybercat.pocketbooksender.data.manga.MangaRepository
import com.cybercat.pocketbooksender.localization.LocalizationManager
import com.cybercat.pocketbooksender.util.launchTemporaryStatus
import com.cybercat.pocketbooksender.util.onFailureRethrowing
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
    private val catalogRepository: DeviceCatalogRepository,
    private val downloadCoordinator: MangaDownloadCoordinator,
    private val downloadLauncher: MangaDownloadLauncher,
    private val localizationManager: LocalizationManager
) : ViewModel() {

    private val mutableMangaState = MutableStateFlow(MangaUiState())
    private val discoveryController = MangaDiscoveryController(
        mangaRepository = mangaRepository,
        catalogRepository = catalogRepository,
        localizationManager = localizationManager,
        mangaState = mutableMangaState,
        scope = viewModelScope,
        showStatus = ::showMangaStatus
    )
    private val downloadController = MangaDownloadController(
        mangaRepository = mangaRepository,
        downloadCoordinator = downloadCoordinator,
        downloadLauncher = downloadLauncher,
        localizationManager = localizationManager,
        mangaState = mutableMangaState,
        scope = viewModelScope,
        showStatus = ::showMangaStatus
    )

    val uiState: StateFlow<MangaUiState> = combine(
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MangaUiState()
    )

    init {
        viewModelScope.launch {
            mangaRepository.normalizeFavoriteSubscribedState()
        }
        if (
            mutableMangaState.value.selectedSourceId.isBlank() &&
            mangaRepository.sources.isNotEmpty()
        ) {
            selectMangaSource(mangaRepository.sources.first().id)
        }
        downloadCoordinator.events
            .onEach(downloadController::handleMangaDownloadEvent)
            .launchIn(viewModelScope)
    }

    fun onMangaSearchChanged(value: String) = discoveryController.onSearchChanged(value)

    fun selectMangaSource(sourceId: String) = discoveryController.selectSource(sourceId)

    fun openMangaBrowser(url: String? = null) = discoveryController.openBrowser(url)

    fun closeMangaBrowser() = discoveryController.closeBrowser()

    fun performNativeLogin(
        targetUrl: String,
        username: String,
        password: String,
        doNotRemember: Boolean
    ) = discoveryController.performNativeLogin(
        targetUrl = targetUrl,
        username = username,
        password = password,
        doNotRemember = doNotRemember
    )

    fun clearPendingLoginPost() = discoveryController.clearPendingLoginPost()

    fun goBackManga() = discoveryController.goBack()

    fun syncMangaWebPage(url: String, html: String) = discoveryController.syncWebPage(url)

    fun searchManga() = discoveryController.search()

    fun openMangaSeries(seriesId: String) = discoveryController.openSeries(seriesId)

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
        discoveryController.refreshAuthState(closeBrowserOnAuthenticated)

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

    private companion object {
        const val STATUS_MESSAGE_MILLIS = 5000L
    }
}

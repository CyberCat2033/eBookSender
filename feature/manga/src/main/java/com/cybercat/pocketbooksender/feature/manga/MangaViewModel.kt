package com.cybercat.pocketbooksender.feature.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybercat.pocketbooksender.data.catalog.DeviceCatalogRepository
import com.cybercat.pocketbooksender.data.manga.MangaAuthState
import com.cybercat.pocketbooksender.data.manga.MangaChapterDownloadTarget
import com.cybercat.pocketbooksender.data.manga.MangaDownloadCoordinator
import com.cybercat.pocketbooksender.data.manga.MangaDownloadEvent
import com.cybercat.pocketbooksender.data.manga.MangaDownloadLauncher
import com.cybercat.pocketbooksender.data.manga.MangaDownloadRequestKind
import com.cybercat.pocketbooksender.data.manga.MangaRepository
import com.cybercat.pocketbooksender.data.manga.MangaSubscriptionCheckResult
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
    private val localizationManager: com.cybercat.pocketbooksender.localization.LocalizationManager
) : ViewModel() {

    private val mutableMangaState = MutableStateFlow(MangaUiState())
    private var activeDownload: ActiveMangaDownload? = null

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
            .onEach(::handleMangaDownloadEvent)
            .launchIn(viewModelScope)
    }

    fun onMangaSearchChanged(value: String) {
        mutableMangaState.update {
            it.copy(
                searchInput = value,
                errorMessage = null,
                statusMessage = null
            )
        }
    }

    fun selectMangaSource(sourceId: String) {
        val source = mangaRepository.sources.firstOrNull { it.id == sourceId } ?: return
        mutableMangaState.update { state ->
            state.copy(
                selectedSourceId = source.id,
                browserUrl = source.homeUrl,
                currentWebUrl = null,
                searchResults = emptyList(),
                selectedSeries = null,
                chapters = emptyList(),
                selectedChapterIds = emptySet(),
                errorMessage = null,
                statusMessage = null
            )
        }
        refreshMangaAuthState()
    }

    fun openMangaBrowser(url: String? = null) {
        mutableMangaState.update { state ->
            val homeUrl = mangaRepository.homeUrl(state.selectedSourceId)
            state.copy(
                browserVisible = true,
                browserUrl = url ?: homeUrl,
                currentWebUrl = state.currentWebUrl ?: homeUrl
            )
        }
    }

    fun closeMangaBrowser() {
        mutableMangaState.update { state ->
            state.copy(browserVisible = false)
        }
        refreshMangaAuthState()
    }

    fun performNativeLogin(
        targetUrl: String,
        username: String,
        password: String,
        doNotRemember: Boolean
    ) {
        val sourceId = mutableMangaState.value.selectedSourceId
        val postBody = mangaRepository.buildLoginPostBody(
            sourceId,
            username,
            password,
            doNotRemember
        )
        if (postBody != null) {
            mutableMangaState.update { state ->
                state.copy(
                    pendingLoginPost = MangaPendingLoginPost(targetUrl, postBody)
                )
            }
        }
    }

    fun clearPendingLoginPost() {
        mutableMangaState.update { state ->
            state.copy(pendingLoginPost = null)
        }
    }

    fun goBackManga() {
        mutableMangaState.update { state ->
            state.copy(
                selectedSeries = null,
                chapters = emptyList(),
                selectedChapterIds = emptySet(),
                lastReadChapterText = null,
                errorMessage = null,
                statusMessage = null
            )
        }
    }

    fun syncMangaWebPage(url: String, html: String) {
        mutableMangaState.update { state ->
            state.copy(
                currentWebUrl = url,
                errorMessage = null
            )
        }
        refreshMangaAuthState(closeBrowserOnAuthenticated = true)
    }

    fun searchManga() {
        val snapshot = mutableMangaState.value
        val query = snapshot.searchInput.trim()
        if (query.isBlank()) {
            mutableMangaState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value.mangaErrorSearchEmpty
                )
            }
            return
        }

        mutableMangaState.update { state ->
            state.copy(
                isLoading = true,
                browserVisible = false,
                errorMessage = null,
                statusMessage = null,
                searchResults = emptyList(),
                selectedSeries = null,
                chapters = emptyList(),
                selectedChapterIds = emptySet()
            )
        }

        viewModelScope.launch {
            runCatching {
                mangaRepository.searchSeries(snapshot.selectedSourceId, query)
            }.onSuccess { results ->
                mutableMangaState.update { state ->
                    state.copy(
                        searchResults = results,
                        selectedSeries = null,
                        chapters = emptyList(),
                        selectedChapterIds = emptySet(),
                        lastReadChapterText = null,
                        isLoading = false,
                        browserVisible = false,
                        statusMessage = null,
                        errorMessage = null
                    )
                }
                if (results.isEmpty()) {
                    showMangaStatus(
                        localizationManager.currentStrings.value.mangaStatusNoMangaFound
                    )
                }
            }.onFailureRethrowing { error ->
                val strings = localizationManager.currentStrings.value
                mutableMangaState.update { state ->
                    state.copy(
                        isLoading = false,
                        browserVisible = false,
                        isAuthorized = state.isAuthorized &&
                            !MangaErrorMessageMapper.isAuthenticationExpired(error),
                        errorMessage = MangaErrorMessageMapper.errorMessage(
                            error = error,
                            fallback = strings.mangaErrorCannotSearch,
                            strings = strings
                        ),
                        statusMessage = null
                    )
                }
            }
        }
    }

    fun openMangaSeries(seriesId: String) {
        val sourceId = mutableMangaState.value.selectedSourceId
        mutableMangaState.update { state ->
            state.copy(
                isLoading = true,
                browserVisible = false,
                errorMessage = null,
                statusMessage = null,
                selectedSeries = null,
                chapters = emptyList(),
                selectedChapterIds = emptySet()
            )
        }

        viewModelScope.launch {
            runCatching {
                mangaRepository.openSeries(sourceId, seriesId)
            }.onSuccess { seriesPage ->
                val lastRead = MangaCatalogProgressFormatter.lastReadChapterText(
                    series = seriesPage.details,
                    catalog = catalogRepository.catalog.value,
                    strings = localizationManager.currentStrings.value
                )
                mutableMangaState.update { state ->
                    state.copy(
                        selectedSeries = seriesPage.details,
                        selectedSeriesScrollRequest = state.selectedSeriesScrollRequest + 1,
                        chapters = seriesPage.chapters,
                        selectedChapterIds = emptySet(),
                        lastReadChapterText = lastRead,
                        isLoading = false,
                        browserVisible = false,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                val strings = localizationManager.currentStrings.value
                mutableMangaState.update { state ->
                    state.copy(
                        isLoading = false,
                        browserVisible = false,
                        isAuthorized = state.isAuthorized &&
                            !MangaErrorMessageMapper.isAuthenticationExpired(error),
                        errorMessage = MangaErrorMessageMapper.errorMessage(
                            error = error,
                            fallback = strings.mangaErrorCannotOpenSeries,
                            strings = strings
                        ),
                        statusMessage = null
                    )
                }
            }
        }
    }

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

    fun checkMangaSubscriptions() {
        if (mutableMangaState.value.isCheckingSubscriptions) return

        mutableMangaState.update { state ->
            state.copy(
                isCheckingSubscriptions = true,
                statusMessage = localizationManager.currentStrings.value
                    .mangaStatusCheckingSubscriptions,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            runCatching {
                mangaRepository.checkSubscriptions()
            }.onSuccess { results ->
                val updatesWithNews = results.filter { it.newChapters.isNotEmpty() }
                if (updatesWithNews.isEmpty()) {
                    mutableMangaState.update { state ->
                        state.copy(
                            isCheckingSubscriptions = false,
                            statusMessage = null,
                            errorMessage = null
                        )
                    }
                    showMangaStatus(
                        localizationManager.currentStrings.value.mangaStatusNoNewChapters
                    )
                    return@onSuccess
                }

                val allNewChapterKeys = MangaSubscriptionUpdateReducer.allChapterKeys(
                    updatesWithNews
                )

                mutableMangaState.update { state ->
                    state.copy(
                        isCheckingSubscriptions = false,
                        subscriptionUpdates = updatesWithNews,
                        subscriptionUpdatesVisible = true,
                        selectedSubscriptionUpdateChapterKeys = allNewChapterKeys,
                        statusMessage = null,
                        errorMessage = null
                    )
                }
            }.onFailureRethrowing { error ->
                val strings = localizationManager.currentStrings.value
                mutableMangaState.update { state ->
                    state.copy(
                        isCheckingSubscriptions = false,
                        statusMessage = null,
                        isAuthorized = state.isAuthorized &&
                            !MangaErrorMessageMapper.isAuthenticationExpired(error),
                        errorMessage = MangaErrorMessageMapper.errorMessage(
                            error = error,
                            fallback = strings.mangaErrorCannotCheckSubscriptions,
                            strings = strings
                        )
                    )
                }
            }
        }
    }

    fun toggleSubscriptionUpdateChapter(chapterKey: String, selected: Boolean) {
        mutableMangaState.update { state ->
            val selectedKeys = if (selected) {
                state.selectedSubscriptionUpdateChapterKeys + chapterKey
            } else {
                state.selectedSubscriptionUpdateChapterKeys - chapterKey
            }
            state.copy(selectedSubscriptionUpdateChapterKeys = selectedKeys)
        }
    }

    fun selectAllSubscriptionUpdateChapters() {
        mutableMangaState.update { state ->
            val allKeys = MangaSubscriptionUpdateReducer.allChapterKeys(state.subscriptionUpdates)
            state.copy(selectedSubscriptionUpdateChapterKeys = allKeys)
        }
    }

    fun clearSubscriptionUpdateChapters() {
        mutableMangaState.update { state ->
            state.copy(selectedSubscriptionUpdateChapterKeys = emptySet())
        }
    }

    fun closeSubscriptionUpdates() {
        mutableMangaState.update { state ->
            state.copy(
                subscriptionUpdatesVisible = false,
                subscriptionUpdates = emptyList(),
                selectedSubscriptionUpdateChapterKeys = emptySet()
            )
        }
    }

    fun downloadSubscriptionUpdates() {
        val snapshot = mutableMangaState.value
        val selectedKeys = snapshot.selectedSubscriptionUpdateChapterKeys
        val targets = MangaSubscriptionUpdateReducer.selectedTargets(
            updates = snapshot.subscriptionUpdates,
            selectedKeys = selectedKeys
        )

        if (targets.isEmpty()) {
            mutableMangaState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value
                        .mangaErrorSelectChaptersFirst
                )
            }
            return
        }

        val strings = localizationManager.currentStrings.value
        val requestId = downloadCoordinator.submit(
            targets = targets,
            kind = MangaDownloadRequestKind.SubscriptionUpdates
        )
        activeDownload = ActiveMangaDownload(
            requestId = requestId,
            kind = MangaDownloadRequestKind.SubscriptionUpdates,
            selectedSubscriptionKeys = selectedKeys,
            subscriptionUpdates = snapshot.subscriptionUpdates
        )
        mutableMangaState.update { state ->
            state.copy(
                subscriptionUpdatesVisible = false,
                isDownloading = true,
                downloadProgress = MangaDownloadProgressFormatter.initialProgress(
                    strings,
                    targets.size
                ),
                errorMessage = null,
                statusMessage = null
            )
        }
        downloadLauncher.startMangaDownload(requestId)
    }

    fun downloadSelectedMangaChapters() {
        val snapshot = mutableMangaState.value
        val series = snapshot.selectedSeries
        if (series == null) {
            mutableMangaState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value
                        .mangaErrorOpenSeriesFirst
                )
            }
            return
        }

        val selectedChapters = snapshot.selectedChapters
        if (selectedChapters.isEmpty()) {
            mutableMangaState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value
                        .mangaErrorSelectChaptersFirst
                )
            }
            return
        }

        val strings = localizationManager.currentStrings.value
        val targets = selectedChapters.map { chapter ->
            MangaChapterDownloadTarget(series, chapter)
        }
        val requestId = downloadCoordinator.submit(
            targets = targets,
            kind = MangaDownloadRequestKind.SelectedChapters
        )
        activeDownload = ActiveMangaDownload(
            requestId = requestId,
            kind = MangaDownloadRequestKind.SelectedChapters,
            selectedSubscriptionKeys = emptySet(),
            subscriptionUpdates = emptyList()
        )
        mutableMangaState.update { state ->
            state.copy(
                isDownloading = true,
                downloadProgress = MangaDownloadProgressFormatter.initialProgress(
                    strings,
                    selectedChapters.size
                ),
                errorMessage = null,
                statusMessage = null
            )
        }
        downloadLauncher.startMangaDownload(requestId)
    }

    private fun handleMangaDownloadEvent(event: MangaDownloadEvent) {
        when (event) {
            is MangaDownloadEvent.Started -> Unit

            is MangaDownloadEvent.Progress -> {
                if (activeDownload?.requestId != event.requestId) return
                mutableMangaState.update { state ->
                    state.copy(
                        downloadProgress = MangaDownloadProgressFormatter.format(
                            progress = event.progress,
                            strings = localizationManager.currentStrings.value,
                            previousProgress = state.downloadProgress?.progress
                        )
                    )
                }
            }

            is MangaDownloadEvent.Completed -> {
                val active = activeDownload?.takeIf { it.requestId == event.requestId } ?: return
                activeDownload = null
                when (active.kind) {
                    MangaDownloadRequestKind.SelectedChapters -> completeSelectedChapterDownload(
                        event
                    )

                    MangaDownloadRequestKind.SubscriptionUpdates ->
                        completeSubscriptionUpdateDownload(active, event)
                }
                if (event.addedToQueueCount > 0) {
                    showMangaStatus(
                        localizationManager.currentStrings.value.get(
                            "manga_status_added_to_queue",
                            event.addedToQueueCount
                        )
                    )
                }
            }

            is MangaDownloadEvent.Failed -> {
                if (activeDownload?.requestId != event.requestId) return
                val active = activeDownload
                activeDownload = null
                mutableMangaState.update { state ->
                    state.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        subscriptionUpdatesVisible =
                            active?.kind == MangaDownloadRequestKind.SubscriptionUpdates &&
                                active.subscriptionUpdates.isNotEmpty(),
                        isAuthorized = state.isAuthorized &&
                            !MangaErrorMessageMapper.isAuthenticationExpired(event.message),
                        errorMessage = MangaErrorMessageMapper.errorMessage(
                            event.message,
                            localizationManager.currentStrings.value
                        )
                    )
                }
            }
        }
    }

    private fun completeSelectedChapterDownload(event: MangaDownloadEvent.Completed) {
        mutableMangaState.update { state ->
            state.copy(
                isDownloading = false,
                downloadProgress = null,
                selectedChapterIds = state.selectedChapterIds - event.downloadedChapterIds,
                errorMessage = MangaErrorMessageMapper.formatFailures(
                    event.failedMessages,
                    localizationManager.currentStrings.value
                )
            )
        }
    }

    private fun completeSubscriptionUpdateDownload(
        active: ActiveMangaDownload,
        event: MangaDownloadEvent.Completed
    ) {
        val remainingUpdates = if (event.failedMessages.isEmpty()) {
            emptyList()
        } else {
            MangaSubscriptionUpdateReducer.remainingAfterDownload(
                updates = active.subscriptionUpdates,
                selectedKeys = active.selectedSubscriptionKeys,
                downloadedKeys = event.downloadedSubscriptionKeys
            )
        }
        val remainingKeys = MangaSubscriptionUpdateReducer.allChapterKeys(remainingUpdates)

        mutableMangaState.update { state ->
            state.copy(
                isDownloading = false,
                downloadProgress = null,
                subscriptionUpdates = remainingUpdates,
                subscriptionUpdatesVisible = remainingUpdates.isNotEmpty(),
                selectedSubscriptionUpdateChapterKeys = remainingKeys,
                errorMessage = MangaErrorMessageMapper.formatFailures(
                    event.failedMessages,
                    localizationManager.currentStrings.value
                )
            )
        }
    }

    fun refreshMangaAuthState(closeBrowserOnAuthenticated: Boolean = false) {
        viewModelScope.launch {
            val sourceId = mutableMangaState.value.selectedSourceId
            val authState = mangaRepository.authState(sourceId)
            val isAuth =
                authState is MangaAuthState.Authenticated || authState is MangaAuthState.NotRequired
            var shouldShowLoginSuccess = false
            mutableMangaState.update { state ->
                val closeBrowser = closeBrowserOnAuthenticated &&
                    authState is MangaAuthState.Authenticated &&
                    state.browserVisible &&
                    !state.isAuthorized
                if (closeBrowser) {
                    shouldShowLoginSuccess = true
                }
                state.copy(
                    isAuthorized = isAuth,
                    browserVisible = if (closeBrowser) false else state.browserVisible
                )
            }
            if (shouldShowLoginSuccess) {
                showMangaStatus(localizationManager.currentStrings.value.mangaStatusLoginSuccess)
            }
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

    private companion object {
        const val STATUS_MESSAGE_MILLIS = 5000L
    }
}

private data class ActiveMangaDownload(
    val requestId: String,
    val kind: MangaDownloadRequestKind,
    val selectedSubscriptionKeys: Set<String>,
    val subscriptionUpdates: List<MangaSubscriptionCheckResult>
)

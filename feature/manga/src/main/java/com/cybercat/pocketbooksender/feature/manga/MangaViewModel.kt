package com.cybercat.pocketbooksender.feature.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybercat.pocketbooksender.data.catalog.DeviceCatalogRepository
import com.cybercat.pocketbooksender.data.manga.MangaAuthState
import com.cybercat.pocketbooksender.data.manga.MangaChapter
import com.cybercat.pocketbooksender.data.manga.MangaChapterDownload
import com.cybercat.pocketbooksender.data.manga.MangaChapterDownloadTarget
import com.cybercat.pocketbooksender.data.manga.MangaDownloadCoordinator
import com.cybercat.pocketbooksender.data.manga.MangaDownloadEvent
import com.cybercat.pocketbooksender.data.manga.MangaDownloadLauncher
import com.cybercat.pocketbooksender.data.manga.MangaDownloadProgress
import com.cybercat.pocketbooksender.data.manga.MangaDownloadRequestKind
import com.cybercat.pocketbooksender.data.manga.MangaRepository
import com.cybercat.pocketbooksender.data.manga.MangaSeriesBookmark
import com.cybercat.pocketbooksender.data.manga.MangaSeriesDetails
import com.cybercat.pocketbooksender.model.DeviceCatalog
import com.cybercat.pocketbooksender.util.formatBytes
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
    private val localizationManager: com.cybercat.pocketbooksender.localization.LocalizationManager,
) : ViewModel() {

    private val _mangaState = MutableStateFlow(MangaUiState())
    private var activeDownload: ActiveMangaDownload? = null

    val uiState: StateFlow<MangaUiState> = combine(
        mangaRepository.downloadedStableKeys,
        mangaRepository.downloadedChapters,
        mangaRepository.savedSeries,
        catalogRepository.catalog,
        _mangaState
    ) { keys: Set<String>,
        chapters: List<MangaChapterDownload>,
        saved: List<MangaSeriesBookmark>,
        catalog: DeviceCatalog,
        mangaState: MangaUiState ->
        val selected = mangaState.selectedSeries
        val lastRead = if (selected != null) {
            lastReadChapterText(selected, catalog)
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
        if (_mangaState.value.selectedSourceId.isBlank() && mangaRepository.sources.isNotEmpty()) {
            selectMangaSource(mangaRepository.sources.first().id)
        }
        downloadCoordinator.events
            .onEach(::handleMangaDownloadEvent)
            .launchIn(viewModelScope)
    }

    fun onMangaSearchChanged(value: String) {
        _mangaState.update {
            it.copy(
                searchInput = value,
                errorMessage = null,
                statusMessage = null,
            )
        }
    }

    fun selectMangaSource(sourceId: String) {
        val source = mangaRepository.sources.firstOrNull { it.id == sourceId } ?: return
        _mangaState.update { state ->
            state.copy(
                selectedSourceId = source.id,
                browserUrl = source.homeUrl,
                currentWebUrl = null,
                searchResults = emptyList(),
                selectedSeries = null,
                chapters = emptyList(),
                selectedChapterIds = emptySet(),
                errorMessage = null,
                statusMessage = null,
            )
        }
        refreshMangaAuthState()
    }

    fun openMangaBrowser(url: String? = null) {
        _mangaState.update { state ->
            val homeUrl = mangaRepository.homeUrl(state.selectedSourceId)
            state.copy(
                browserVisible = true,
                browserUrl = url ?: homeUrl,
                currentWebUrl = state.currentWebUrl ?: homeUrl,
            )
        }
    }

    fun closeMangaBrowser() {
        _mangaState.update { state ->
            state.copy(browserVisible = false)
        }
        refreshMangaAuthState()
    }

    fun performNativeLogin(targetUrl: String, username: String, password: String, doNotRemember: Boolean) {
        val sourceId = _mangaState.value.selectedSourceId
        val postBody = mangaRepository.buildLoginPostBody(sourceId, username, password, doNotRemember)
        if (postBody != null) {
            _mangaState.update { state ->
                state.copy(
                    pendingLoginPost = MangaPendingLoginPost(targetUrl, postBody)
                )
            }
        }
    }

    fun clearPendingLoginPost() {
        _mangaState.update { state ->
            state.copy(pendingLoginPost = null)
        }
    }

    fun goBackManga() {
        _mangaState.update { state ->
            state.copy(
                selectedSeries = null,
                chapters = emptyList(),
                selectedChapterIds = emptySet(),
                lastReadChapterText = null,
                errorMessage = null,
                statusMessage = null,
            )
        }
    }

    fun syncMangaWebPage(url: String, html: String) {
        _mangaState.update { state ->
            state.copy(
                currentWebUrl = url,
                errorMessage = null,
            )
        }
        refreshMangaAuthState(closeBrowserOnAuthenticated = true)
    }

    fun searchManga() {
        val snapshot = _mangaState.value
        val query = snapshot.searchInput.trim()
        if (query.isBlank()) {
            _mangaState.update { it.copy(errorMessage = localizationManager.currentStrings.value.mangaErrorSearchEmpty) }
            return
        }

        _mangaState.update { state ->
            state.copy(
                isLoading = true,
                browserVisible = false,
                errorMessage = null,
                statusMessage = null,
                searchResults = emptyList(),
                selectedSeries = null,
                chapters = emptyList(),
                selectedChapterIds = emptySet(),
            )
        }

        viewModelScope.launch {
            runCatching {
                mangaRepository.searchSeries(snapshot.selectedSourceId, query)
            }.onSuccess { results ->
                _mangaState.update { state ->
                    state.copy(
                        searchResults = results,
                        selectedSeries = null,
                        chapters = emptyList(),
                        selectedChapterIds = emptySet(),
                        lastReadChapterText = null,
                        isLoading = false,
                        browserVisible = false,
                        statusMessage = null,
                        errorMessage = null,
                    )
                }
                if (results.isEmpty()) {
                    showMangaStatus(localizationManager.currentStrings.value.mangaStatusNoMangaFound)
                }
            }.onFailureRethrowing { error ->
                _mangaState.update { state ->
                    state.copy(
                        isLoading = false,
                        browserVisible = false,
                        errorMessage = error.message ?: localizationManager.currentStrings.value.mangaErrorCannotSearch,
                        statusMessage = null,
                    )
                }
            }
        }
    }

    fun openMangaSeries(seriesId: String) {
        val sourceId = _mangaState.value.selectedSourceId
        _mangaState.update { state ->
            state.copy(
                isLoading = true,
                browserVisible = false,
                errorMessage = null,
                statusMessage = null,
                selectedSeries = null,
                chapters = emptyList(),
                selectedChapterIds = emptySet(),
            )
        }

        viewModelScope.launch {
            runCatching {
                mangaRepository.openSeries(sourceId, seriesId)
            }.onSuccess { seriesPage ->
                val lastRead = lastReadChapterText(seriesPage.details, catalogRepository.catalog.value)
                _mangaState.update { state ->
                    state.copy(
                        selectedSeries = seriesPage.details,
                        selectedSeriesScrollRequest = state.selectedSeriesScrollRequest + 1,
                        chapters = seriesPage.chapters,
                        selectedChapterIds = emptySet(),
                        lastReadChapterText = lastRead,
                        isLoading = false,
                        browserVisible = false,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                _mangaState.update { state ->
                    state.copy(
                        isLoading = false,
                        browserVisible = false,
                        errorMessage = error.message ?: localizationManager.currentStrings.value.mangaErrorCannotOpenSeries,
                        statusMessage = null,
                    )
                }
            }
        }
    }

    fun toggleMangaChapter(chapterId: String, selected: Boolean) {
        _mangaState.update { state ->
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
        _mangaState.update { state ->
            state.copy(selectedChapterIds = selectedIds)
        }
    }

    fun selectAllMangaChapters() {
        _mangaState.update { state ->
            state.copy(
                selectedChapterIds = state.chapters.mapTo(mutableSetOf()) { it.chapterId }
            )
        }
    }

    fun clearMangaChapterSelection() {
        _mangaState.update { state ->
            state.copy(selectedChapterIds = emptySet())
        }
    }

    fun setSelectedMangaFavorite(favorite: Boolean) {
        val series = _mangaState.value.selectedSeries ?: return
        viewModelScope.launch {
            runCatching {
                mangaRepository.setFavorite(series, favorite)
            }.onFailureRethrowing { error ->
                _mangaState.update { state ->
                    state.copy(
                        errorMessage = error.message ?: localizationManager.currentStrings.value.mangaErrorCannotUpdateFavorite,
                        statusMessage = null,
                    )
                }
            }
        }
    }

    fun setSelectedMangaSubscribed(subscribed: Boolean) {
        val series = _mangaState.value.selectedSeries ?: return
        viewModelScope.launch {
            runCatching {
                mangaRepository.setSubscribed(series, subscribed)
            }.onFailureRethrowing { error ->
                _mangaState.update { state ->
                    state.copy(
                        errorMessage = error.message ?: localizationManager.currentStrings.value.mangaErrorCannotUpdateSubscription,
                        statusMessage = null,
                    )
                }
            }
        }
    }

    fun checkMangaSubscriptions() {
        if (_mangaState.value.isCheckingSubscriptions) return

        _mangaState.update { state ->
            state.copy(
                isCheckingSubscriptions = true,
                statusMessage = localizationManager.currentStrings.value.mangaStatusCheckingSubscriptions,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                mangaRepository.checkSubscriptions()
            }.onSuccess { results ->
                val updatesWithNews = results.filter { it.newChapters.isNotEmpty() }
                if (updatesWithNews.isEmpty()) {
                    _mangaState.update { state ->
                        state.copy(
                            isCheckingSubscriptions = false,
                            statusMessage = null,
                            errorMessage = null,
                        )
                    }
                    showMangaStatus(localizationManager.currentStrings.value.mangaStatusNoNewChapters)
                    return@onSuccess
                }

                val allNewChapterKeys = updatesWithNews.flatMap { update ->
                    update.newChapters.map { it.subscriptionUpdateSelectionKey() }
                }.toSet()

                _mangaState.update { state ->
                    state.copy(
                        isCheckingSubscriptions = false,
                        subscriptionUpdates = updatesWithNews,
                        subscriptionUpdatesVisible = true,
                        selectedSubscriptionUpdateChapterKeys = allNewChapterKeys,
                        statusMessage = null,
                        errorMessage = null,
                    )
                }
            }.onFailureRethrowing { error ->
                _mangaState.update { state ->
                    state.copy(
                        isCheckingSubscriptions = false,
                        statusMessage = null,
                        errorMessage = error.message ?: localizationManager.currentStrings.value.mangaErrorCannotCheckSubscriptions,
                    )
                }
            }
        }
    }

    fun toggleSubscriptionUpdateChapter(chapterKey: String, selected: Boolean) {
        _mangaState.update { state ->
            val selectedKeys = if (selected) {
                state.selectedSubscriptionUpdateChapterKeys + chapterKey
            } else {
                state.selectedSubscriptionUpdateChapterKeys - chapterKey
            }
            state.copy(selectedSubscriptionUpdateChapterKeys = selectedKeys)
        }
    }

    fun selectAllSubscriptionUpdateChapters() {
        _mangaState.update { state ->
            val allKeys = state.subscriptionUpdates.flatMap { update ->
                update.newChapters.map { it.subscriptionUpdateSelectionKey() }
            }.toSet()
            state.copy(selectedSubscriptionUpdateChapterKeys = allKeys)
        }
    }

    fun clearSubscriptionUpdateChapters() {
        _mangaState.update { state ->
            state.copy(selectedSubscriptionUpdateChapterKeys = emptySet())
        }
    }

    fun closeSubscriptionUpdates() {
        _mangaState.update { state ->
            state.copy(
                subscriptionUpdatesVisible = false,
                subscriptionUpdates = emptyList(),
                selectedSubscriptionUpdateChapterKeys = emptySet(),
            )
        }
    }

    fun downloadSubscriptionUpdates() {
        val snapshot = _mangaState.value
        val selectedKeys = snapshot.selectedSubscriptionUpdateChapterKeys
        val targets = snapshot.subscriptionUpdates.flatMap { update ->
            update.newChapters
                .filter { it.subscriptionUpdateSelectionKey() in selectedKeys }
                .map { chapter -> MangaChapterDownloadTarget(update.page.details, chapter) }
        }

        if (targets.isEmpty()) {
            _mangaState.update { it.copy(errorMessage = localizationManager.currentStrings.value.mangaErrorSelectChaptersFirst) }
            return
        }

        val strings = localizationManager.currentStrings.value
        val requestId = downloadCoordinator.submit(
            targets = targets,
            kind = MangaDownloadRequestKind.SubscriptionUpdates,
        )
        activeDownload = ActiveMangaDownload(
            requestId = requestId,
            kind = MangaDownloadRequestKind.SubscriptionUpdates,
            selectedSubscriptionKeys = selectedKeys,
            subscriptionUpdates = snapshot.subscriptionUpdates,
        )
        _mangaState.update { state ->
            state.copy(
                subscriptionUpdatesVisible = false,
                isDownloading = true,
                downloadProgress = MangaDownloadUiProgress(
                    title = strings.mangaStatusDownloadPreparing,
                    detail = when (targets.size) {
                        1 -> strings.mangaStatusDownloadOneChapterSelected
                        else -> strings.get("manga_status_download_chapters_selected", targets.size)
                    },
                    currentChapterTitle = null,
                    progress = null,
                ),
                errorMessage = null,
                statusMessage = null,
            )
        }
        downloadLauncher.startMangaDownload(requestId)
    }

    fun downloadSelectedMangaChapters() {
        val snapshot = _mangaState.value
        val series = snapshot.selectedSeries
        if (series == null) {
            _mangaState.update { it.copy(errorMessage = localizationManager.currentStrings.value.mangaErrorOpenSeriesFirst) }
            return
        }

        val selectedChapters = snapshot.selectedChapters
        if (selectedChapters.isEmpty()) {
            _mangaState.update { it.copy(errorMessage = localizationManager.currentStrings.value.mangaErrorSelectChaptersFirst) }
            return
        }

        val strings = localizationManager.currentStrings.value
        val targets = selectedChapters.map { chapter ->
            MangaChapterDownloadTarget(series, chapter)
        }
        val requestId = downloadCoordinator.submit(
            targets = targets,
            kind = MangaDownloadRequestKind.SelectedChapters,
        )
        activeDownload = ActiveMangaDownload(
            requestId = requestId,
            kind = MangaDownloadRequestKind.SelectedChapters,
            selectedSubscriptionKeys = emptySet(),
            subscriptionUpdates = emptyList(),
        )
        _mangaState.update { state ->
            state.copy(
                isDownloading = true,
                downloadProgress = MangaDownloadUiProgress(
                    title = strings.mangaStatusDownloadPreparing,
                    detail = when (selectedChapters.size) {
                        1 -> strings.mangaStatusDownloadOneChapterSelected
                        else -> strings.get("manga_status_download_chapters_selected", selectedChapters.size)
                    },
                    currentChapterTitle = null,
                    progress = null,
                ),
                errorMessage = null,
                statusMessage = null,
            )
        }
        downloadLauncher.startMangaDownload(requestId)
    }

    private fun handleMangaDownloadEvent(event: MangaDownloadEvent) {
        when (event) {
            is MangaDownloadEvent.Started -> Unit
            is MangaDownloadEvent.Progress -> {
                if (activeDownload?.requestId != event.requestId) return
                _mangaState.update { state ->
                    state.copy(
                        downloadProgress = event.progress.toUiProgress(
                            previousProgress = state.downloadProgress?.progress,
                        ),
                    )
                }
            }
            is MangaDownloadEvent.Completed -> {
                val active = activeDownload?.takeIf { it.requestId == event.requestId } ?: return
                activeDownload = null
                when (active.kind) {
                    MangaDownloadRequestKind.SelectedChapters -> completeSelectedChapterDownload(event)
                    MangaDownloadRequestKind.SubscriptionUpdates -> completeSubscriptionUpdateDownload(active, event)
                }
                if (event.addedToQueueCount > 0) {
                    showMangaStatus(
                        localizationManager.currentStrings.value.get(
                            "manga_status_added_to_queue",
                            event.addedToQueueCount,
                        ),
                    )
                }
            }
            is MangaDownloadEvent.Failed -> {
                if (activeDownload?.requestId != event.requestId) return
                val active = activeDownload
                activeDownload = null
                _mangaState.update { state ->
                    state.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        subscriptionUpdatesVisible =
                            active?.kind == MangaDownloadRequestKind.SubscriptionUpdates &&
                                active.subscriptionUpdates.isNotEmpty(),
                        errorMessage = event.message,
                    )
                }
            }
        }
    }

    private fun completeSelectedChapterDownload(event: MangaDownloadEvent.Completed) {
        _mangaState.update { state ->
            state.copy(
                isDownloading = false,
                downloadProgress = null,
                selectedChapterIds = state.selectedChapterIds - event.downloadedChapterIds,
                errorMessage = formatMangaFailures(event.failedMessages),
            )
        }
    }

    private fun completeSubscriptionUpdateDownload(
        active: ActiveMangaDownload,
        event: MangaDownloadEvent.Completed,
    ) {
        val remainingUpdates = if (event.failedMessages.isEmpty()) {
            emptyList()
        } else {
            active.subscriptionUpdates.mapNotNull { update ->
                val remainingChapters = update.newChapters.filter { chapter ->
                    val key = chapter.subscriptionUpdateSelectionKey()
                    key in active.selectedSubscriptionKeys && key !in event.downloadedSubscriptionKeys
                }
                if (remainingChapters.isEmpty()) {
                    null
                } else {
                    update.copy(newChapters = remainingChapters)
                }
            }
        }
        val remainingKeys = remainingUpdates.flatMap { update ->
            update.newChapters.map { chapter -> chapter.subscriptionUpdateSelectionKey() }
        }.toSet()

        _mangaState.update { state ->
            state.copy(
                isDownloading = false,
                downloadProgress = null,
                subscriptionUpdates = remainingUpdates,
                subscriptionUpdatesVisible = remainingUpdates.isNotEmpty(),
                selectedSubscriptionUpdateChapterKeys = remainingKeys,
                errorMessage = formatMangaFailures(event.failedMessages),
            )
        }
    }

    fun refreshMangaAuthState(closeBrowserOnAuthenticated: Boolean = false) {
        viewModelScope.launch {
            val sourceId = _mangaState.value.selectedSourceId
            val authState = mangaRepository.authState(sourceId)
            val isAuth = authState is MangaAuthState.Authenticated || authState is MangaAuthState.NotRequired
            var shouldShowLoginSuccess = false
            _mangaState.update { state ->
                val closeBrowser = closeBrowserOnAuthenticated &&
                    authState is MangaAuthState.Authenticated &&
                    state.browserVisible &&
                    !state.isAuthorized
                if (closeBrowser) {
                    shouldShowLoginSuccess = true
                }
                state.copy(
                    isAuthorized = isAuth,
                    browserVisible = if (closeBrowser) false else state.browserVisible,
                )
            }
            if (shouldShowLoginSuccess) {
                showMangaStatus(localizationManager.currentStrings.value.mangaStatusLoginSuccess)
            }
        }
    }

    private fun lastReadChapterText(
        series: MangaSeriesDetails,
        catalog: DeviceCatalog,
    ): String? {
        val seriesKey = series.title.catalogMatchKey()
        if (seriesKey.isBlank()) return null

        val group = catalog.manga.firstOrNull { mangaGroup ->
            val groupKey = mangaGroup.name.catalogMatchKey()
            groupKey.isNotBlank() && (groupKey == seriesKey || groupKey in seriesKey || seriesKey in groupKey)
        } ?: return null

        val file = group.lastReadFile ?: return null
        val progress = when {
            file.completed -> localizationManager.currentStrings.value.mangaProgressCompleted
            file.readProgressPercent != null -> "${file.readProgressPercent}%"
            else -> null
        }
        return if (progress == null) {
            file.title ?: file.name
        } else {
            "${file.title ?: file.name} · $progress"
        }
    }

    private fun showMangaStatus(message: String) {
        viewModelScope.launchTemporaryStatus(
            message = message,
            delayMillis = StatusMessageMillis,
            setMessage = { msg ->
                _mangaState.update { state ->
                    state.copy(statusMessage = msg, errorMessage = null)
                }
            },
            clearIfStillCurrent = { msg ->
                _mangaState.update { state ->
                    if (state.statusMessage == msg) {
                        state.copy(statusMessage = null)
                    } else {
                        state
                    }
                }
            }
        )
    }

    private fun MangaDownloadProgress.toUiProgress(
        previousProgress: Float?,
    ): MangaDownloadUiProgress {
        val safeTotalChapters = totalChapters.coerceAtLeast(1)
        val completedChapterCount = completedChapters.coerceIn(0, safeTotalChapters)
        val allChaptersComplete = completedChapterCount >= safeTotalChapters
        val archiveTotal = archiveTotalBytes?.takeIf { it > 0L }
        val archiveFraction = archiveTotal?.let { total ->
            archiveBytesRead.toFloat() / total.toFloat()
        }
        val pageFraction = when {
            allChaptersComplete -> 0f
            archiveFraction != null -> archiveFraction
            totalPages > 0 -> completedPages.toFloat() / totalPages.toFloat()
            else -> 0f
        }.coerceIn(0f, 1f)
        val progressCap = if (allChaptersComplete) 1f else 0.99f
        val previousSafeProgress = previousProgress?.coerceAtMost(progressCap) ?: 0f
        val progress = ((completedChapterCount + pageFraction) / safeTotalChapters)
            .coerceIn(0f, 1f)
            .coerceAtMost(progressCap)
            .coerceAtLeast(previousSafeProgress)

        val strings = localizationManager.currentStrings.value
        val step = when {
            detail.equals("Preparing", ignoreCase = true) -> strings.mangaProgressStepPreparing
            detail.equals("Downloading archive", ignoreCase = true) -> strings.mangaProgressStepDownloadingArchive
            detail.equals("Archive downloaded", ignoreCase = true) -> strings.mangaProgressStepArchiveSaved
            detail.equals("Archive unavailable, downloading pages", ignoreCase = true) ->
                strings.mangaProgressStepSwitchingToPages
            !detail.isNullOrBlank() -> detail
            totalPages > 0 -> strings.mangaProgressStepDownloadingPages
            else -> strings.mangaProgressStepDownloadingChapter
        }
        val chapterProgressText = if (allChaptersComplete) {
            strings.mangaProgressAllChaptersSaved
        } else {
            strings.get("manga_progress_chapters_done", completedChapterCount, safeTotalChapters)
        }
        val detailText = when {
            allChaptersComplete ->
                chapterProgressText
            archiveTotal != null && detail.equals("Downloading archive", ignoreCase = true) ->
                strings.get("manga_progress_detail_archive", chapterProgressText, archiveBytesRead.formatBytes(), archiveTotal.formatBytes())
            totalPages > 0 && completedPages >= totalPages ->
                strings.get("manga_progress_detail_finalizing", chapterProgressText)
            totalPages > 0 && detail.isNullOrBlank() ->
                strings.get("manga_progress_detail_page", chapterProgressText, completedPages, totalPages)
            else ->
                strings.get("manga_progress_detail_generic", chapterProgressText, step ?: "")
        }

        return MangaDownloadUiProgress(
            title = strings.get("manga_download_progress_title", (progress * 100).toInt()),
            detail = detailText,
            currentChapterTitle = chapterTitle,
            progress = progress,
        )
    }

    private fun formatMangaFailures(failedMessages: List<String>): String? {
        if (failedMessages.isEmpty()) return null
        val strings = localizationManager.currentStrings.value
        val visible = failedMessages.take(3).joinToString("\n") { message ->
            message.replace(MangaNetworkUnavailableMessage, strings.get("manga_error_network_unavailable"))
        }
        val hiddenCount = failedMessages.size - 3
        return if (hiddenCount > 0) {
            strings.get("manga_error_failures_summary", visible, hiddenCount)
        } else {
            visible
        }
    }

    private fun String.catalogMatchKey(): String =
        lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")

    private companion object {
        const val StatusMessageMillis = 5000L
        const val MangaNetworkUnavailableMessage = "MANGA_NETWORK_UNAVAILABLE"
    }
}

private data class ActiveMangaDownload(
    val requestId: String,
    val kind: MangaDownloadRequestKind,
    val selectedSubscriptionKeys: Set<String>,
    val subscriptionUpdates: List<com.cybercat.pocketbooksender.data.manga.MangaSubscriptionCheckResult>,
)

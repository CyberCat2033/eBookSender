package com.cybercat.pocketbooksender.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybercat.pocketbooksender.data.catalog.DeviceCatalogRepository
import com.cybercat.pocketbooksender.data.manga.MangaAuthState
import com.cybercat.pocketbooksender.data.manga.MangaChapter
import com.cybercat.pocketbooksender.data.manga.MangaChapterDownload
import com.cybercat.pocketbooksender.data.manga.MangaDownloadedChapter
import com.cybercat.pocketbooksender.data.manga.MangaDownloadProgress
import com.cybercat.pocketbooksender.data.manga.MangaRepository
import com.cybercat.pocketbooksender.data.manga.MangaSeriesBookmark
import com.cybercat.pocketbooksender.data.manga.MangaSeriesDetails
import com.cybercat.pocketbooksender.data.manga.MangaChapterDownloadTarget
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.DeviceCatalog
import com.cybercat.pocketbooksender.model.UploadItem
import com.cybercat.pocketbooksender.transfer.UploadQueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MangaViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val catalogRepository: DeviceCatalogRepository,
    private val queueManager: UploadQueueManager,
    private val localizationManager: com.cybercat.pocketbooksender.localization.LocalizationManager,
) : ViewModel() {

    private val _mangaState = MutableStateFlow(MangaUiState())

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
        if (_mangaState.value.selectedSourceId.isBlank() && mangaRepository.sources.isNotEmpty()) {
            selectMangaSource(mangaRepository.sources.first().id)
        }
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
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
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
        _mangaState.update { state ->
            val selectedIds = state.chapters
                .filter { chapter -> chapter.stableKey !in state.downloadedStableKeys }
                .mapTo(mutableSetOf()) { chapter -> chapter.chapterId }
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
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
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
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
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

                val allNewChapterIds = updatesWithNews.flatMap { update ->
                    update.newChapters.map { it.chapterId }
                }.toSet()

                _mangaState.update { state ->
                    state.copy(
                        isCheckingSubscriptions = false,
                        subscriptionUpdates = updatesWithNews,
                        subscriptionUpdatesVisible = true,
                        selectedSubscriptionUpdateChapterIds = allNewChapterIds,
                        statusMessage = null,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
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

    fun toggleSubscriptionUpdateChapter(chapterId: String, selected: Boolean) {
        _mangaState.update { state ->
            val selectedIds = if (selected) {
                state.selectedSubscriptionUpdateChapterIds + chapterId
            } else {
                state.selectedSubscriptionUpdateChapterIds - chapterId
            }
            state.copy(selectedSubscriptionUpdateChapterIds = selectedIds)
        }
    }

    fun selectAllSubscriptionUpdateChapters() {
        _mangaState.update { state ->
            val allIds = state.subscriptionUpdates.flatMap { update ->
                update.newChapters.map { it.chapterId }
            }.toSet()
            state.copy(selectedSubscriptionUpdateChapterIds = allIds)
        }
    }

    fun clearSubscriptionUpdateChapters() {
        _mangaState.update { state ->
            state.copy(selectedSubscriptionUpdateChapterIds = emptySet())
        }
    }

    fun closeSubscriptionUpdates() {
        _mangaState.update { state ->
            state.copy(
                subscriptionUpdatesVisible = false,
                subscriptionUpdates = emptyList(),
                selectedSubscriptionUpdateChapterIds = emptySet(),
            )
        }
    }

    fun downloadSubscriptionUpdates() {
        val snapshot = _mangaState.value
        val selectedIds = snapshot.selectedSubscriptionUpdateChapterIds
        val targets = snapshot.subscriptionUpdates.flatMap { update ->
            update.newChapters
                .filter { it.chapterId in selectedIds }
                .map { chapter -> MangaChapterDownloadTarget(update.page.details, chapter) }
        }

        if (targets.isEmpty()) {
            _mangaState.update { it.copy(errorMessage = localizationManager.currentStrings.value.mangaErrorSelectChaptersFirst) }
            return
        }

        val strings = localizationManager.currentStrings.value
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

        viewModelScope.launch {
            runCatching {
                mangaRepository.downloadMultipleSeriesChapters(
                    sourceId = snapshot.selectedSourceId,
                    targets = targets,
                    onProgress = { progress ->
                        _mangaState.update { state ->
                            state.copy(
                                downloadProgress = progress.toUiProgress(
                                    previousProgress = state.downloadProgress?.progress,
                                ),
                            )
                        }
                    },
                )
            }.onSuccess { result ->
                if (result.downloaded.isNotEmpty()) {
                    addDownloadedMangaFiles(result.downloaded)
                }

                _mangaState.update { state ->
                    state.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        subscriptionUpdates = emptyList(),
                        selectedSubscriptionUpdateChapterIds = emptySet(),
                        errorMessage = formatMangaFailures(result.failedMessages),
                    )
                }

                if (result.downloaded.isNotEmpty()) {
                    showMangaStatus(localizationManager.currentStrings.value.get("manga_status_added_to_queue", result.downloaded.size))
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                _mangaState.update { state ->
                    state.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        errorMessage = error.message ?: localizationManager.currentStrings.value.mangaErrorCannotDownload,
                    )
                }
            }
        }
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

        viewModelScope.launch {
            runCatching {
                mangaRepository.downloadChapters(
                    sourceId = snapshot.selectedSourceId,
                    series = series,
                    chapters = selectedChapters,
                    onProgress = { progress ->
                        _mangaState.update { state ->
                            state.copy(
                                downloadProgress = progress.toUiProgress(
                                    previousProgress = state.downloadProgress?.progress,
                                ),
                            )
                        }
                    },
                )
            }.onSuccess { result ->
                if (result.downloaded.isNotEmpty()) {
                    addDownloadedMangaFiles(result.downloaded)
                }

                _mangaState.update { state ->
                    val downloadedIds = result.downloaded.mapTo(mutableSetOf()) { downloaded ->
                        downloaded.chapter.chapterId
                    }
                    state.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        selectedChapterIds = state.selectedChapterIds - downloadedIds,
                        errorMessage = formatMangaFailures(result.failedMessages),
                    )
                }

                if (result.downloaded.isNotEmpty()) {
                    showMangaStatus(localizationManager.currentStrings.value.get("manga_status_added_to_queue", result.downloaded.size))
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                _mangaState.update { state ->
                    state.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        errorMessage = error.message ?: localizationManager.currentStrings.value.mangaErrorCannotDownload,
                    )
                }
            }
        }
    }

    private fun addDownloadedMangaFiles(
        downloaded: List<MangaDownloadedChapter>,
    ) {
        val items = downloaded.map { item ->
            val uri = Uri.fromFile(item.file)
            val ext = item.file.extension.lowercase().ifBlank { "cbz" }
            UploadItem(
                id = UUID.randomUUID().toString(),
                sourceUri = uri.toString(),
                originalName = item.file.name,
                extension = ext,
                category = BookCategory.Manga,
                title = item.chapter.title,
                mangaSeries = item.series.title,
                mangaVolume = item.chapter.title,
                plannedPath = "",
            )
        }

        queueManager.addPreparedItems(items)
    }

    private fun refreshMangaAuthState(closeBrowserOnAuthenticated: Boolean = false) {
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
        showTemporaryStatus(
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
            },
            message = message,
        )
    }

    private fun showTemporaryStatus(
        delayMillis: Long,
        setMessage: (String) -> Unit,
        clearIfStillCurrent: (String) -> Unit,
        message: String,
    ) {
        setMessage(message)
        viewModelScope.launch {
            delay(delayMillis)
            clearIfStillCurrent(message)
        }
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
                strings.get("manga_progress_detail_generic", chapterProgressText, step)
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
        val visible = failedMessages.take(3).joinToString("\n")
        val hiddenCount = failedMessages.size - 3
        return if (hiddenCount > 0) {
            localizationManager.currentStrings.value.get("manga_error_failures_summary", visible, hiddenCount)
        } else {
            visible
        }
    }

    private fun Long.formatBytes(): String {
        val units = listOf("B", "KB", "MB", "GB")
        var value = toDouble()
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex += 1
        }
        return if (unitIndex == 0) {
            "${value.toLong()} ${units[unitIndex]}"
        } else {
            "%.1f %s".format(value, units[unitIndex])
        }
    }

    private fun String.catalogMatchKey(): String =
        lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")

    private companion object {
        const val StatusMessageMillis = 5000L
    }
}

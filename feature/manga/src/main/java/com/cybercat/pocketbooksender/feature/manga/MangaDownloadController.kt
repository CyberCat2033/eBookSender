package com.cybercat.pocketbooksender.feature.manga

import com.cybercat.pocketbooksender.data.manga.MangaChapterDownloadTarget
import com.cybercat.pocketbooksender.data.manga.MangaDownloadCoordinator
import com.cybercat.pocketbooksender.data.manga.MangaDownloadEvent
import com.cybercat.pocketbooksender.data.manga.MangaDownloadLauncher
import com.cybercat.pocketbooksender.data.manga.MangaDownloadRequestKind
import com.cybercat.pocketbooksender.data.manga.MangaRepository
import com.cybercat.pocketbooksender.data.manga.MangaSubscriptionCheckResult
import com.cybercat.pocketbooksender.localization.LocalizationManager
import com.cybercat.pocketbooksender.util.onFailureRethrowing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class MangaDownloadController(
    private val mangaRepository: MangaRepository,
    private val downloadCoordinator: MangaDownloadCoordinator,
    private val downloadLauncher: MangaDownloadLauncher,
    private val localizationManager: LocalizationManager,
    private val mangaState: MutableStateFlow<MangaUiState>,
    private val scope: CoroutineScope,
    private val showStatus: (String) -> Unit
) {
    private var activeDownload: ActiveMangaDownload? = null

    fun checkMangaSubscriptions() {
        if (mangaState.value.isCheckingSubscriptions) return

        mangaState.update { state ->
            state.copy(
                isCheckingSubscriptions = true,
                statusMessage = localizationManager.currentStrings.value
                    .mangaStatusCheckingSubscriptions,
                errorMessage = null
            )
        }

        scope.launch {
            runCatching {
                mangaRepository.checkSubscriptions()
            }.onSuccess { results ->
                val updatesWithNews = results.filter { it.newChapters.isNotEmpty() }
                if (updatesWithNews.isEmpty()) {
                    mangaState.update { state ->
                        state.copy(
                            isCheckingSubscriptions = false,
                            statusMessage = null,
                            errorMessage = null
                        )
                    }
                    showStatus(
                        localizationManager.currentStrings.value.mangaStatusNoNewChapters
                    )
                    return@onSuccess
                }

                val allNewChapterKeys = MangaSubscriptionUpdateReducer.allChapterKeys(
                    updatesWithNews
                )

                mangaState.update { state ->
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
                mangaState.update { state ->
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
        mangaState.update { state ->
            val selectedKeys = if (selected) {
                state.selectedSubscriptionUpdateChapterKeys + chapterKey
            } else {
                state.selectedSubscriptionUpdateChapterKeys - chapterKey
            }
            state.copy(selectedSubscriptionUpdateChapterKeys = selectedKeys)
        }
    }

    fun selectAllSubscriptionUpdateChapters() {
        mangaState.update { state ->
            val allKeys = MangaSubscriptionUpdateReducer.allChapterKeys(state.subscriptionUpdates)
            state.copy(selectedSubscriptionUpdateChapterKeys = allKeys)
        }
    }

    fun clearSubscriptionUpdateChapters() {
        mangaState.update { state ->
            state.copy(selectedSubscriptionUpdateChapterKeys = emptySet())
        }
    }

    fun closeSubscriptionUpdates() {
        mangaState.update { state ->
            state.copy(
                subscriptionUpdatesVisible = false,
                subscriptionUpdates = emptyList(),
                selectedSubscriptionUpdateChapterKeys = emptySet()
            )
        }
    }

    fun downloadSubscriptionUpdates() {
        val snapshot = mangaState.value
        val selectedKeys = snapshot.selectedSubscriptionUpdateChapterKeys
        val targets = MangaSubscriptionUpdateReducer.selectedTargets(
            updates = snapshot.subscriptionUpdates,
            selectedKeys = selectedKeys
        )

        if (targets.isEmpty()) {
            mangaState.update {
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
        mangaState.update { state ->
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
        val snapshot = mangaState.value
        val series = snapshot.selectedSeries
        if (series == null) {
            mangaState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value
                        .mangaErrorOpenSeriesFirst
                )
            }
            return
        }

        val selectedChapters = snapshot.selectedChapters
        if (selectedChapters.isEmpty()) {
            mangaState.update {
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
        mangaState.update { state ->
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

    fun cancelActiveDownload() {
        val active = activeDownload ?: return
        downloadLauncher.cancelMangaDownload(active.requestId)
    }

    fun handleMangaDownloadEvent(event: MangaDownloadEvent) {
        when (event) {
            is MangaDownloadEvent.Started -> Unit

            is MangaDownloadEvent.Progress -> {
                if (activeDownload?.requestId != event.requestId) return
                mangaState.update { state ->
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
                    showStatus(
                        localizationManager.currentStrings.value.get(
                            "manga_status_added_to_queue",
                            event.addedToQueueCount
                        )
                    )
                }
            }

            is MangaDownloadEvent.Canceled -> {
                val active = activeDownload?.takeIf { it.requestId == event.requestId } ?: return
                activeDownload = null
                when (active.kind) {
                    MangaDownloadRequestKind.SelectedChapters -> cancelSelectedChapterDownload(
                        event
                    )

                    MangaDownloadRequestKind.SubscriptionUpdates ->
                        cancelSubscriptionUpdateDownload(active, event)
                }
                showStatus(mangaDownloadCanceledMessage(event.addedToQueueCount))
            }

            is MangaDownloadEvent.Failed -> {
                if (activeDownload?.requestId != event.requestId) return
                val active = activeDownload
                activeDownload = null
                mangaState.update { state ->
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

    private fun cancelSelectedChapterDownload(event: MangaDownloadEvent.Canceled) {
        mangaState.update { state ->
            state.copy(
                isDownloading = false,
                downloadProgress = null,
                selectedChapterIds = state.selectedChapterIds - event.downloadedChapterIds,
                errorMessage = null
            )
        }
    }

    private fun completeSelectedChapterDownload(event: MangaDownloadEvent.Completed) {
        mangaState.update { state ->
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

    private fun cancelSubscriptionUpdateDownload(
        active: ActiveMangaDownload,
        event: MangaDownloadEvent.Canceled
    ) {
        val remainingUpdates = MangaSubscriptionUpdateReducer.remainingAfterDownload(
            updates = active.subscriptionUpdates,
            selectedKeys = active.selectedSubscriptionKeys,
            downloadedKeys = event.downloadedSubscriptionKeys
        )
        val remainingKeys = MangaSubscriptionUpdateReducer.allChapterKeys(remainingUpdates)

        mangaState.update { state ->
            state.copy(
                isDownloading = false,
                downloadProgress = null,
                subscriptionUpdates = remainingUpdates,
                subscriptionUpdatesVisible = remainingUpdates.isNotEmpty(),
                selectedSubscriptionUpdateChapterKeys = remainingKeys,
                errorMessage = null
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

        mangaState.update { state ->
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

    private fun mangaDownloadCanceledMessage(addedToQueueCount: Int): String {
        val strings = localizationManager.currentStrings.value
        return if (addedToQueueCount > 0) {
            strings.get("manga_status_download_canceled_with_files", addedToQueueCount)
        } else {
            strings.get("manga_status_download_canceled")
        }
    }
}

private data class ActiveMangaDownload(
    val requestId: String,
    val kind: MangaDownloadRequestKind,
    val selectedSubscriptionKeys: Set<String>,
    val subscriptionUpdates: List<MangaSubscriptionCheckResult>
)

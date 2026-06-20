package com.cybercat.ebooksender.feature.opds

import android.net.Uri
import android.util.Log
import com.cybercat.ebooksender.data.opds.DownloadOpdsEntriesUseCase
import com.cybercat.ebooksender.data.opds.MatchOpdsAuthSourceUseCase
import com.cybercat.ebooksender.data.opds.OpdsAcquisition
import com.cybercat.ebooksender.data.opds.OpdsAuthenticationRequiredException
import com.cybercat.ebooksender.data.opds.OpdsDownloadProgress
import com.cybercat.ebooksender.data.opds.OpdsEntry
import com.cybercat.ebooksender.data.opds.OpdsRepository
import com.cybercat.ebooksender.data.opds.OpdsSource
import com.cybercat.ebooksender.data.transfer.UploadQueueManager
import com.cybercat.ebooksender.localization.LocalizationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "OpdsDownloadController"

internal class OpdsDownloadController(
    private val opdsRepository: OpdsRepository,
    private val downloadOpdsEntriesUseCase: DownloadOpdsEntriesUseCase,
    private val matchOpdsAuthSource: MatchOpdsAuthSourceUseCase,
    private val queueManager: UploadQueueManager,
    private val localizationManager: LocalizationManager,
    private val opdsState: MutableStateFlow<OpdsUiState>,
    private val scope: CoroutineScope,
    private val showStatus: (String) -> Unit,
    private val showError: (String) -> Unit,
    private val openCredentialsDialog: (OpdsSource, String?) -> Unit
) {
    private var downloadJob: Job? = null

    fun downloadOpdsAcquisition(entry: OpdsEntry, acquisition: OpdsAcquisition) {
        if (downloadJob?.isActive == true) return

        val baseUrl = opdsState.value.currentUrl
        if (baseUrl == null) {
            opdsState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value
                        .opdsErrorOpenCatalogFirst,
                    statusMessage = null
                )
            }
            return
        }

        downloadJob = scope.launch {
            opdsState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = OpdsDownloadUiProgress(
                        completedCount = 0,
                        totalCount = 1,
                        currentItemTitle = entry.title.ifBlank { acquisition.title.orEmpty() },
                        currentItemAuthors = entry.authors
                    ),
                    errorMessage = null,
                    statusMessage = null
                )
            }

            try {
                val file = opdsRepository.downloadPublication(
                    baseUrl = baseUrl,
                    entry = entry,
                    acquisition = acquisition,
                    onProgress = ::onDownloadProgress
                )
                queueManager.addUris(listOf(Uri.fromFile(file)))
                opdsState.update { state ->
                    state.copy(
                        isDownloading = false,
                        downloadProgress = null
                    )
                }
                showStatus(
                    localizationManager.currentStrings.value.get(
                        "opds_status_added_to_queue",
                        file.name
                    )
                )
            } catch (error: CancellationException) {
                opdsState.update { state ->
                    state.copy(
                        isDownloading = false,
                        downloadProgress = null
                    )
                }
                showStatus(
                    localizationManager.currentStrings.value.get("opds_status_download_canceled")
                )
            } catch (error: Throwable) {
                if (error is OpdsAuthenticationRequiredException) {
                    Log.w(TAG, "Authentication required for OPDS download", error)
                    val matchingSource = matchOpdsAuthSource(error)
                    if (matchingSource != null) {
                        opdsState.update { state ->
                            state.copy(
                                isDownloading = false,
                                downloadProgress = null
                            )
                        }
                        openCredentialsDialog(matchingSource, null)
                        return@launch
                    }
                } else {
                    Log.e(TAG, "Failed to download publication", error)
                }
                opdsState.update { state ->
                    state.copy(
                        isDownloading = false,
                        downloadProgress = null
                    )
                }
                showError(
                    error.message
                        ?: localizationManager.currentStrings.value.opdsErrorCannotDownload
                )
            } finally {
                downloadJob = null
            }
        }
    }

    fun downloadOpdsEntries(entries: List<OpdsEntry>) {
        if (downloadJob?.isActive == true) return

        val baseUrl = opdsState.value.currentUrl
        if (baseUrl == null) {
            opdsState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value
                        .opdsErrorOpenCatalogFirst,
                    statusMessage = null
                )
            }
            return
        }

        val downloadableCount = downloadOpdsEntriesUseCase.downloadableEntryCount(entries)
        if (downloadableCount == 0) {
            opdsState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value
                        .opdsErrorNoDownloadableEntries,
                    statusMessage = null
                )
            }
            return
        }

        var addedToQueueCount = 0
        downloadJob = scope.launch {
            opdsState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = OpdsDownloadUiProgress(
                        completedCount = 0,
                        totalCount = downloadableCount
                    ),
                    errorMessage = null,
                    statusMessage = null
                )
            }

            try {
                val result = downloadOpdsEntriesUseCase(
                    baseUrl = baseUrl,
                    entries = entries,
                    onFileDownloaded = { file ->
                        withContext(NonCancellable + Dispatchers.Main.immediate) {
                            queueManager.addUris(listOf(Uri.fromFile(file)))
                            addedToQueueCount += 1
                            opdsState.update { state ->
                                val currentProgress = state.downloadProgress
                                state.copy(
                                    downloadProgress = currentProgress?.copy(
                                        completedCount = addedToQueueCount,
                                        totalCount = downloadableCount,
                                        bytesRead = 0L,
                                        totalBytes = null
                                    )
                                )
                            }
                        }
                    }
                ).getOrThrow()

                opdsState.update { state ->
                    state.copy(
                        isDownloading = false,
                        downloadProgress = null,
                        errorMessage = if (result.failedCount > 0) {
                            localizationManager.currentStrings.value.get(
                                "opds_error_failed_to_download_entries",
                                result.failedCount
                            )
                        } else {
                            null
                        }
                    )
                }

                if (result.downloadedFiles.isNotEmpty()) {
                    showStatus(
                        localizationManager.currentStrings.value.get(
                            "opds_status_added_to_queue_multiple",
                            result.downloadedFiles.size
                        )
                    )
                }
            } catch (error: CancellationException) {
                opdsState.update { state ->
                    state.copy(
                        isDownloading = false,
                        downloadProgress = null
                    )
                }
                showStatus(downloadCanceledMessage(addedToQueueCount))
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to download OPDS entries", error)
                opdsState.update { state ->
                    state.copy(
                        isDownloading = false,
                        downloadProgress = null
                    )
                }
                showError(
                    error.message
                        ?: localizationManager.currentStrings.value.opdsErrorCannotDownload
                )
            } finally {
                downloadJob = null
            }
        }
    }

    fun cancelOpdsDownload() {
        val job = downloadJob?.takeIf { it.isActive } ?: return
        opdsState.update { state ->
            state.copy(
                statusMessage = null,
                downloadProgress = state.downloadProgress?.copy(isCanceling = true)
            )
        }
        job.cancel()
    }

    private fun onDownloadProgress(progress: OpdsDownloadProgress) {
        opdsState.update { state ->
            val currentProgress = state.downloadProgress ?: return@update state
            state.copy(
                downloadProgress = currentProgress.copy(
                    bytesRead = progress.bytesRead,
                    totalBytes = progress.totalBytes,
                    currentItemTitle = progress.currentItemTitle
                        ?: currentProgress.currentItemTitle,
                    currentItemAuthors = progress.currentItemAuthors.ifEmpty {
                        currentProgress.currentItemAuthors
                    }
                )
            )
        }
    }

    private fun downloadCanceledMessage(addedToQueueCount: Int): String {
        val strings = localizationManager.currentStrings.value
        return if (addedToQueueCount > 0) {
            strings.get("opds_status_download_canceled_with_files", addedToQueueCount)
        } else {
            strings.get("opds_status_download_canceled")
        }
    }
}

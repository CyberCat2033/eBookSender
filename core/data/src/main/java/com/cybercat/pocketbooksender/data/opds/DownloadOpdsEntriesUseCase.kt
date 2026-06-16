package com.cybercat.pocketbooksender.data.opds

import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class DownloadOpdsEntriesUseCase @Inject constructor(
    private val opdsRepository: OpdsRepository,
) {
    fun hasDownloadableEntries(entries: List<OpdsEntry>): Boolean =
        entries.any { entry -> entry.bestAcquisition() != null }

    suspend operator fun invoke(
        baseUrl: String,
        entries: List<OpdsEntry>,
    ): Result<DownloadOpdsEntriesResult> {
        return try {
            val downloadRequests = entries.mapNotNull { entry ->
                entry.bestAcquisition()?.let { acquisition ->
                    OpdsDownloadRequest(entry = entry, acquisition = acquisition)
                }
            }

            val outcomes = coroutineScope {
                downloadRequests.map { request ->
                    async { download(baseUrl, request) }
                }.awaitAll()
            }

            Result.success(
                DownloadOpdsEntriesResult(
                    downloadedFiles = outcomes.mapNotNull { outcome -> outcome.file },
                    failedCount = outcomes.count { outcome -> outcome.failed },
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private suspend fun download(
        baseUrl: String,
        request: OpdsDownloadRequest,
    ): OpdsDownloadOutcome {
        return try {
            OpdsDownloadOutcome(
                file = opdsRepository.downloadPublication(
                    baseUrl = baseUrl,
                    entry = request.entry,
                    acquisition = request.acquisition,
                ),
                failed = false,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            OpdsDownloadOutcome(file = null, failed = true)
        }
    }

    private fun OpdsEntry.bestAcquisition(): OpdsAcquisition? =
        acquisitions.mapNotNull { acquisition ->
            acquisition.supportedDownloadFormat()?.let { format -> acquisition to format.priority }
        }.minByOrNull { (_, priority) ->
            priority
        }?.first
}

data class DownloadOpdsEntriesResult(
    val downloadedFiles: List<File>,
    val failedCount: Int,
)

private data class OpdsDownloadRequest(
    val entry: OpdsEntry,
    val acquisition: OpdsAcquisition,
)

private data class OpdsDownloadOutcome(
    val file: File?,
    val failed: Boolean,
)

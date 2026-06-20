package com.cybercat.ebooksender.data.manga

import com.cybercat.ebooksender.data.request.RequestCoordinator
import com.cybercat.ebooksender.data.request.RequestSubmitPolicy
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaDownloadCoordinator @Inject constructor() {
    private val requestCoordinator = RequestCoordinator<MangaDownloadRequest, MangaDownloadEvent>(
        requestId = MangaDownloadRequest::id,
        submitPolicy = RequestSubmitPolicy.ReplacePending
    )
    val events = requestCoordinator.events

    fun submit(targets: List<MangaChapterDownloadTarget>, kind: MangaDownloadRequestKind): String {
        require(targets.isNotEmpty()) { "No manga chapters selected" }
        val id = UUID.randomUUID().toString()
        requestCoordinator.submit(
            MangaDownloadRequest(
                id = id,
                kind = kind,
                targets = targets
            )
        )
        return id
    }

    fun takeRequest(id: String?): MangaDownloadRequest? = requestCoordinator.takeRequest(id)

    fun emit(event: MangaDownloadEvent) = requestCoordinator.emit(event)
}

interface MangaDownloadLauncher {
    fun startMangaDownload(requestId: String)
    fun cancelMangaDownload(requestId: String)
}

data class MangaDownloadRequest(
    val id: String,
    val kind: MangaDownloadRequestKind,
    val targets: List<MangaChapterDownloadTarget>
)

enum class MangaDownloadRequestKind {
    SelectedChapters,
    SubscriptionUpdates
}

sealed interface MangaDownloadEvent {
    data class Started(
        val requestId: String,
        val kind: MangaDownloadRequestKind,
        val totalChapters: Int
    ) : MangaDownloadEvent

    data class Progress(val requestId: String, val progress: MangaDownloadProgress) :
        MangaDownloadEvent

    data class Completed(
        val requestId: String,
        val kind: MangaDownloadRequestKind,
        val downloadedChapterIds: Set<String>,
        val downloadedSubscriptionKeys: Set<String>,
        val addedToQueueCount: Int,
        val failedMessages: List<String>
    ) : MangaDownloadEvent

    data class Canceled(
        val requestId: String,
        val kind: MangaDownloadRequestKind,
        val downloadedChapterIds: Set<String>,
        val downloadedSubscriptionKeys: Set<String>,
        val addedToQueueCount: Int
    ) : MangaDownloadEvent

    data class Failed(
        val requestId: String,
        val kind: MangaDownloadRequestKind,
        val message: String
    ) : MangaDownloadEvent
}

package com.cybercat.pocketbooksender.data.manga

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class MangaDownloadCoordinator @Inject constructor() {
    private val pendingRequest = AtomicReference<MangaDownloadRequest?>(null)

    private val _events = MutableSharedFlow<MangaDownloadEvent>(
        extraBufferCapacity = 64
    )
    val events = _events.asSharedFlow()

    fun submit(targets: List<MangaChapterDownloadTarget>, kind: MangaDownloadRequestKind): String {
        require(targets.isNotEmpty()) { "No manga chapters selected" }
        val id = UUID.randomUUID().toString()
        pendingRequest.set(
            MangaDownloadRequest(
                id = id,
                kind = kind,
                targets = targets
            )
        )
        return id
    }

    fun takeRequest(id: String?): MangaDownloadRequest? {
        val current = pendingRequest.get() ?: return null
        if (current.id != id) return null
        return if (pendingRequest.compareAndSet(current, null)) current else null
    }

    fun emit(event: MangaDownloadEvent) {
        _events.tryEmit(event)
    }
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

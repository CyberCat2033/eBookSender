package com.cybercat.pocketbooksender.transfer

import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.RemoteDevice
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@Singleton
class TransferCoordinator @Inject constructor() {
    private val lock = Any()
    private var pendingRequest: TransferRequest? = null
    private var activeRequestId: String? = null

    private val _events = Channel<TransferEvent>(capacity = Channel.UNLIMITED)
    val events = _events.receiveAsFlow()

    fun submit(device: RemoteDevice, items: List<TransferUploadItem>): TransferSubmitResult {
        require(items.isNotEmpty()) { "No transfer items selected" }
        val id = UUID.randomUUID().toString()
        synchronized(lock) {
            if (pendingRequest != null || activeRequestId != null) {
                return TransferSubmitResult.RejectedAlreadyRunning
            }
            pendingRequest = TransferRequest(
                id = id,
                device = device,
                items = items
            )
        }
        return TransferSubmitResult.Accepted(id)
    }

    fun takeRequest(id: String?): TransferRequest? = synchronized(lock) {
        val current = pendingRequest ?: return null
        if (current.id != id) return null
        pendingRequest = null
        activeRequestId = current.id
        current
    }

    fun cancelPendingRequest(id: String?): TransferRequest? = synchronized(lock) {
        val current = pendingRequest ?: return null
        if (current.id != id) return null
        pendingRequest = null
        current
    }

    fun finishActiveRequest(id: String) {
        synchronized(lock) {
            if (activeRequestId == id) {
                activeRequestId = null
            }
        }
    }

    fun emit(event: TransferEvent) {
        check(_events.trySend(event).isSuccess) { "Transfer event channel is unavailable" }
    }
}

sealed interface TransferSubmitResult {
    data class Accepted(val requestId: String) : TransferSubmitResult

    data object RejectedAlreadyRunning : TransferSubmitResult
}

data class TransferRequest(
    val id: String,
    val device: RemoteDevice,
    val items: List<TransferUploadItem>
)

data class TransferUploadItem(
    val id: String,
    val sourceUri: String,
    val originalName: String,
    val extension: String,
    val category: BookCategory,
    val title: String,
    val mangaSeries: String?,
    val mangaVolume: String?,
    val seriesIndex: String?,
    val plannedPath: String
)

sealed interface TransferEvent {
    data class ItemStarted(val itemId: String, val completed: Int, val total: Int) : TransferEvent

    data class ItemProgress(val itemId: String, val progress: Float) : TransferEvent

    data class ItemUploaded(val itemId: String, val completed: Int, val total: Int) : TransferEvent

    data class ItemFailed(
        val itemId: String,
        val message: String,
        val failureReason: TransferFailureReason? = null
    ) : TransferEvent

    data class Completed(val uploaded: Int, val failed: Int) : TransferEvent

    data class Canceled(val uploaded: Int, val failed: Int) : TransferEvent
}

enum class TransferFailureReason {
    LocalNetworkBypassBlocked
}

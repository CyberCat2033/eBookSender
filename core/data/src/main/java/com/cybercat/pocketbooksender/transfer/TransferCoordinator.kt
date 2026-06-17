package com.cybercat.pocketbooksender.transfer

import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.RemoteDevice
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class TransferCoordinator @Inject constructor() {
    private val pendingRequest = AtomicReference<TransferRequest?>(null)

    private val _events = MutableSharedFlow<TransferEvent>(
        extraBufferCapacity = 64
    )
    val events = _events.asSharedFlow()

    fun submit(device: RemoteDevice, items: List<TransferUploadItem>): String {
        val id = UUID.randomUUID().toString()
        pendingRequest.set(
            TransferRequest(
                id = id,
                device = device,
                items = items
            )
        )
        return id
    }

    fun takeRequest(id: String?): TransferRequest? {
        val current = pendingRequest.get() ?: return null
        if (current.id != id) return null
        return if (pendingRequest.compareAndSet(current, null)) current else null
    }

    fun cancelPendingRequest(id: String?): TransferRequest? {
        val current = pendingRequest.get() ?: return null
        if (current.id != id) return null
        return if (pendingRequest.compareAndSet(current, null)) current else null
    }

    fun emit(event: TransferEvent) {
        _events.tryEmit(event)
    }
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

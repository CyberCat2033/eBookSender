package com.cybercat.pocketbooksender.transfer

import com.cybercat.pocketbooksender.model.PocketBookDevice
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object TransferCoordinator {
    private var request: TransferRequest? = null

    private val _events = MutableSharedFlow<TransferEvent>(
        extraBufferCapacity = 64,
    )
    val events = _events.asSharedFlow()

    fun submit(
        device: PocketBookDevice,
        items: List<TransferUploadItem>,
    ): String {
        val id = UUID.randomUUID().toString()
        request = TransferRequest(
            id = id,
            device = device,
            items = items,
        )
        return id
    }

    fun takeRequest(id: String?): TransferRequest? {
        val current = request ?: return null
        if (current.id != id) return null
        request = null
        return current
    }

    fun emit(event: TransferEvent) {
        _events.tryEmit(event)
    }
}

data class TransferRequest(
    val id: String,
    val device: PocketBookDevice,
    val items: List<TransferUploadItem>,
)

data class TransferUploadItem(
    val id: String,
    val sourceUri: String,
    val originalName: String,
    val extension: String,
    val plannedPath: String,
)

sealed interface TransferEvent {
    data class ItemStarted(
        val itemId: String,
        val completed: Int,
        val total: Int,
    ) : TransferEvent

    data class ItemProgress(
        val itemId: String,
        val progress: Float,
    ) : TransferEvent

    data class ItemUploaded(
        val itemId: String,
        val completed: Int,
        val total: Int,
    ) : TransferEvent

    data class ItemFailed(
        val itemId: String,
        val message: String,
    ) : TransferEvent

    data class Completed(
        val uploaded: Int,
        val failed: Int,
    ) : TransferEvent
}

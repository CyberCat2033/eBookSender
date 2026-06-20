package com.cybercat.ebooksender.transfer

import com.cybercat.ebooksender.data.request.RequestCoordinator
import com.cybercat.ebooksender.data.request.RequestSubmitPolicy
import com.cybercat.ebooksender.model.BookCategory
import com.cybercat.ebooksender.model.RemoteDevice
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferCoordinator @Inject constructor() {
    private val requestCoordinator = RequestCoordinator<TransferRequest, TransferEvent>(
        requestId = TransferRequest::id,
        submitPolicy = RequestSubmitPolicy.RejectWhenPendingOrActive
    )
    val events = requestCoordinator.events

    fun submit(device: RemoteDevice, items: List<TransferUploadItem>): TransferSubmitResult {
        require(items.isNotEmpty()) { "No transfer items selected" }
        val id = UUID.randomUUID().toString()
        val accepted = requestCoordinator.submit(
            TransferRequest(
                id = id,
                device = device,
                items = items
            )
        )
        if (!accepted) return TransferSubmitResult.RejectedAlreadyRunning
        return TransferSubmitResult.Accepted(id)
    }

    fun takeRequest(id: String?): TransferRequest? = requestCoordinator.takeRequest(id)

    fun cancelPendingRequest(id: String?): TransferRequest? =
        requestCoordinator.cancelPendingRequest(id)

    fun finishActiveRequest(id: String) = requestCoordinator.finishActiveRequest(id)

    fun hasActiveTransfer(): Boolean = requestCoordinator.hasPendingOrActiveRequest()

    fun emit(event: TransferEvent) = requestCoordinator.emit(event)
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

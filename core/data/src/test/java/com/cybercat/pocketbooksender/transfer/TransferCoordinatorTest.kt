package com.cybercat.pocketbooksender.transfer

import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.RemoteDevice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferCoordinatorTest {
    @Test
    fun submitRejectsNewRequestsWhileAnotherTransferIsPendingOrActive() {
        val coordinator = TransferCoordinator()

        val firstSubmit = coordinator.submit(sampleDevice(), listOf(sampleItem("first")))
        assertTrue(firstSubmit is TransferSubmitResult.Accepted)

        val firstRequestId = (firstSubmit as TransferSubmitResult.Accepted).requestId
        assertEquals(
            TransferSubmitResult.RejectedAlreadyRunning,
            coordinator.submit(sampleDevice(), listOf(sampleItem("second")))
        )

        assertEquals(firstRequestId, coordinator.takeRequest(firstRequestId)?.id)
        assertEquals(
            TransferSubmitResult.RejectedAlreadyRunning,
            coordinator.submit(sampleDevice(), listOf(sampleItem("third")))
        )

        coordinator.finishActiveRequest(firstRequestId)

        assertTrue(
            coordinator.submit(sampleDevice(), listOf(sampleItem("fourth"))) is
                TransferSubmitResult.Accepted
        )
    }

    @Test
    fun cancelPendingRequestRemovesOnlyTheMatchingRequest() {
        val coordinator = TransferCoordinator()

        val submitResult = coordinator.submit(sampleDevice(), listOf(sampleItem("first")))
        val requestId = (submitResult as TransferSubmitResult.Accepted).requestId

        assertEquals(requestId, coordinator.cancelPendingRequest(requestId)?.id)
        assertTrue(coordinator.takeRequest(requestId) == null)
        assertTrue(
            coordinator.submit(sampleDevice(), listOf(sampleItem("second"))) is
                TransferSubmitResult.Accepted
        )
    }

    @Test
    fun emittedEventsAreRetainedUntilTheCollectorStarts() = runBlocking {
        val coordinator = TransferCoordinator()
        val expected = TransferEvent.Completed(uploaded = 2, failed = 1)

        coordinator.emit(expected)

        assertEquals(expected, coordinator.events.first())
    }

    private fun sampleDevice() = RemoteDevice(host = "192.168.0.2")

    private fun sampleItem(id: String) = TransferUploadItem(
        id = id,
        sourceUri = "content://items/$id",
        originalName = "$id.epub",
        extension = "epub",
        category = BookCategory.Books,
        title = id,
        mangaSeries = null,
        mangaVolume = null,
        seriesIndex = null,
        plannedPath = "Books/$id.epub"
    )
}

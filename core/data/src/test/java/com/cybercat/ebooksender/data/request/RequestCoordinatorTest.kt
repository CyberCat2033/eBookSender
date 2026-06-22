package com.cybercat.ebooksender.data.request

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestCoordinatorTest {

    private data class TestRequest(val id: String, val payload: String)
    private data class TestEvent(val message: String)

    @Test
    fun testRejectWhenPendingOrActivePolicy() = runBlocking {
        val coordinator = RequestCoordinator<TestRequest, TestEvent>(
            requestId = { it.id },
            submitPolicy = RequestSubmitPolicy.RejectWhenPendingOrActive
        )

        assertFalse(coordinator.hasPendingOrActiveRequest())

        // Submit first request - should succeed
        val req1 = TestRequest("req-1", "payload-1")
        assertTrue(coordinator.submit(req1))
        assertTrue(coordinator.hasPendingOrActiveRequest())

        // Submit second request while req1 is pending - should be rejected
        val req2 = TestRequest("req-2", "payload-2")
        assertFalse(coordinator.submit(req2))

        // Take request with incorrect ID - should return null
        assertNull(coordinator.takeRequest("incorrect-id"))
        assertTrue(coordinator.hasPendingOrActiveRequest())

        // Take request with correct ID - should return req1
        assertEquals(req1, coordinator.takeRequest("req-1"))
        assertTrue(coordinator.hasPendingOrActiveRequest()) // because it's active now

        // Submit req2 while req1 is active - should be rejected
        assertFalse(coordinator.submit(req2))

        // Finish active request with wrong ID - should not clear active
        coordinator.finishActiveRequest("wrong-id")
        assertTrue(coordinator.hasPendingOrActiveRequest())

        // Finish active request with correct ID
        coordinator.finishActiveRequest("req-1")
        assertFalse(coordinator.hasPendingOrActiveRequest())

        // Now we can submit again
        assertTrue(coordinator.submit(req2))
    }

    @Test
    fun testReplacePendingPolicy() = runBlocking {
        val coordinator = RequestCoordinator<TestRequest, TestEvent>(
            requestId = { it.id },
            submitPolicy = RequestSubmitPolicy.ReplacePending
        )

        val req1 = TestRequest("req-1", "payload-1")
        val req2 = TestRequest("req-2", "payload-2")

        assertTrue(coordinator.submit(req1))
        // With ReplacePending, submitting another should succeed and replace the pending one
        assertTrue(coordinator.submit(req2))

        // Take req1 (which was replaced) - should return null
        assertNull(coordinator.takeRequest("req-1"))

        // Take req2 - should return req2
        assertEquals(req2, coordinator.takeRequest("req-2"))
    }

    @Test
    fun testCancelPendingRequest() = runBlocking {
        val coordinator = RequestCoordinator<TestRequest, TestEvent>(
            requestId = { it.id },
            submitPolicy = RequestSubmitPolicy.RejectWhenPendingOrActive
        )

        val req = TestRequest("req-1", "payload")
        assertTrue(coordinator.submit(req))

        // Cancel with wrong ID
        assertNull(coordinator.cancelPendingRequest("wrong-id"))
        assertTrue(coordinator.hasPendingOrActiveRequest())

        // Cancel with correct ID
        assertEquals(req, coordinator.cancelPendingRequest("req-1"))
        assertFalse(coordinator.hasPendingOrActiveRequest())
    }

    @Test
    fun testEmitEvents() = runBlocking {
        val coordinator = RequestCoordinator<TestRequest, TestEvent>(
            requestId = { it.id },
            submitPolicy = RequestSubmitPolicy.ReplacePending
        )

        val events = mutableListOf<TestEvent>()
        val job = launch {
            coordinator.events.take(2).toList(events)
        }

        coordinator.emit(TestEvent("Event 1"))
        coordinator.emit(TestEvent("Event 2"))

        job.join()

        assertEquals(2, events.size)
        assertEquals("Event 1", events[0].message)
        assertEquals("Event 2", events[1].message)
    }
}

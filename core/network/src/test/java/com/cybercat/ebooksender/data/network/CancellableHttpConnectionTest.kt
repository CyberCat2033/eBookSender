package com.cybercat.ebooksender.data.network

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellableHttpConnectionTest {
    @Test
    fun cancellationDisconnectsBlockingRequestBeforeJobCompletion() = runBlocking {
        withTimeout(5_000) {
            val connection = TestConnection()
            val request = async(Dispatchers.IO) {
                connection.runDisconnectingOnCancellation { connection.responseCode }
            }
            connection.started.await()

            request.cancelAndJoin()

            assertTrue(connection.disconnected.get())
            assertTrue(request.isCancelled)
        }
    }

    @Test
    fun cancellationDuringSuspendingProgressAlsoDisconnects() = runBlocking {
        withTimeout(5_000) {
            val connection = TestConnection()
            val request = async(Dispatchers.IO) {
                connection.runDisconnectingOnCancellation {
                    connection.started.complete(Unit)
                    awaitCancellation()
                }
            }
            connection.started.await()

            request.cancelAndJoin()

            assertTrue(connection.disconnected.get())
            assertTrue(request.isCancelled)
        }
    }

    @Test
    fun successKeepsConnectionAvailableForCaller() = runBlocking {
        val connection = TestConnection()
        val result = connection.runDisconnectingOnCancellation { ensureActive ->
            ensureActive()
            42
        }

        assertEquals(42, result)
        assertFalse(connection.disconnected.get())
    }

    private class TestConnection : HttpURLConnection(URL("https://example.test/archive.cbz")) {
        val started = CompletableDeferred<Unit>()
        val disconnected = AtomicBoolean(false)
        private val released = CountDownLatch(1)

        override fun connect() = Unit

        override fun usingProxy(): Boolean = false

        override fun disconnect() {
            disconnected.set(true)
            released.countDown()
        }

        override fun getResponseCode(): Int {
            started.complete(Unit)
            check(released.await(4, TimeUnit.SECONDS)) { "Request was not disconnected" }
            throw IOException("Disconnected")
        }
    }
}

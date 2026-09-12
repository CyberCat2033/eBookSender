package com.cybercat.ebooksender.data.opds

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test

class OpdsHttpClientTest {
    @Test
    fun documentDeadlineDisconnectsAStalledResponseBody() = runBlocking {
        withTimeout(5_000) {
            val connection = StalledConnection()
            val client = client(connection, timeoutMillis = 200)

            val error = runCatching { client.readDocument(TEST_URL, "*/*") { it.read() } }
                .exceptionOrNull()

            assertTrue(error is SocketTimeoutException)
            assertTrue(connection.disconnected.get())
        }
    }

    @Test
    fun documentDeadlineAlsoCoversWaitingForResponseHeaders() = runBlocking {
        withTimeout(5_000) {
            val connection = StalledConnection(stallHeaders = true)
            val error = runCatching {
                client(connection, timeoutMillis = 200).readDocument(TEST_URL, "*/*") { it.read() }
            }.exceptionOrNull()

            assertTrue(error is SocketTimeoutException)
            assertTrue(connection.disconnected.get())
        }
    }

    @Test
    fun cancellationDisconnectsTheBodyAndRemainsCancellation() = runBlocking {
        withTimeout(5_000) {
            val connection = StalledConnection()
            val client = client(connection, timeoutMillis = 10_000)
            val request = async { client.readDocument(TEST_URL, "*/*") { it.read() } }
            connection.readStarted.await()

            request.cancelAndJoin()

            assertTrue(request.isCancelled)
            assertTrue(connection.disconnected.get())
        }
    }

    private fun client(connection: HttpURLConnection, timeoutMillis: Long) = OpdsHttpClient(
        credentialsProvider = object : OpdsCredentialsProvider {
            override suspend fun getCredentialsForUrl(urlStr: String): Pair<String, String>? = null
        },
        connectionFactory = { connection },
        documentTimeoutMillis = timeoutMillis
    )

    private class StalledConnection(private val stallHeaders: Boolean = false) :
        HttpURLConnection(URL(TEST_URL)) {
        val disconnected = AtomicBoolean(false)
        val readStarted = CompletableDeferred<Unit>()
        private val released = CountDownLatch(1)

        override fun connect() = Unit

        override fun usingProxy(): Boolean = false

        override fun disconnect() {
            disconnected.set(true)
            released.countDown()
        }

        override fun getResponseCode(): Int {
            if (stallHeaders) stall()
            return HTTP_OK
        }

        override fun getInputStream(): InputStream = object : InputStream() {
            override fun read(): Int = stall()
        }

        private fun stall(): Nothing {
            readStarted.complete(Unit)
            check(released.await(4, TimeUnit.SECONDS)) { "Connection was not disconnected" }
            throw IOException("Disconnected")
        }
    }

    private companion object {
        const val TEST_URL = "https://catalog.test/opds"
    }
}

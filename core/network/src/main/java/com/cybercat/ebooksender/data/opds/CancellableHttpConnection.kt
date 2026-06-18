package com.cybercat.ebooksender.data.opds

import java.net.HttpURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

suspend fun <T> HttpURLConnection.runDisconnectingOnCancellation(
    block: (ensureActive: () -> Unit) -> T
): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        disconnect()
    }

    fun ensureActive() {
        if (!continuation.isActive) {
            throw CancellationException("OPDS request canceled")
        }
    }

    try {
        val result = block(::ensureActive)
        if (continuation.isActive) {
            continuation.resume(result)
        }
    } catch (error: Throwable) {
        if (continuation.isActive) {
            continuation.resumeWithException(error)
        }
    }
}

package com.cybercat.ebooksender.data.network

import java.net.HttpURLConnection
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

suspend fun <T> HttpURLConnection.runDisconnectingOnCancellation(
    block: suspend (ensureActive: () -> Unit) -> T
): T = coroutineScope {
    val requestContext = currentCoroutineContext()
    requestContext.ensureActive()
    // A separate IO coroutine can disconnect even while the request blocks in a socket read.
    // Keep disconnect off the thread that handles the user's cancel action.
    val cancellationWatcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            if (!requestContext.isActive) {
                disconnect()
            }
        }
    }

    try {
        requestContext.ensureActive()
        val result = block { requestContext.ensureActive() }
        requestContext.ensureActive()
        result
    } catch (error: Throwable) {
        requestContext.ensureActive()
        throw error
    } finally {
        cancellationWatcher.cancel()
    }
}

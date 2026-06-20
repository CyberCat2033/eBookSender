package com.cybercat.ebooksender.data.request

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class RequestCoordinator<Request : Any, Event : Any>(
    private val requestId: (Request) -> String,
    private val submitPolicy: RequestSubmitPolicy
) {
    private val lock = Any()
    private var pendingRequest: Request? = null
    private var activeRequestId: String? = null

    private val eventsChannel = Channel<Event>(capacity = Channel.UNLIMITED)
    val events: Flow<Event> = eventsChannel.receiveAsFlow()

    fun submit(request: Request): Boolean = synchronized(lock) {
        when (submitPolicy) {
            RequestSubmitPolicy.RejectWhenPendingOrActive -> {
                if (pendingRequest != null || activeRequestId != null) {
                    return false
                }
                pendingRequest = request
                true
            }

            RequestSubmitPolicy.ReplacePending -> {
                pendingRequest = request
                true
            }
        }
    }

    fun takeRequest(id: String?): Request? = synchronized(lock) {
        val current = pendingRequest ?: return null
        if (requestId(current) != id) return null
        pendingRequest = null
        if (submitPolicy == RequestSubmitPolicy.RejectWhenPendingOrActive) {
            activeRequestId = requestId(current)
        }
        current
    }

    fun cancelPendingRequest(id: String?): Request? = synchronized(lock) {
        val current = pendingRequest ?: return null
        if (requestId(current) != id) return null
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

    fun hasPendingOrActiveRequest(): Boolean = synchronized(lock) {
        pendingRequest != null || activeRequestId != null
    }

    fun emit(event: Event) {
        check(eventsChannel.trySend(event).isSuccess) { "Request event channel is unavailable" }
    }
}

enum class RequestSubmitPolicy {
    RejectWhenPendingOrActive,
    ReplacePending
}

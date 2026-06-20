package com.cybercat.ebooksender.transfer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MetadataExtractionDispatcher(
    private val scope: CoroutineScope,
    private val parallelism: Int,
    private val shouldLoadMetadata: (String) -> Boolean,
    private val loadMetadata: suspend (String) -> Unit
) {
    private val queueLock = Any()
    private val pendingItemIds = ArrayDeque<String>()
    private val queuedItemIds = mutableSetOf<String>()
    private val runningItemIds = mutableSetOf<String>()
    private val workerJobs = mutableSetOf<Job>()
    private var queueSignal = CompletableDeferred<Unit>()

    fun enqueue(itemIds: Iterable<String>) {
        val signal = synchronized(queueLock) {
            var enqueued = false
            itemIds.forEach { itemId ->
                if (
                    itemId !in queuedItemIds &&
                    itemId !in runningItemIds &&
                    shouldLoadMetadata(itemId)
                ) {
                    pendingItemIds.addLast(itemId)
                    queuedItemIds += itemId
                    enqueued = true
                }
            }
            if (enqueued) queueSignal else null
        }

        signal?.let {
            ensureWorkersStarted()
            it.complete(Unit)
        }
    }

    fun prioritize(itemIds: List<String>) {
        val signal = synchronized(queueLock) {
            val prioritizedIds = itemIds.filter { itemId ->
                itemId !in runningItemIds && shouldLoadMetadata(itemId)
            }
            if (prioritizedIds.isEmpty()) {
                null
            } else {
                val currentPendingIds = pendingItemIds.toList()
                val currentPendingIdSet = currentPendingIds.toSet()
                val prioritizedIdSet = prioritizedIds.toSet()
                pendingItemIds.clear()

                prioritizedIds.forEach { itemId ->
                    if (itemId in currentPendingIdSet || queuedItemIds.add(itemId)) {
                        pendingItemIds.addLast(itemId)
                    }
                }
                currentPendingIds.forEach { itemId ->
                    if (itemId !in prioritizedIdSet) {
                        pendingItemIds.addLast(itemId)
                    }
                }
                queueSignal
            }
        }

        signal?.let {
            ensureWorkersStarted()
            it.complete(Unit)
        }
    }

    private fun ensureWorkersStarted() {
        val newJobs = synchronized(queueLock) {
            workerJobs.removeAll { job -> !job.isActive }
            List((parallelism - workerJobs.size).coerceAtLeast(0)) {
                scope.launch(start = CoroutineStart.LAZY) {
                    processQueue()
                }
            }.also { jobs ->
                workerJobs += jobs
            }
        }
        newJobs.forEach(Job::start)
    }

    private suspend fun processQueue() {
        val workerJob = currentCoroutineContext()[Job]
        while (true) {
            val (itemId, signal) = synchronized(queueLock) {
                val nextItemId = pendingItemIds.removeFirstOrNull()
                if (nextItemId != null) {
                    queuedItemIds.remove(nextItemId)
                    runningItemIds += nextItemId
                    nextItemId to queueSignal
                } else {
                    if (queueSignal.isCompleted) {
                        queueSignal = CompletableDeferred()
                    }
                    null to queueSignal
                }
            }

            if (itemId == null) {
                val signaled = withTimeoutOrNull(WORKER_IDLE_TIMEOUT_MILLIS) {
                    signal.await()
                    true
                } == true
                if (!signaled && shouldStopIdleWorker(workerJob)) {
                    return
                }
                continue
            }

            try {
                if (shouldLoadMetadata(itemId)) {
                    loadMetadata(itemId)
                }
            } finally {
                synchronized(queueLock) {
                    runningItemIds.remove(itemId)
                }
            }
        }
    }

    private fun shouldStopIdleWorker(workerJob: Job?): Boolean = synchronized(queueLock) {
        if (pendingItemIds.isNotEmpty()) return@synchronized false
        if (workerJob != null) {
            workerJobs.remove(workerJob)
        }
        true
    }

    private companion object {
        const val WORKER_IDLE_TIMEOUT_MILLIS = 30_000L
    }
}

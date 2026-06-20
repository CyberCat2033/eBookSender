package com.cybercat.ebooksender.transfer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MetadataExtractionDispatcher(
    scope: CoroutineScope,
    parallelism: Int,
    private val shouldLoadMetadata: (String) -> Boolean,
    private val loadMetadata: suspend (String) -> Unit
) {
    private val queueLock = Any()
    private val pendingItemIds = ArrayDeque<String>()
    private val queuedItemIds = mutableSetOf<String>()
    private val runningItemIds = mutableSetOf<String>()
    private var queueSignal = CompletableDeferred<Unit>()

    init {
        repeat(parallelism) {
            scope.launch {
                processQueue()
            }
        }
    }

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

        signal?.complete(Unit)
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

        signal?.complete(Unit)
    }

    private suspend fun processQueue() {
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
                signal.await()
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
}

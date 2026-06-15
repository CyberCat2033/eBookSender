package com.cybercat.pocketbooksender.data.pocketbook

import android.os.SystemClock
import com.cybercat.pocketbooksender.model.PocketBookDevice
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class PocketBookRescanCoordinator @Inject constructor(
    private val controlClient: PocketBookControlClient,
) {
    private val mutex = Mutex()
    private var lastRequestAtMillis = 0L

    suspend fun requestRescanAndWait(device: PocketBookDevice): Result<Unit> {
        val result = requestRescan(device)
        if (result.isSuccess) {
            delay(RescanSettleDelayMillis)
        }
        return result
    }

    private suspend fun requestRescan(device: PocketBookDevice): Result<Unit> =
        mutex.withLock {
            val now = SystemClock.elapsedRealtime()
            val waitMillis = (lastRequestAtMillis + MinRequestIntervalMillis - now).coerceAtLeast(0L)
            if (waitMillis > 0) {
                delay(waitMillis)
            }

            lastRequestAtMillis = SystemClock.elapsedRealtime()
            controlClient.requestDatabaseRescan(device)
        }

    private companion object {
        const val MinRequestIntervalMillis = 5_000L
        const val RescanSettleDelayMillis = 2_000L
    }
}

package com.cybercat.pocketbooksender.data.pocketbook

import android.os.SystemClock
import com.cybercat.pocketbooksender.model.RemoteDevice
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class PocketBookRescanCoordinator @Inject constructor(
    private val controlClient: PocketBookControlClient
) {
    private val mutex = Mutex()
    private var lastRequestAtMillis = 0L

    suspend fun requestRescanAndWait(device: RemoteDevice): Result<Unit> {
        val result = requestRescan(device)
        if (result.isSuccess) {
            delay(RESCAN_SETTLE_DELAY_MILLIS)
        }
        return result
    }

    private suspend fun requestRescan(device: RemoteDevice): Result<Unit> = mutex.withLock {
        val now = SystemClock.elapsedRealtime()
        val waitMillis = (lastRequestAtMillis + MIN_REQUEST_INTERVAL_MILLIS - now).coerceAtLeast(
            0L
        )
        if (waitMillis > 0) {
            delay(waitMillis)
        }

        lastRequestAtMillis = SystemClock.elapsedRealtime()
        controlClient.requestDatabaseRescan(device)
    }

    private companion object {
        const val MIN_REQUEST_INTERVAL_MILLIS = 5_000L
        const val RESCAN_SETTLE_DELAY_MILLIS = 2_000L
    }
}

package com.cybercat.ebooksender.power

import android.content.Context
import android.os.PowerManager

class ScopedWakeLock(
    context: Context,
    tag: String,
    private val timeoutMillis: Long,
) {
    private val wakeLock: PowerManager.WakeLock =
        (context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${context.packageName}:$tag")
            .apply {
                setReferenceCounted(false)
            }

    fun acquire() {
        if (!wakeLock.isHeld) {
            wakeLock.acquire(timeoutMillis)
        }
    }

    fun release() {
        if (wakeLock.isHeld) {
            runCatching {
                wakeLock.release()
            }
        }
    }
}

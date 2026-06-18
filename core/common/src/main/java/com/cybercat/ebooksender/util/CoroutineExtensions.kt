package com.cybercat.ebooksender.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Launches a coroutine that sets a temporary status message using [setMessage] and
 * automatically clears it after [delayMillis] using [clearIfStillCurrent], if the status has not changed in the meantime.
 */
fun CoroutineScope.launchTemporaryStatus(
    message: String,
    delayMillis: Long,
    setMessage: (String) -> Unit,
    clearIfStillCurrent: (String) -> Unit,
) {
    setMessage(message)
    launch {
        delay(delayMillis)
        clearIfStillCurrent(message)
    }
}

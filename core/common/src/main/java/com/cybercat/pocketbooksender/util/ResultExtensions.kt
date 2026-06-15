package com.cybercat.pocketbooksender.util

import kotlinx.coroutines.CancellationException

/**
 * Performs the given [action] on the encapsulated [Throwable] exception if this instance represents [failure],
 * but automatically rethrows the exception if it is a [CancellationException] to preserve coroutine cancellation behavior.
 */
inline fun <T> Result<T>.onFailureRethrowing(action: (exception: Throwable) -> Unit): Result<T> {
    onFailure { error ->
        if (error is CancellationException) throw error
        action(error)
    }
    return this
}

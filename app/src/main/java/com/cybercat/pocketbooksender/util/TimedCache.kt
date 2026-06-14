package com.cybercat.pocketbooksender.util

internal data class TimedCacheEntry<T>(
    val value: T,
    val createdAtMillis: Long = System.currentTimeMillis(),
) {
    fun isFresh(ttlMillis: Long): Boolean =
        System.currentTimeMillis() - createdAtMillis <= ttlMillis
}

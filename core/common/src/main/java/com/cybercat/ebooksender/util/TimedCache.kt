package com.cybercat.ebooksender.util

data class TimedCacheEntry<T>(
    val value: T,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    fun isFresh(ttlMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis - createdAtMillis <= ttlMillis
}

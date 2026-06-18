package com.cybercat.ebooksender.util

class ExpiringLruCache<K, V>(
    private val ttlMillis: Long,
    private val maxSize: Int,
    private val clock: () -> Long = System::currentTimeMillis
) {
    init {
        require(ttlMillis > 0L) { "ttlMillis must be > 0" }
        require(maxSize > 0) { "maxSize must be > 0" }
    }

    private val entries = LinkedHashMap<K, TimedCacheEntry<V>>(maxSize, 0.75f, true)

    @Synchronized
    fun get(key: K): V? {
        val now = clock()
        trimExpiredLocked(now)
        return entries[key]?.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        val now = clock()
        trimExpiredLocked(now)
        entries[key] = TimedCacheEntry(value = value, createdAtMillis = now)
        trimToSizeLocked()
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    private fun trimExpiredLocked(now: Long) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!entry.value.isFresh(ttlMillis, now)) {
                iterator.remove()
            }
        }
    }

    private fun trimToSizeLocked() {
        while (entries.size > maxSize) {
            val eldestKey = entries.entries.iterator().next().key
            entries.remove(eldestKey)
        }
    }
}

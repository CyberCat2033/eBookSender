package com.cybercat.ebooksender.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpiringLruCacheTest {

    @Test
    fun testBasicPutAndGet() {
        val cache = ExpiringLruCache<String, String>(ttlMillis = 1000L, maxSize = 5)
        cache.put("key1", "value1")
        assertEquals("value1", cache.get("key1"))
    }

    @Test
    fun testExpiration() {
        var mockTime = 1000L
        val cache = ExpiringLruCache<String, String>(
            ttlMillis = 100L,
            maxSize = 5,
            clock = { mockTime }
        )

        cache.put("key1", "value1")
        assertEquals("value1", cache.get("key1"))

        // Advance time within TTL
        mockTime += 50
        assertEquals("value1", cache.get("key1"))

        // Advance time past TTL
        mockTime += 51 // total 101, which is > 100
        assertNull(cache.get("key1"))
    }

    @Test
    fun testMaxSizeEviction() {
        val cache = ExpiringLruCache<String, String>(ttlMillis = 10000L, maxSize = 3)
        cache.put("1", "one")
        cache.put("2", "two")
        cache.put("3", "three")

        assertEquals("one", cache.get("1"))

        // Add one more, "2" should be evicted because "1" was recently accessed
        cache.put("4", "four")

        assertEquals("one", cache.get("1"))
        assertNull(cache.get("2"))
        assertEquals("three", cache.get("3"))
        assertEquals("four", cache.get("4"))
    }

    @Test
    fun testClear() {
        val cache = ExpiringLruCache<String, String>(ttlMillis = 1000L, maxSize = 5)
        cache.put("key1", "value1")
        cache.clear()
        assertNull(cache.get("key1"))
    }
}

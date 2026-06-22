package com.cybercat.ebooksender.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlHostMatcherTest {

    @Test
    fun testNormalizedHost() {
        assertEquals("google.com", UrlHostMatcher.normalizedHost("https://google.com/path?query=1"))
        assertEquals("google.com", UrlHostMatcher.normalizedHost("http://google.com"))
        assertEquals("www.google.com", UrlHostMatcher.normalizedHost("http://www.google.com"))
        assertEquals(
            "sub.domain.com",
            UrlHostMatcher.normalizedHost("https://sub.domain.com/index.html")
        )

        assertNull(UrlHostMatcher.normalizedHost(""))
        assertNull(UrlHostMatcher.normalizedHost("   "))
    }

    @Test
    fun testHostsMatch() {
        assertTrue(UrlHostMatcher.hostsMatch("https://google.com/abc", "http://google.com/xyz"))
        assertFalse(UrlHostMatcher.hostsMatch("https://www.google.com", "https://google.com"))
        assertFalse(UrlHostMatcher.hostsMatch("https://google.com", "https://apple.com"))
    }

    @Test
    fun testDisplayHostWithoutWww() {
        assertEquals("google.com", UrlHostMatcher.displayHostWithoutWww("https://www.google.com"))
        assertEquals("google.com", UrlHostMatcher.displayHostWithoutWww("https://google.com"))
        assertEquals(
            "sub.google.com",
            UrlHostMatcher.displayHostWithoutWww("https://sub.google.com")
        )
        assertNull(UrlHostMatcher.displayHostWithoutWww(""))
    }

    @Test
    fun testHostContainsAny() {
        assertTrue(
            UrlHostMatcher.hostContainsAny("https://sub.google.com", listOf("google", "apple"))
        )
        assertTrue(UrlHostMatcher.hostContainsAny("https://google.com", listOf("GOOGLE")))
        assertFalse(UrlHostMatcher.hostContainsAny("https://apple.com", listOf("google")))
        assertFalse(UrlHostMatcher.hostContainsAny("", listOf("google")))
    }
}

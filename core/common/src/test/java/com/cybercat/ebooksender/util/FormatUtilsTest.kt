package com.cybercat.ebooksender.util

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FormatUtilsTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun testFormatBytes() {
        assertEquals("0 B", 0L.formatBytes())
        assertEquals("512 B", 512L.formatBytes())
        assertEquals("1023 B", 1023L.formatBytes())

        assertEquals("1.0 KB", 1024L.formatBytes())
        assertEquals("1.5 KB", 1536L.formatBytes())

        assertEquals("1.0 MB", (1024L * 1024L).formatBytes())
        assertEquals("2.5 MB", (1024L * 1024L * 2.5).toLong().formatBytes())

        assertEquals("1.0 GB", (1024L * 1024L * 1024L).formatBytes())
    }
}

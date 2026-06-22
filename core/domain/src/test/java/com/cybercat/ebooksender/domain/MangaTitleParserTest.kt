package com.cybercat.ebooksender.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaTitleParserTest {

    private val parser = MangaTitleParser()

    @Test
    fun testParseDashPattern() {
        val parts1 = parser.parse("Naruto - 01.cbz")
        assertEquals("Naruto", parts1.series)
        assertEquals("01", parts1.volume)

        val parts2 = parser.parse("One Piece - Chapter 100.cbr")
        assertEquals("One Piece", parts2.series)
        assertEquals("Chapter 100", parts2.volume)
    }

    @Test
    fun testParseUnderscorePattern() {
        val parts1 = parser.parse("Naruto_01.cbz")
        assertEquals("Naruto", parts1.series)
        assertEquals("01", parts1.volume)

        val parts2 = parser.parse("Bleach_05.cbz")
        assertEquals("Bleach", parts2.series)
        assertEquals("05", parts2.volume)
    }

    @Test
    fun testParseSpacePattern() {
        val parts1 = parser.parse("Naruto 01.cbz")
        assertEquals("Naruto", parts1.series)
        assertEquals("01", parts1.volume)

        val parts2 = parser.parse("Naruto v01.cbr")
        assertEquals("Naruto", parts2.series)
        assertEquals("v01", parts2.volume)

        val parts3 = parser.parse("Naruto ch01.cbz")
        assertNull(parts3.series)
        assertNull(parts3.volume)
    }

    @Test
    fun testParseNoMatch() {
        val parts = parser.parse("Naruto.cbz")
        assertNull(parts.series)
        assertNull(parts.volume)

        val partsEmpty = parser.parse("")
        assertNull(partsEmpty.series)
        assertNull(partsEmpty.volume)
    }
}

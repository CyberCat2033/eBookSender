package com.cybercat.ebooksender.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalSortTest {

    @Test
    fun testSimpleNumericSorting() {
        assertTrue(NaturalSort.compare("1", "2") < 0)
        assertTrue(NaturalSort.compare("2", "1") > 0)
        assertTrue(NaturalSort.compare("2", "10") < 0)
        assertTrue(NaturalSort.compare("10", "2") > 0)
        assertEquals(0, NaturalSort.compare("5", "5"))
    }

    @Test
    fun testLeadingZeros() {
        assertTrue(NaturalSort.compare("01", "1") > 0)
        assertTrue(NaturalSort.compare("1", "01") < 0)
        assertTrue(NaturalSort.compare("01", "02") < 0)
        assertTrue(NaturalSort.compare("002", "01") > 0)
    }

    @Test
    fun testMixedAlphaNumeric() {
        assertTrue(NaturalSort.compare("v1.2", "v1.10") < 0)
        assertTrue(NaturalSort.compare("v1.10", "v1.2") > 0)
        assertTrue(NaturalSort.compare("Naruto 2", "Naruto 10") < 0)
        assertTrue(NaturalSort.compare("Chapter 01a", "Chapter 1b") > 0)
        assertTrue(NaturalSort.compare("Chapter 1a", "Chapter 1a") == 0)
    }

    @Test
    fun testCaseInsensitivity() {
        assertEquals(0, NaturalSort.compare("abc", "ABC"))
        assertTrue(NaturalSort.compare("abc", "abd") < 0)
        assertTrue(NaturalSort.compare("ABC", "abd") < 0)
    }

    @Test
    fun testStringLengthTieBreakers() {
        assertTrue(NaturalSort.compare("a", "ab") < 0)
        assertTrue(NaturalSort.compare("ab", "a") > 0)
    }

    @Test
    fun testSortingListsWithComparator() {
        val list = listOf("file 10", "file 2", "file 1", "file 01")
        val sorted = list.sortedWith(NaturalSort.by { it })
        val expected = listOf("file 1", "file 01", "file 2", "file 10")
        assertEquals(expected, sorted)
    }
}

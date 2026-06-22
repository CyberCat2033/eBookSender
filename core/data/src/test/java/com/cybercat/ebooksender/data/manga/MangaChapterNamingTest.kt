package com.cybercat.ebooksender.data.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaChapterNamingTest {

    @Test
    fun testQueueSeriesIndex_whenNumberForSortIsNull_returnsNull() {
        val chapter = chapter(numberForSort = null)
        assertNull(mangaChapterQueueSeriesIndex(chapter))
    }

    @Test
    fun testQueueSeriesIndex_whenNumberForSortIsInteger_returnsIntegerString() {
        val chapter = chapter(numberForSort = 5.0)
        assertEquals("5", mangaChapterQueueSeriesIndex(chapter))
    }

    @Test
    fun testQueueSeriesIndex_whenNumberForSortIsDecimal_returnsDecimalStringWithoutTrailingZeros() {
        val chapter1 = chapter(numberForSort = 5.50)
        assertEquals("5.5", mangaChapterQueueSeriesIndex(chapter1))

        val chapter2 = chapter(numberForSort = 12.003)
        assertEquals("12.003", mangaChapterQueueSeriesIndex(chapter2))
    }

    @Test
    fun testQueueVolume_whenNumberForSortIsNull_returnsTitle() {
        val chapter = chapter(numberForSort = null, title = "Special Episode")
        assertEquals("Special Episode", mangaChapterQueueVolume(chapter))
    }

    @Test
    fun testQueueVolume_whenTitleIsBlank_returnsPaddedSortKey() {
        val chapter = chapter(numberForSort = 4.0, title = "   ")
        assertEquals("0004", mangaChapterQueueVolume(chapter))
    }

    @Test
    fun testQueueVolume_whenTitleMatchesNumberLabel_returnsPaddedSortKey() {
        val chapter = chapter(numberForSort = 12.0, title = "12")
        assertEquals("0012", mangaChapterQueueVolume(chapter))
    }

    @Test
    fun testQueueVolume_whenTitleMatchesDecimalNumberLabel_returnsPaddedSortKey() {
        val chapter = chapter(numberForSort = 3.5, title = "3.5")
        assertEquals("0003.500", mangaChapterQueueVolume(chapter))
    }

    @Test
    fun testQueueVolume_whenTitleIsDifferent_returnsPaddedSortKeyWithTitle() {
        val chapter = chapter(numberForSort = 1.5, title = "The Beginning of the End")
        assertEquals("0001.500_The Beginning of the End", mangaChapterQueueVolume(chapter))
    }

    @Test
    fun testQueueVolume_whenNumberIsLarge_doesNotTruncate() {
        val chapter = chapter(numberForSort = 12345.678, title = "Huge")
        assertEquals("12345.678_Huge", mangaChapterQueueVolume(chapter))
    }

    @Test
    fun testQueueVolume_whenFractionHasTrailingZeros_formatsPrecisely() {
        val chapter = chapter(numberForSort = 2.050, title = "Test")
        assertEquals("0002.050_Test", mangaChapterQueueVolume(chapter))
    }

    private fun chapter(numberForSort: Double?, title: String = "") = MangaChapter(
        sourceId = "test-source",
        seriesId = "test-series",
        chapterId = "test-chapter",
        stableKey = "test-stable-key",
        title = title,
        numberForSort = numberForSort,
        publishedAtMillis = null
    )
}

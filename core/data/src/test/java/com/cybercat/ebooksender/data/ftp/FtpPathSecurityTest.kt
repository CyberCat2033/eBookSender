package com.cybercat.ebooksender.data.ftp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class FtpPathSecurityTest {

    @Test
    fun toSafeRelativeFtpPathConvertsBackslashesAndTrims() {
        assertEquals("books/manga", "books\\manga".toSafeRelativeFtpPath())
        assertEquals("books/manga", "  books/manga  ".toSafeRelativeFtpPath())
    }

    @Test
    fun toSafeRelativeFtpPathThrowsOnBlankOrAbsolute() {
        try {
            "".toSafeRelativeFtpPath()
            fail("Expected IllegalArgumentException for empty path")
        } catch (_: IllegalArgumentException) {}

        try {
            "/absolute/path".toSafeRelativeFtpPath()
            fail("Expected IllegalArgumentException for absolute path")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun toSafeRelativeFtpPathRejectsTraversalSegments() {
        try {
            "books/../manga".toSafeRelativeFtpPath()
            fail("Expected IllegalArgumentException for path traversal")
        } catch (_: IllegalArgumentException) {}

        try {
            "books/./manga".toSafeRelativeFtpPath()
            fail("Expected IllegalArgumentException for single dot segment")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun toSafeRelativeFtpPathOrBlankReturnsBlankOrRunsValidation() {
        assertEquals("", "   ".toSafeRelativeFtpPathOrBlank())
        assertEquals("books/manga", "books/manga".toSafeRelativeFtpPathOrBlank())
        try {
            "/absolute".toSafeRelativeFtpPathOrBlank()
            fail("Expected IllegalArgumentException for absolute path")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun toSafeFtpEntryNameOrNullValidatesNames() {
        assertEquals("valid_name.epub", "valid_name.epub".toSafeFtpEntryNameOrNull())
        assertNull("   ".toSafeFtpEntryNameOrNull())
        assertNull(".".toSafeFtpEntryNameOrNull())
        assertNull("..".toSafeFtpEntryNameOrNull())
        assertNull("folder/file.epub".toSafeFtpEntryNameOrNull())
        assertNull("folder\\file.epub".toSafeFtpEntryNameOrNull())
        // Control character detection
        assertNull("file\u0000name.epub".toSafeFtpEntryNameOrNull())
    }

    @Test
    fun buildSafeChildFtpPathCombinesPathsCorrectly() {
        assertEquals("books/manga/chapter1", buildSafeChildFtpPath("books/manga", "chapter1"))
        assertEquals("chapter1", buildSafeChildFtpPath("", "chapter1"))
        assertEquals("chapter1", buildSafeChildFtpPath("   ", "chapter1"))
        assertNull(buildSafeChildFtpPath("books", ".."))
        assertNull(buildSafeChildFtpPath("books", "folder/file"))
    }
}

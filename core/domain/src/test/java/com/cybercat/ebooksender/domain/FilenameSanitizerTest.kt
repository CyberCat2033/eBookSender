package com.cybercat.ebooksender.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilenameSanitizerTest {

    @Test
    fun testDirectoryNameOrNull() {
        assertEquals("Hello World", FilenameSanitizer.directoryNameOrNull("Hello World"))
        assertEquals("Hello_World", FilenameSanitizer.directoryNameOrNull("Hello/World"))
        assertEquals("Hello_World", FilenameSanitizer.directoryNameOrNull("Hello\\World"))
        assertEquals("Hello_World", FilenameSanitizer.directoryNameOrNull("Hello:World"))
        assertEquals("Hello_World", FilenameSanitizer.directoryNameOrNull("Hello*World"))
        assertEquals("Hello_World", FilenameSanitizer.directoryNameOrNull("Hello?World"))
        assertEquals("Hello_World", FilenameSanitizer.directoryNameOrNull("Hello\"World"))
        assertEquals("Hello_World", FilenameSanitizer.directoryNameOrNull("Hello<World"))
        assertEquals("Hello_World", FilenameSanitizer.directoryNameOrNull("Hello>World"))
        assertEquals("Hello_World", FilenameSanitizer.directoryNameOrNull("Hello|World"))

        // Trimming characters: space and dot should be trimmed for folder names
        assertEquals("Hello World", FilenameSanitizer.directoryNameOrNull("  Hello World.  "))
        assertEquals("Hello World", FilenameSanitizer.directoryNameOrNull("Hello World."))

        // Null or blank strings should return null
        assertNull(FilenameSanitizer.directoryNameOrNull(null))
        assertNull(FilenameSanitizer.directoryNameOrNull("   "))
        assertNull(FilenameSanitizer.directoryNameOrNull("..."))
    }

    @Test
    fun testDirectoryNameWithFallback() {
        assertEquals("Hello World", FilenameSanitizer.directoryName("Hello World", "Fallback"))
        assertEquals("Fallback", FilenameSanitizer.directoryName(null, "Fallback"))
        assertEquals("Fallback", FilenameSanitizer.directoryName("   ", "Fallback"))
    }

    @Test
    fun testFileTitleOrNull() {
        assertEquals("Hello_World", FilenameSanitizer.fileTitleOrNull("Hello World"))
        assertEquals("Hello_World", FilenameSanitizer.fileTitleOrNull("Hello/World"))

        // Collapsing underscores
        assertEquals("Hello_World", FilenameSanitizer.fileTitleOrNull("Hello___World"))
        assertEquals("Hello_World", FilenameSanitizer.fileTitleOrNull("Hello  World"))

        // Trimming characters: underscores and dots should be trimmed
        assertEquals("Hello_World", FilenameSanitizer.fileTitleOrNull("___Hello_World.___"))
        assertEquals("Hello_World", FilenameSanitizer.fileTitleOrNull(".Hello_World."))

        // Null or blank strings should return null
        assertNull(FilenameSanitizer.fileTitleOrNull(null))
        assertNull(FilenameSanitizer.fileTitleOrNull("   "))
        assertNull(FilenameSanitizer.fileTitleOrNull("___"))
        assertNull(FilenameSanitizer.fileTitleOrNull("..."))
    }

    @Test
    fun testFileTitleWithFallback() {
        assertEquals("Hello_World", FilenameSanitizer.fileTitle("Hello World", "Fallback"))
        assertEquals("Fallback", FilenameSanitizer.fileTitle(null, "Fallback"))
        assertEquals("Fallback", FilenameSanitizer.fileTitle("   ", "Fallback"))
    }
}

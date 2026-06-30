package com.cybercat.ebooksender.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookFormatsTest {

    @Test
    fun pickerMimeTypesCoverEverySupportedExtension() {
        assertEquals(AllSupportedExtensions, requiredPickerMimeTypesByExtension.keys)

        val pickerMimeTypes = supportedPickerMimeTypes().toSet()

        requiredPickerMimeTypesByExtension.forEach { (extension, mimeTypes) ->
            val missingMimeTypes = mimeTypes - pickerMimeTypes
            assertTrue(
                "Missing picker MIME types for .$extension: $missingMimeTypes",
                missingMimeTypes.isEmpty()
            )
        }
    }

    @Test
    fun pickerMimeTypesIncludeSafFallbacksForUnknownEbookFormats() {
        val pickerMimeTypes = supportedPickerMimeTypes().toSet()

        assertTrue("application/octet-stream" in pickerMimeTypes)
        assertTrue("application/xml" in pickerMimeTypes)
        assertTrue("text/xml" in pickerMimeTypes)
    }

    @Test
    fun pickerMimeTypesAreUnique() {
        val pickerMimeTypes = supportedPickerMimeTypes()

        assertEquals(pickerMimeTypes.toSet().size, pickerMimeTypes.size)
    }

    private val requiredPickerMimeTypesByExtension = mapOf(
        "epub" to setOf("application/epub+zip"),
        "fb2" to setOf(
            "application/fb2+xml",
            "application/xml",
            "text/fb2+xml",
            "text/xml",
            "application/x-fictionbook",
            "application/x-fictionbook+xml"
        ),
        "mobi" to setOf(
            "application/x-mobipocket-ebook",
            "application/x-mobi8-ebook"
        ),
        "azw3" to setOf(
            "application/vnd.amazon.ebook",
            "application/vnd.amazon.mobi8-ebook"
        ),
        "txt" to setOf("text/plain"),
        "rtf" to setOf(
            "application/rtf",
            "application/x-rtf",
            "text/rtf",
            "text/richtext"
        ),
        "pdf" to setOf("application/pdf"),
        "djvu" to setOf(
            "application/djvu",
            "application/vnd.djvu",
            "application/x-djvu",
            "image/vnd.djvu",
            "image/x-djvu"
        ),
        "doc" to setOf("application/msword"),
        "docx" to setOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ),
        "cbz" to setOf(
            "application/vnd.comicbook+zip",
            "application/x-cbz"
        ),
        "cbr" to setOf(
            "application/vnd.comicbook-rar",
            "application/x-cbr",
            "application/rar",
            "application/vnd.rar",
            "application/x-rar",
            "application/x-rar-compressed"
        )
    )
}

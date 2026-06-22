package com.cybercat.ebooksender.transfer

import com.cybercat.ebooksender.data.transfer.UploadFileSkipReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadItemValidatorTest {

    private val validator = UploadItemValidator()

    @Test
    fun validatesDuplicateUriString() {
        val result = validator.validate(
            uriString = "content://file1",
            displayName = "some_book.epub",
            fileSize = 1024L,
            existingIdentityKeys = setOf("content://file1"),
            maxFileSizeBytes = 10 * 1024 * 1024
        )
        assertEquals(UploadItemValidator.Result.Duplicate, result)
    }

    @Test
    fun validatesSupportedExtensions() {
        val result = validator.validate(
            uriString = "content://file2/my_book.epub",
            displayName = "my_book.epub",
            fileSize = 1024L,
            existingIdentityKeys = emptySet(),
            maxFileSizeBytes = 10 * 1024 * 1024
        )
        assertTrue(result is UploadItemValidator.Result.Accepted)
        assertEquals("my_book.epub", (result as UploadItemValidator.Result.Accepted).displayName)
    }

    @Test
    fun validatesSupportedExtensionsInZip() {
        val result = validator.validate(
            uriString = "content://file3/manga.epub.zip",
            displayName = "manga.epub.zip",
            fileSize = 2048L,
            existingIdentityKeys = emptySet(),
            maxFileSizeBytes = 10 * 1024 * 1024
        )
        assertTrue(result is UploadItemValidator.Result.Accepted)
        assertEquals("manga.epub.zip", (result as UploadItemValidator.Result.Accepted).displayName)
    }

    @Test
    fun validatesUnsupportedExtension() {
        val result = validator.validate(
            uriString = "content://file4/unsupported.png",
            displayName = "unsupported.png",
            fileSize = 1024L,
            existingIdentityKeys = emptySet(),
            maxFileSizeBytes = 10 * 1024 * 1024
        )
        assertTrue(result is UploadItemValidator.Result.Skipped)
        val skipped = result as UploadItemValidator.Result.Skipped
        assertEquals("unsupported.png", skipped.file.displayName)
        assertEquals(UploadFileSkipReason.UnsupportedFormat, skipped.file.reason)
    }

    @Test
    fun validatesTooLargeFile() {
        val result = validator.validate(
            uriString = "content://file5/large_book.epub",
            displayName = "large_book.epub",
            fileSize = 20 * 1024 * 1024L, // 20 MB
            existingIdentityKeys = emptySet(),
            maxFileSizeBytes = 10 * 1024 * 1024L // 10 MB limit
        )
        assertTrue(result is UploadItemValidator.Result.Skipped)
        val skipped = result as UploadItemValidator.Result.Skipped
        assertEquals("large_book.epub", skipped.file.displayName)
        assertEquals(UploadFileSkipReason.TooLarge, skipped.file.reason)
    }
}

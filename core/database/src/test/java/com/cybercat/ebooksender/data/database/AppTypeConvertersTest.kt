package com.cybercat.ebooksender.data.database

import com.cybercat.ebooksender.model.BookCategory
import com.cybercat.ebooksender.model.UploadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AppTypeConvertersTest {
    private val converters = AppTypeConverters()

    @Test
    fun bookCategoryConversion() {
        for (category in BookCategory.entries) {
            val str = converters.bookCategoryToString(category)
            assertEquals(category.name, str)
            val convertedBack = converters.stringToBookCategory(str)
            assertEquals(category, convertedBack)
        }
    }

    @Test
    fun uploadStatusConversion() {
        for (status in UploadStatus.entries) {
            val str = converters.uploadStatusToString(status)
            assertEquals(status.name, str)
            val convertedBack = converters.stringToUploadStatus(str)
            assertEquals(status, convertedBack)
        }
    }
}

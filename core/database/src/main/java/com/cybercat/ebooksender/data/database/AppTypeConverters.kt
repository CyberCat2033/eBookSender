package com.cybercat.ebooksender.data.database

import androidx.room.TypeConverter
import com.cybercat.ebooksender.model.BookCategory
import com.cybercat.ebooksender.model.UploadStatus

class AppTypeConverters {
    @TypeConverter
    fun bookCategoryToString(value: BookCategory): String = value.name

    @TypeConverter
    fun stringToBookCategory(value: String): BookCategory = BookCategory.valueOf(value)

    @TypeConverter
    fun uploadStatusToString(value: UploadStatus): String = value.name

    @TypeConverter
    fun stringToUploadStatus(value: String): UploadStatus = UploadStatus.valueOf(value)
}

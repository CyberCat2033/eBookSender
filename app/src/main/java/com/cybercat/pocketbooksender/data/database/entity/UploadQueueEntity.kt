package com.cybercat.pocketbooksender.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.UploadStatus

@Entity(tableName = "upload_queue")
data class UploadQueueEntity(
    @PrimaryKey val id: String,
    val sourceUri: String,
    val originalName: String,
    val extension: String,
    val category: BookCategory,
    val title: String,
    val author: String?,
    val programmingTag: String?,
    val mangaSeries: String?,
    val mangaVolume: String?,
    val coverUri: String?,
    val plannedPath: String,
    val status: UploadStatus,
    val progress: Float,
    val createdAt: Long,
)

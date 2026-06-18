package com.cybercat.ebooksender.data.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "manga_chapter_history",
    primaryKeys = ["sourceId", "stableKey"],
    indices = [
        Index(value = ["downloadedAtMillis"]),
    ],
)
data class MangaChapterHistoryEntity(
    val sourceId: String,
    val seriesId: String,
    val chapterId: String,
    val stableKey: String,
    val seriesTitle: String,
    val chapterTitle: String,
    val fileName: String,
    val fileUri: String,
    val downloadedAtMillis: Long,
)

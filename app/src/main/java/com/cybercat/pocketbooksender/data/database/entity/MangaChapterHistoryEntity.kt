package com.cybercat.pocketbooksender.data.database.entity

import androidx.room.Entity

@Entity(
    tableName = "manga_chapter_history",
    primaryKeys = ["sourceId", "stableKey"],
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

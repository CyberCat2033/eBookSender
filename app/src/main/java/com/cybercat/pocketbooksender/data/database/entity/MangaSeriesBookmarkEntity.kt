package com.cybercat.pocketbooksender.data.database.entity

import androidx.room.Entity

@Entity(
    tableName = "manga_series_bookmarks",
    primaryKeys = ["sourceId", "seriesId"],
)
data class MangaSeriesBookmarkEntity(
    val sourceId: String,
    val seriesId: String,
    val title: String,
    val coverUrl: String?,
    val description: String?,
    val favorite: Boolean,
    val subscribed: Boolean,
    val addedAtMillis: Long,
    val lastOpenedAtMillis: Long,
    val lastCheckedAtMillis: Long?,
)

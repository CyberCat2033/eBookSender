package com.cybercat.ebooksender.data.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "manga_series_bookmarks",
    primaryKeys = ["sourceId", "seriesId"],
    indices = [
        Index(value = ["subscribed", "title"]),
        Index(
            value = ["favorite", "lastOpenedAtMillis", "title"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC],
        ),
        Index(
            value = ["subscribed", "lastOpenedAtMillis", "title"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC],
        ),
    ],
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

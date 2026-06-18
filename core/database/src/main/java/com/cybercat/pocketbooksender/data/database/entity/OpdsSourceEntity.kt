package com.cybercat.pocketbooksender.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "opds_sources")
data class OpdsSourceEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val enabled: Boolean,
    val lastSyncedAt: Long?
)

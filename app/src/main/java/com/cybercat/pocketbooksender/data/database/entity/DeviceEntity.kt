package com.cybercat.pocketbooksender.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val ftpUrl: String,
    val host: String,
    val port: Int,
    val username: String,
    val rootPath: String,
    val lastConnectedAt: Long,
)

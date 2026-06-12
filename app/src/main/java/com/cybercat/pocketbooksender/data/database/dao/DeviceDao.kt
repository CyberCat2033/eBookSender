package com.cybercat.pocketbooksender.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cybercat.pocketbooksender.data.database.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY lastConnectedAt DESC")
    fun observeDevices(): Flow<List<DeviceEntity>>

    @Upsert
    suspend fun upsert(device: DeviceEntity)
}

package com.cybercat.pocketbooksender.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cybercat.pocketbooksender.data.database.entity.UploadQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadQueueDao {
    @Query("SELECT * FROM upload_queue ORDER BY createdAt")
    fun observeQueue(): Flow<List<UploadQueueEntity>>

    @Upsert
    suspend fun upsertAll(items: List<UploadQueueEntity>)

    @Query("DELETE FROM upload_queue WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM upload_queue")
    suspend fun clear()
}

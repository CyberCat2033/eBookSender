package com.cybercat.pocketbooksender.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cybercat.pocketbooksender.data.database.entity.OpdsSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OpdsSourceDao {
    @Query("SELECT * FROM opds_sources ORDER BY title")
    fun observeSources(): Flow<List<OpdsSourceEntity>>

    @Query("SELECT * FROM opds_sources")
    suspend fun getAllSources(): List<OpdsSourceEntity>

    @Query("SELECT COUNT(*) FROM opds_sources")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(source: OpdsSourceEntity)

    @Query("DELETE FROM opds_sources WHERE id = :id")
    suspend fun deleteById(id: String)
}

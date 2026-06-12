package com.cybercat.pocketbooksender.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cybercat.pocketbooksender.data.database.entity.MangaChapterHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaChapterHistoryDao {
    @Query("SELECT * FROM manga_chapter_history ORDER BY downloadedAtMillis DESC")
    fun observeHistory(): Flow<List<MangaChapterHistoryEntity>>

    @Query("SELECT stableKey FROM manga_chapter_history")
    fun observeDownloadedStableKeys(): Flow<List<String>>

    @Upsert
    suspend fun upsertAll(items: List<MangaChapterHistoryEntity>)
}

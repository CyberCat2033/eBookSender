package com.cybercat.pocketbooksender.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cybercat.pocketbooksender.data.database.entity.MangaSeriesBookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaSeriesBookmarkDao {
    @Query(
        """
        SELECT * FROM manga_series_bookmarks
        WHERE favorite = 1 OR subscribed = 1
        ORDER BY lastOpenedAtMillis DESC, title COLLATE NOCASE
        """,
    )
    fun observeSavedSeries(): Flow<List<MangaSeriesBookmarkEntity>>

    @Query("SELECT * FROM manga_series_bookmarks WHERE subscribed = 1 ORDER BY title COLLATE NOCASE")
    suspend fun subscribedSeries(): List<MangaSeriesBookmarkEntity>

    @Upsert
    suspend fun upsert(series: MangaSeriesBookmarkEntity)

    @Query(
        """
        UPDATE manga_series_bookmarks
        SET favorite = 0
        WHERE favorite = 1 AND subscribed = 1
        """,
    )
    suspend fun normalizeMutualExclusion()

    @Query(
        """
        INSERT INTO manga_series_bookmarks (
            sourceId,
            seriesId,
            title,
            coverUrl,
            description,
            favorite,
            subscribed,
            addedAtMillis,
            lastOpenedAtMillis,
            lastCheckedAtMillis
        ) VALUES (
            :sourceId,
            :seriesId,
            :title,
            :coverUrl,
            :description,
            0,
            0,
            :openedAtMillis,
            :openedAtMillis,
            NULL
        )
        ON CONFLICT(sourceId, seriesId) DO UPDATE SET
            title = excluded.title,
            coverUrl = excluded.coverUrl,
            description = excluded.description,
            lastOpenedAtMillis = excluded.lastOpenedAtMillis
        """,
    )
    suspend fun upsertSnapshot(
        sourceId: String,
        seriesId: String,
        title: String,
        coverUrl: String?,
        description: String?,
        openedAtMillis: Long,
    )

    @Query(
        """
        UPDATE manga_series_bookmarks
        SET favorite = :favorite,
            subscribed = CASE WHEN :favorite = 1 THEN 0 ELSE subscribed END,
            title = :title,
            coverUrl = :coverUrl,
            description = :description,
            lastOpenedAtMillis = :updatedAtMillis
        WHERE sourceId = :sourceId AND seriesId = :seriesId
        """,
    )
    suspend fun setFavorite(
        sourceId: String,
        seriesId: String,
        title: String,
        coverUrl: String?,
        description: String?,
        favorite: Boolean,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE manga_series_bookmarks
        SET subscribed = :subscribed,
            favorite = CASE WHEN :subscribed = 1 THEN 0 ELSE favorite END,
            title = :title,
            coverUrl = :coverUrl,
            description = :description,
            lastOpenedAtMillis = :updatedAtMillis
        WHERE sourceId = :sourceId AND seriesId = :seriesId
        """,
    )
    suspend fun setSubscribed(
        sourceId: String,
        seriesId: String,
        title: String,
        coverUrl: String?,
        description: String?,
        subscribed: Boolean,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        UPDATE manga_series_bookmarks
        SET lastCheckedAtMillis = :checkedAtMillis
        WHERE sourceId = :sourceId AND seriesId = :seriesId
        """,
    )
    suspend fun markChecked(
        sourceId: String,
        seriesId: String,
        checkedAtMillis: Long,
    )
}

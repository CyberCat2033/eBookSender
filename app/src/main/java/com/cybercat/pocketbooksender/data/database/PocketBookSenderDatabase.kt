package com.cybercat.pocketbooksender.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cybercat.pocketbooksender.data.database.dao.MangaChapterHistoryDao
import com.cybercat.pocketbooksender.data.database.dao.MangaSeriesBookmarkDao
import com.cybercat.pocketbooksender.data.database.dao.OpdsSourceDao
import com.cybercat.pocketbooksender.data.database.entity.MangaChapterHistoryEntity
import com.cybercat.pocketbooksender.data.database.entity.MangaSeriesBookmarkEntity
import com.cybercat.pocketbooksender.data.database.entity.OpdsSourceEntity

@Database(
    entities = [
        OpdsSourceEntity::class,
        MangaChapterHistoryEntity::class,
        MangaSeriesBookmarkEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(AppTypeConverters::class)
abstract class PocketBookSenderDatabase : RoomDatabase() {
    abstract fun opdsSourceDao(): OpdsSourceDao
    abstract fun mangaChapterHistoryDao(): MangaChapterHistoryDao
    abstract fun mangaSeriesBookmarkDao(): MangaSeriesBookmarkDao
}

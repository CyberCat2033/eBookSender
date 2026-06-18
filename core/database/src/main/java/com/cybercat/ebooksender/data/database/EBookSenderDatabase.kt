package com.cybercat.ebooksender.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cybercat.ebooksender.data.database.dao.MangaChapterHistoryDao
import com.cybercat.ebooksender.data.database.dao.MangaSeriesBookmarkDao
import com.cybercat.ebooksender.data.database.dao.OpdsSourceDao
import com.cybercat.ebooksender.data.database.entity.MangaChapterHistoryEntity
import com.cybercat.ebooksender.data.database.entity.MangaSeriesBookmarkEntity
import com.cybercat.ebooksender.data.database.entity.OpdsSourceEntity

@Database(
    entities = [
        OpdsSourceEntity::class,
        MangaChapterHistoryEntity::class,
        MangaSeriesBookmarkEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class EBookSenderDatabase : RoomDatabase() {
    abstract fun opdsSourceDao(): OpdsSourceDao
    abstract fun mangaChapterHistoryDao(): MangaChapterHistoryDao
    abstract fun mangaSeriesBookmarkDao(): MangaSeriesBookmarkDao
}

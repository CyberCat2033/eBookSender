package com.cybercat.pocketbooksender.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cybercat.pocketbooksender.data.database.dao.DeviceDao
import com.cybercat.pocketbooksender.data.database.dao.MangaChapterHistoryDao
import com.cybercat.pocketbooksender.data.database.dao.MangaSeriesBookmarkDao
import com.cybercat.pocketbooksender.data.database.dao.OpdsSourceDao
import com.cybercat.pocketbooksender.data.database.dao.UploadQueueDao
import com.cybercat.pocketbooksender.data.database.entity.DeviceEntity
import com.cybercat.pocketbooksender.data.database.entity.MangaChapterHistoryEntity
import com.cybercat.pocketbooksender.data.database.entity.MangaSeriesBookmarkEntity
import com.cybercat.pocketbooksender.data.database.entity.OpdsSourceEntity
import com.cybercat.pocketbooksender.data.database.entity.UploadQueueEntity

@Database(
    entities = [
        DeviceEntity::class,
        OpdsSourceEntity::class,
        UploadQueueEntity::class,
        MangaChapterHistoryEntity::class,
        MangaSeriesBookmarkEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(AppTypeConverters::class)
abstract class PocketBookSenderDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun opdsSourceDao(): OpdsSourceDao
    abstract fun uploadQueueDao(): UploadQueueDao
    abstract fun mangaChapterHistoryDao(): MangaChapterHistoryDao
    abstract fun mangaSeriesBookmarkDao(): MangaSeriesBookmarkDao
}

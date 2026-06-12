package com.cybercat.pocketbooksender.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cybercat.pocketbooksender.data.database.PocketBookSenderDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val Migration1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `manga_chapter_history` (
                    `sourceId` TEXT NOT NULL,
                    `seriesId` TEXT NOT NULL,
                    `chapterId` TEXT NOT NULL,
                    `stableKey` TEXT NOT NULL,
                    `seriesTitle` TEXT NOT NULL,
                    `chapterTitle` TEXT NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `fileUri` TEXT NOT NULL,
                    `downloadedAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`sourceId`, `stableKey`)
                )
                """.trimIndent(),
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): PocketBookSenderDatabase =
        Room.databaseBuilder(
            context,
            PocketBookSenderDatabase::class.java,
            "pocketbook_sender.db",
        )
            .addMigrations(Migration1To2)
            .build()

    @Provides
    fun provideDeviceDao(database: PocketBookSenderDatabase) = database.deviceDao()

    @Provides
    fun provideOpdsSourceDao(database: PocketBookSenderDatabase) = database.opdsSourceDao()

    @Provides
    fun provideUploadQueueDao(database: PocketBookSenderDatabase) = database.uploadQueueDao()

    @Provides
    fun provideMangaChapterHistoryDao(database: PocketBookSenderDatabase) =
        database.mangaChapterHistoryDao()
}

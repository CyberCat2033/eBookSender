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

    private val Migration2To3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `manga_series_bookmarks` (
                    `sourceId` TEXT NOT NULL,
                    `seriesId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `coverUrl` TEXT,
                    `description` TEXT,
                    `favorite` INTEGER NOT NULL,
                    `subscribed` INTEGER NOT NULL,
                    `addedAtMillis` INTEGER NOT NULL,
                    `lastOpenedAtMillis` INTEGER NOT NULL,
                    `lastCheckedAtMillis` INTEGER,
                    PRIMARY KEY(`sourceId`, `seriesId`)
                )
                """.trimIndent(),
            )
        }
    }

    private val Migration3To4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `devices`")
            db.execSQL("DROP TABLE IF EXISTS `upload_queue`")
        }
    }

    private val Migration4To5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `opds_sources` ADD COLUMN `username` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `opds_sources` ADD COLUMN `password` TEXT DEFAULT NULL")
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
            .addMigrations(Migration1To2, Migration2To3, Migration3To4, Migration4To5)
            .build()

    @Provides
    fun provideOpdsSourceDao(database: PocketBookSenderDatabase) = database.opdsSourceDao()

    @Provides
    fun provideMangaChapterHistoryDao(database: PocketBookSenderDatabase) =
        database.mangaChapterHistoryDao()

    @Provides
    fun provideMangaSeriesBookmarkDao(database: PocketBookSenderDatabase) =
        database.mangaSeriesBookmarkDao()
}

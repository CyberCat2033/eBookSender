package com.cybercat.ebooksender.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cybercat.ebooksender.data.database.EBookSenderDatabase
import com.cybercat.ebooksender.data.opds.OpdsSecureCredentialsStore
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
                """.trimIndent()
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
                """.trimIndent()
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

    private val Migration5To6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_manga_chapter_history_downloadedAtMillis`
                ON `manga_chapter_history` (`downloadedAtMillis`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_manga_series_bookmarks_subscribed_title`
                ON `manga_series_bookmarks` (`subscribed`, `title`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_manga_series_bookmarks_favorite_lastOpenedAtMillis_title`
                ON `manga_series_bookmarks` (`favorite`, `lastOpenedAtMillis` DESC, `title`)
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_manga_series_bookmarks_subscribed_lastOpenedAtMillis_title`
                ON `manga_series_bookmarks` (`subscribed`, `lastOpenedAtMillis` DESC, `title`)
                """.trimIndent()
            )
        }
    }

    private fun migration6To7(context: Context) = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val secureStore = OpdsSecureCredentialsStore(context)
            db.query("SELECT `id`, `username`, `password` FROM `opds_sources`").use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow("id")
                val usernameIndex = cursor.getColumnIndexOrThrow("username")
                val passwordIndex = cursor.getColumnIndexOrThrow("password")
                while (cursor.moveToNext()) {
                    val sourceId = cursor.getString(idIndex)
                    val username = cursor.takeUnless {
                        it.isNull(usernameIndex)
                    }?.getString(usernameIndex)
                    val password = cursor.takeUnless {
                        it.isNull(passwordIndex)
                    }?.getString(passwordIndex)
                    if (!username.isNullOrBlank() || !password.isNullOrBlank()) {
                        secureStore.save(
                            sourceId = sourceId,
                            username = username,
                            password = password
                        )
                    }
                }
            }

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `opds_sources_new` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `url` TEXT NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    `lastSyncedAt` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `opds_sources_new` (`id`, `title`, `url`, `enabled`, `lastSyncedAt`)
                SELECT `id`, `title`, `url`, `enabled`, `lastSyncedAt`
                FROM `opds_sources`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `opds_sources`")
            db.execSQL("ALTER TABLE `opds_sources_new` RENAME TO `opds_sources`")
        }
    }

    private val Migration7To8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `encrypted_secrets` (
                    `namespace` TEXT NOT NULL,
                    `ownerId` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `ciphertext` BLOB NOT NULL,
                    `iv` BLOB NOT NULL,
                    `updatedAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`namespace`, `ownerId`, `name`)
                )
                """.trimIndent()
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EBookSenderDatabase =
        Room.databaseBuilder(
            context,
            EBookSenderDatabase::class.java,
            "ebook_sender.db"
        )
            .addMigrations(
                Migration1To2,
                Migration2To3,
                Migration3To4,
                Migration4To5,
                Migration5To6,
                migration6To7(context),
                Migration7To8
            )
            .build()

    @Provides
    fun provideOpdsSourceDao(database: EBookSenderDatabase) = database.opdsSourceDao()

    @Provides
    fun provideMangaChapterHistoryDao(database: EBookSenderDatabase) =
        database.mangaChapterHistoryDao()

    @Provides
    fun provideMangaSeriesBookmarkDao(database: EBookSenderDatabase) =
        database.mangaSeriesBookmarkDao()

    @Provides
    fun provideEncryptedSecretDao(database: EBookSenderDatabase) = database.encryptedSecretDao()
}

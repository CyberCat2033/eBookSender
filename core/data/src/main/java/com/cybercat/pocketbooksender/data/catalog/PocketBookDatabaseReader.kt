package com.cybercat.pocketbooksender.data.catalog

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.cybercat.pocketbooksender.data.ftp.FtpGateway
import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.DEFAULT_FTP_RELATIVE_ROOT_PATH
import com.cybercat.pocketbooksender.model.PocketBookDevice
import com.cybercat.pocketbooksender.model.normalizeFtpRelativeRootPath
import com.cybercat.pocketbooksender.model.normalizeFtpRootPath
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val POCKET_BOOK_STORAGE_PREFIX = "/mnt/ext1/"

@Singleton
class PocketBookDatabaseReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ftpGateway: FtpGateway
) {
    internal suspend fun readCatalogFiles(
        device: PocketBookDevice,
        settings: AppSettings
    ): List<DbCatalogFile> {
        val dbFile = downloadDatabaseSnapshot(
            device.copy(relativeRootPath = DEFAULT_FTP_RELATIVE_ROOT_PATH)
        )
        return readDatabaseFiles(dbFile, device, settings)
    }

    private suspend fun downloadDatabaseSnapshot(device: PocketBookDevice): File {
        val directory = File(context.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }

        DATABASE_FILES.forEach { name ->
            File(directory, name).delete()
        }

        val dbFile = File(directory, DATABASE_NAME)
        dbFile.outputStream().use { outputStream ->
            ftpGateway.downloadFile(
                device = device,
                remoteRelativePath = "$REMOTE_DATABASE_DIRECTORY/$DATABASE_NAME",
                output = outputStream
            ).getOrThrow()
        }

        OPTIONAL_DATABASE_FILES.forEach { name ->
            val file = File(directory, name)
            file.outputStream().use { outputStream ->
                ftpGateway.downloadFile(
                    device = device,
                    remoteRelativePath = "$REMOTE_DATABASE_DIRECTORY/$name",
                    output = outputStream
                ).onFailure {
                    file.delete()
                }
            }
        }

        return dbFile
    }

    private fun readDatabaseFiles(
        dbFile: File,
        device: PocketBookDevice,
        settings: AppSettings
    ): List<DbCatalogFile> {
        val database = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )

        return database.use { db ->
            db.execSQL("PRAGMA query_only = ON")
            val storageRootPrefix = device.storageRootPrefix(settings)
            val args = arrayOf(
                "$storageRootPrefix${settings.booksFolderName}%",
                "$storageRootPrefix${settings.documentsFolderName}%",
                "$storageRootPrefix${settings.mangaFolderName}%"
            )
            db.rawQuery(CATALOG_QUERY, args).use { cursor ->
                cursor.requireCatalogShape()
                val parsedFiles = cursor.toDbCatalogFiles(storageRootPrefix)
                if (parsedFiles.isEmpty() && cursor.count > 0) {
                    throw UnsupportedCatalogDatabaseException(
                        "Catalog query returned rows but no parsable file entries"
                    )
                }
                parsedFiles
            }
        }
    }

    private fun Cursor.toDbCatalogFiles(storageRootPrefix: String): List<DbCatalogFile> {
        return buildList {
            while (moveToNext()) {
                runCatching { toDbCatalogFile(storageRootPrefix) }
                    .getOrNull()
                    ?.let(::add)
            }
        }
    }

    private fun Cursor.requireCatalogShape() {
        val missingColumns = REQUIRED_CATALOG_COLUMNS.filter { getColumnIndex(it) < 0 }
        if (missingColumns.isNotEmpty()) {
            throw UnsupportedCatalogDatabaseException(
                "Unsupported PocketBook DB catalog schema: missing ${missingColumns.joinToString()}"
            )
        }
    }

    private fun Cursor.toDbCatalogFile(storageRootPrefix: String): DbCatalogFile? {
        val folder = string("folder")?.trimEnd('/') ?: return null
        val fileName = string("filename")?.takeIf { it.isNotBlank() } ?: return null
        val relativePath = normalizePath("$folder/$fileName", storageRootPrefix) ?: return null
        val completed = int("completed") == 1
        val cpage = int("cpage")
        val npage = int("npage")

        return DbCatalogFile(
            fileId = long("file_id"),
            bookId = long("book_id"),
            name = fileName,
            path = relativePath,
            size = long("file_size") ?: long("book_size") ?: 0L,
            modifiedAtMillis = long("modification_time")?.secondsToMillis(),
            title = string("title")?.cleanText(),
            authors = string("author").splitAuthors(),
            readProgressPercent = readProgressPercent(
                completed = completed,
                cpage = cpage,
                npage = npage
            ),
            completed = completed,
            lastOpenedAtMillis = long("opentime")?.takeIf { it > 0 }?.secondsToMillis(),
            cpage = cpage,
            npage = npage,
            series = string("series")?.cleanText()
        )
    }

    private companion object {
        const val CACHE_DIRECTORY = "pocketbook-catalog"
        const val REMOTE_DATABASE_DIRECTORY = "system/explorer-3"
        const val DATABASE_NAME = "explorer-3.db"

        val OPTIONAL_DATABASE_FILES = listOf("$DATABASE_NAME-wal", "$DATABASE_NAME-shm")
        val DATABASE_FILES = listOf(DATABASE_NAME) + OPTIONAL_DATABASE_FILES

        val CATALOG_QUERY = """
            SELECT
                f.id AS file_id,
                b.id AS book_id,
                d.name AS folder,
                f.filename AS filename,
                f.size AS file_size,
                f.modification_time AS modification_time,
                b.title AS title,
                b.author AS author,
                b.series AS series,
                b.size AS book_size,
                bs.cpage AS cpage,
                bs.npage AS npage,
                bs.opentime AS opentime,
                bs.completed AS completed
            FROM files f
            LEFT JOIN folders d ON d.id = f.folder_id
            LEFT JOIN books_impl b ON b.id = f.book_id
            LEFT JOIN books_settings bs ON bs.bookid = b.id
                AND bs.profileid = COALESCE(
                    (SELECT id FROM profiles WHERE name = 'default' LIMIT 1),
                    (SELECT MIN(id) FROM profiles)
                )
            WHERE d.name LIKE ?
                OR d.name LIKE ?
                OR d.name LIKE ?
            ORDER BY d.name, f.filename
        """.trimIndent()

        private val REQUIRED_CATALOG_COLUMNS = listOf("folder", "filename")
    }

    private class UnsupportedCatalogDatabaseException(message: String) : IllegalStateException(message)
}

private fun PocketBookDevice.storageRootPrefix(settings: AppSettings): String {
    val mountRoot = normalizeFtpRootPath(rootPath).takeUnless { it == "/" }
        ?: POCKET_BOOK_STORAGE_PREFIX.trimEnd('/')
    val relativeRoot = normalizeFtpRelativeRootPath(settings.rootPath)
    return if (relativeRoot.isBlank()) {
        "$mountRoot/"
    } else {
        "$mountRoot/$relativeRoot/"
    }
}

internal data class DbCatalogFile(
    val fileId: Long?,
    val bookId: Long?,
    val name: String,
    val path: String,
    val size: Long,
    val modifiedAtMillis: Long?,
    val title: String?,
    val authors: List<String>,
    val readProgressPercent: Int?,
    val completed: Boolean,
    val lastOpenedAtMillis: Long?,
    val cpage: Int?,
    val npage: Int?,
    val series: String?
)

private fun normalizePath(absolutePath: String, storageRootPrefix: String): String? = absolutePath
    .removePrefix(storageRootPrefix)
    .trim('/')
    .takeIf { it.isNotBlank() }

private fun String?.splitAuthors(): List<String> = orEmpty()
    .split(',')
    .mapNotNull { it.cleanText() }

private fun String.cleanText(): String? = trim()
    .replace(Regex("\\s+"), " ")
    .takeIf { it.isNotBlank() }

private fun readProgressPercent(completed: Boolean, cpage: Int?, npage: Int?): Int? {
    if (completed) return 100
    val current = cpage ?: return null
    val total = npage?.takeIf { it > 0 } ?: return null
    return ((current.coerceAtLeast(0) * 100.0) / total)
        .toInt()
        .coerceIn(0, 100)
}

private fun Long.secondsToMillis(): Long = this * 1_000L

private fun Cursor.string(column: String): String? {
    val index = getColumnIndex(column)
    if (index < 0 || isNull(index)) return null
    return getString(index)
}

private fun Cursor.int(column: String): Int? {
    val index = getColumnIndex(column)
    if (index < 0 || isNull(index)) return null
    return getInt(index)
}

private fun Cursor.long(column: String): Long? {
    val index = getColumnIndex(column)
    if (index < 0 || isNull(index)) return null
    return getLong(index)
}

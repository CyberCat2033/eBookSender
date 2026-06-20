package com.cybercat.ebooksender.data.catalog

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.cybercat.ebooksender.data.ftp.FtpEntry
import com.cybercat.ebooksender.data.ftp.FtpGateway
import com.cybercat.ebooksender.data.pocketbook.PocketBookLibraryPaths
import com.cybercat.ebooksender.model.AppSettings
import com.cybercat.ebooksender.model.DEFAULT_FTP_RELATIVE_ROOT_PATH
import com.cybercat.ebooksender.model.RemoteDevice
import com.cybercat.ebooksender.model.normalizeFtpRelativeRootPath
import com.cybercat.ebooksender.model.normalizeFtpRootPath
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PocketBookDatabaseReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ftpGateway: FtpGateway
) {
    internal suspend fun readCatalogFiles(
        device: RemoteDevice,
        settings: AppSettings
    ): List<DbCatalogFile> {
        val dbFile = downloadDatabaseSnapshot(
            device.copy(relativeRootPath = DEFAULT_FTP_RELATIVE_ROOT_PATH)
        )
        return readDatabaseFiles(dbFile, device, settings)
    }

    private suspend fun downloadDatabaseSnapshot(device: RemoteDevice): File {
        val directory = File(context.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }
        val remoteFiles = ftpGateway.listEntries(
            device = device,
            remoteRelativePath = PocketBookLibraryPaths.REMOTE_DATABASE_DIRECTORY
        ).getOrNull()
            ?.filter { entry -> !entry.isDirectory && entry.name in DATABASE_FILES }
            ?.associateBy { entry -> entry.name }

        if (remoteFiles != null && canReuseSnapshot(directory, remoteFiles)) {
            return File(directory, PocketBookLibraryPaths.DATABASE_NAME)
        }

        DATABASE_FILES.forEach { name ->
            File(directory, name).delete()
            File(directory, "$name.download").delete()
        }
        val dbFile = File(directory, PocketBookLibraryPaths.DATABASE_NAME)
        downloadSnapshotFile(device, directory, PocketBookLibraryPaths.DATABASE_NAME)
            .getOrThrow()

        OPTIONAL_DATABASE_FILES
            .filter { name -> remoteFiles == null || remoteFiles.containsKey(name) }
            .forEach { name ->
                downloadSnapshotFile(device, directory, name)
                    .onFailure {
                        File(directory, name).delete()
                        File(directory, "$name.download").delete()
                    }
            }

        if (remoteFiles == null) {
            File(directory, SNAPSHOT_METADATA_FILE).delete()
        } else {
            saveSnapshotMetadata(
                directory = directory,
                files = remoteFiles
                    .filterKeys { name -> File(directory, name).isFile }
                    .mapValues { (_, entry) -> entry.toSnapshotFileMetadata() }
            )
        }

        return dbFile
    }

    private suspend fun downloadSnapshotFile(
        device: RemoteDevice,
        directory: File,
        name: String
    ): Result<Unit> = runCatching {
        val target = File(directory, name)
        val temporary = File(directory, "$name.download")
        temporary.outputStream().use { outputStream ->
            ftpGateway.downloadFile(
                device = device,
                remoteRelativePath = "${PocketBookLibraryPaths.REMOTE_DATABASE_DIRECTORY}/$name",
                output = outputStream
            ).getOrThrow()
        }
        if (target.exists()) {
            check(target.delete()) { "Cannot replace cached PocketBook database file $name" }
        }
        check(temporary.renameTo(target)) {
            "Cannot finalize cached PocketBook database file $name"
        }
    }

    private fun canReuseSnapshot(directory: File, remoteFiles: Map<String, FtpEntry>): Boolean {
        val cachedMetadata = readSnapshotMetadata(directory)
        if (PocketBookLibraryPaths.DATABASE_NAME !in remoteFiles) return false

        return DATABASE_FILES.all { name ->
            val localFile = File(directory, name)
            val remoteMetadata = remoteFiles[name]?.toSnapshotFileMetadata()
            if (remoteMetadata == null) {
                !localFile.exists() && name !in cachedMetadata
            } else {
                localFile.isFile &&
                    localFile.length() == remoteMetadata.size &&
                    cachedMetadata[name] == remoteMetadata
            }
        }
    }

    private fun readSnapshotMetadata(directory: File): Map<String, SnapshotFileMetadata> {
        val file = File(directory, SNAPSHOT_METADATA_FILE)
        if (!file.isFile) return emptyMap()

        val properties = Properties()
        runCatching {
            file.inputStream().use(properties::load)
        }.getOrElse {
            return emptyMap()
        }

        return DATABASE_FILES.mapNotNull { name ->
            val size = properties.getProperty("$name.size")?.toLongOrNull()
                ?: return@mapNotNull null
            val modifiedAtMillis = properties
                .getProperty("$name.modifiedAtMillis")
                ?.takeIf(String::isNotBlank)
                ?.toLongOrNull()
            name to SnapshotFileMetadata(
                size = size,
                modifiedAtMillis = modifiedAtMillis
            )
        }.toMap()
    }

    private fun saveSnapshotMetadata(directory: File, files: Map<String, SnapshotFileMetadata>) {
        val properties = Properties()
        files.forEach { (name, metadata) ->
            properties.setProperty("$name.size", metadata.size.toString())
            properties.setProperty(
                "$name.modifiedAtMillis",
                metadata.modifiedAtMillis?.toString().orEmpty()
            )
        }
        File(directory, SNAPSHOT_METADATA_FILE).outputStream().use { output ->
            properties.store(output, null)
        }
    }

    private fun readDatabaseFiles(
        dbFile: File,
        device: RemoteDevice,
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

    private fun Cursor.toDbCatalogFiles(storageRootPrefix: String): List<DbCatalogFile> =
        buildList {
            while (moveToNext()) {
                runCatching { toDbCatalogFile(storageRootPrefix) }
                    .getOrNull()
                    ?.let(::add)
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
        const val SNAPSHOT_METADATA_FILE = "snapshot.properties"

        val OPTIONAL_DATABASE_FILES = listOf(
            "${PocketBookLibraryPaths.DATABASE_NAME}-wal",
            "${PocketBookLibraryPaths.DATABASE_NAME}-shm"
        )
        val DATABASE_FILES = listOf(PocketBookLibraryPaths.DATABASE_NAME) + OPTIONAL_DATABASE_FILES

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

    private class UnsupportedCatalogDatabaseException(message: String) :
        IllegalStateException(message)
}

private data class SnapshotFileMetadata(val size: Long, val modifiedAtMillis: Long?)

private fun FtpEntry.toSnapshotFileMetadata() = SnapshotFileMetadata(
    size = size,
    modifiedAtMillis = modifiedAtMillis
)

private fun RemoteDevice.storageRootPrefix(settings: AppSettings): String {
    val mountRoot = normalizeFtpRootPath(rootPath).takeUnless { it == "/" }
        ?: PocketBookLibraryPaths.STORAGE_PREFIX.trimEnd('/')
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
    .mapNotNull { author -> author.cleanText().takeIf { it.isNotBlank() } }

private fun String.cleanText(): String = trim()
    .replace(Regex("\\s+"), " ")

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

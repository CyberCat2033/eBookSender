package com.cybercat.pocketbooksender.data.catalog

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.cybercat.pocketbooksender.data.ftp.FtpEntry
import com.cybercat.pocketbooksender.data.ftp.FtpGateway
import com.cybercat.pocketbooksender.domain.AllSupportedExtensions
import com.cybercat.pocketbooksender.domain.MangaArchiveExtensions
import com.cybercat.pocketbooksender.domain.NaturalSort
import com.cybercat.pocketbooksender.domain.bookTitleWithoutExtension
import com.cybercat.pocketbooksender.domain.contentExtension
import com.cybercat.pocketbooksender.model.CatalogFile
import com.cybercat.pocketbooksender.model.CatalogGroup
import com.cybercat.pocketbooksender.model.DeviceCatalog
import com.cybercat.pocketbooksender.model.MangaSeriesGroup
import com.cybercat.pocketbooksender.model.PocketBookDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PocketBookStoragePrefix = "/mnt/ext1/"

@Singleton
class DeviceCatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ftpGateway: FtpGateway,
) {
    suspend fun load(device: PocketBookDevice): DeviceCatalog = withContext(Dispatchers.IO) {
        val databaseCatalog = runCatching { loadFromPocketBookDatabase(device) }.getOrNull()
        if (databaseCatalog != null && !databaseCatalog.isEmpty) {
            databaseCatalog
        } else {
            loadFromFolders(device)
        }
    }

    private suspend fun loadFromPocketBookDatabase(device: PocketBookDevice): DeviceCatalog {
        val dbFile = downloadDatabaseSnapshot(device)
        val files = readDatabaseFiles(dbFile)
            .filter { it.path.contentExtension() in AllSupportedExtensions }

        return DeviceCatalog(
            books = files.toBookGroups(),
            programming = files.toProgrammingGroups(),
            manga = files.toMangaGroups(),
            scannedAtMillis = System.currentTimeMillis(),
            isLoading = false,
            errorMessage = null,
        )
    }

    private suspend fun downloadDatabaseSnapshot(device: PocketBookDevice): File {
        val directory = File(context.cacheDir, CacheDirectory).apply { mkdirs() }

        DatabaseFiles.forEach { name ->
            File(directory, name).delete()
        }

        val dbFile = File(directory, DatabaseName)
        dbFile.outputStream().use { outputStream ->
            ftpGateway.downloadFile(
                device = device,
                remoteRelativePath = "$RemoteDatabaseDirectory/$DatabaseName",
                output = outputStream,
            ).getOrThrow()
        }

        OptionalDatabaseFiles.forEach { name ->
            val file = File(directory, name)
            file.outputStream().use { outputStream ->
                ftpGateway.downloadFile(
                    device = device,
                    remoteRelativePath = "$RemoteDatabaseDirectory/$name",
                    output = outputStream,
                ).onFailure {
                    file.delete()
                }
            }
        }

        return dbFile
    }

    private fun readDatabaseFiles(dbFile: File): List<DbCatalogFile> {
        val database = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )

        return database.use { db ->
            db.execSQL("PRAGMA query_only = ON")
            val args = arrayOf(
                "$PocketBookStoragePrefix$BooksRoot%",
                "$PocketBookStoragePrefix$ProgrammingRoot%",
                "$PocketBookStoragePrefix$MangaRoot%",
            )
            db.rawQuery(CatalogQuery, args).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        cursor.toDbCatalogFile()?.let(::add)
                    }
                }
            }
        }
    }

    private fun Cursor.toDbCatalogFile(): DbCatalogFile? {
        val folder = string("folder")?.trimEnd('/') ?: return null
        val fileName = string("filename")?.takeIf { it.isNotBlank() } ?: return null
        val relativePath = normalizePath("$folder/$fileName") ?: return null
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
                npage = npage,
            ),
            completed = completed,
            lastOpenedAtMillis = long("opentime")?.takeIf { it > 0 }?.secondsToMillis(),
            cpage = cpage,
            npage = npage,
            series = string("series")?.cleanText(),
        )
    }

    private fun List<DbCatalogFile>.toBookGroups(): List<CatalogGroup> =
        filter { it.path.isUnder(BooksRoot) }
            .deduplicateIn(BooksRoot)
            .groupBy { file ->
                file.path.directoryAfter(BooksRoot)
                    ?: file.authors.firstOrNull()
                    ?: "Unknown Author"
            }
            .map { (name, files) ->
                CatalogGroup(
                    name = name,
                    path = "$BooksRoot/$name",
                    files = files.toCatalogFiles(),
                )
            }
            .filter { it.files.isNotEmpty() }
            .sortedWith(NaturalSort.by { it.name })

    private fun List<DbCatalogFile>.toProgrammingGroups(): List<CatalogGroup> =
        filter { it.path.isUnder(ProgrammingRoot) }
            .deduplicateIn(ProgrammingRoot)
            .groupBy { file ->
                file.path.directoryAfter(ProgrammingRoot) ?: "Untagged"
            }
            .map { (name, files) ->
                CatalogGroup(
                    name = name,
                    path = "$ProgrammingRoot/$name",
                    files = files.toCatalogFiles(),
                )
            }
            .filter { it.files.isNotEmpty() }
            .sortedWith(NaturalSort.by { it.name })

    private fun List<DbCatalogFile>.toMangaGroups(): List<MangaSeriesGroup> =
        filter { it.path.isUnder(MangaRoot) }
            .deduplicateIn(MangaRoot)
            .groupBy { file ->
                file.path.directoryAfter(MangaRoot) ?: "Unsorted Manga"
            }
            .map { (name, files) ->
                val catalogFiles = files.toCatalogFiles()
                MangaSeriesGroup(
                    name = name,
                    path = "$MangaRoot/$name",
                    latestFile = catalogFiles.lastOrNull(),
                    lastReadFile = catalogFiles.lastReadFile(),
                    files = catalogFiles,
                )
            }
            .filter { it.files.isNotEmpty() }
            .sortedWith(NaturalSort.by { it.name })

    private fun List<DbCatalogFile>.toCatalogFiles(): List<CatalogFile> =
        map { it.toCatalogFile() }
            .distinctBy(CatalogFile::path)
            .sortedWith(NaturalSort.by { it.path })

    private fun List<DbCatalogFile>.deduplicateIn(root: String): List<DbCatalogFile> =
        groupBy { file -> file.bookId?.let { "book:$it" } ?: "path:${file.path}" }
            .values
            .map { duplicates -> duplicates.preferredFor(root) }

    private fun List<DbCatalogFile>.preferredFor(root: String): DbCatalogFile =
        maxWith(
            compareBy<DbCatalogFile> { it.path.directoryAfter(root) != null }
                .thenBy { it.modifiedAtMillis ?: 0L }
                .thenBy { it.fileId ?: 0L },
        )

    private fun List<CatalogFile>.lastReadFile(): CatalogFile? =
        filter { it.lastOpenedAtMillis != null }
            .maxByOrNull { it.lastOpenedAtMillis ?: 0L }

    private suspend fun loadFromFolders(device: PocketBookDevice): DeviceCatalog =
        DeviceCatalog(
            books = loadGroupedFiles(device, BooksRoot),
            programming = loadGroupedFiles(device, ProgrammingRoot),
            manga = loadMangaSeries(device, MangaRoot),
            scannedAtMillis = System.currentTimeMillis(),
            isLoading = false,
            errorMessage = null,
        )

    private suspend fun loadGroupedFiles(
        device: PocketBookDevice,
        root: String,
    ): List<CatalogGroup> {
        val rootEntries = ftpGateway.listEntries(device, root).getOrThrow()
        val rootFiles = rootEntries
            .filterNot(FtpEntry::isDirectory)
            .filter { it.isKnownBookFile() }
            .map { it.toCatalogFile() }

        val folderGroups = rootEntries
            .filter(FtpEntry::isDirectory)
            .mapNotNull { group ->
                val files = ftpGateway.listEntries(device, group.path)
                    .getOrDefault(emptyList())
                    .filterNot(FtpEntry::isDirectory)
                    .filter { it.isKnownBookFile() }
                    .map { it.toCatalogFile() }
                    .sortedWith(NaturalSort.by { it.path })

                if (files.isEmpty()) {
                    null
                } else {
                    CatalogGroup(
                        name = group.name,
                        path = group.path,
                        files = files,
                    )
                }
            }

        val fallbackGroup = if (rootFiles.isEmpty()) {
            null
        } else {
            CatalogGroup(
                name = if (root == ProgrammingRoot) "Untagged" else "Unknown Author",
                path = root,
                files = rootFiles.sortedWith(NaturalSort.by { it.path }),
            )
        }

        return (listOfNotNull(fallbackGroup) + folderGroups)
            .sortedWith(NaturalSort.by { it.name })
    }

    private suspend fun loadMangaSeries(
        device: PocketBookDevice,
        root: String,
    ): List<MangaSeriesGroup> {
        return ftpGateway.listEntries(device, root)
            .getOrThrow()
            .filter(FtpEntry::isDirectory)
            .mapNotNull { series ->
                val files = ftpGateway.listEntries(device, series.path)
                    .getOrDefault(emptyList())
                    .filterNot(FtpEntry::isDirectory)
                    .filter { it.name.contentExtension() in MangaArchiveExtensions }
                    .map { it.toCatalogFile() }
                    .sortedWith(NaturalSort.by { it.path })

                if (files.isEmpty()) {
                    null
                } else {
                    MangaSeriesGroup(
                        name = series.name,
                        path = series.path,
                        latestFile = files.lastOrNull(),
                        lastReadFile = null,
                        files = files,
                    )
                }
            }
            .sortedWith(NaturalSort.by { it.name })
    }

    private fun FtpEntry.toCatalogFile(): CatalogFile =
        CatalogFile(
            name = name,
            path = path,
            size = size,
            modifiedAtMillis = modifiedAtMillis,
        )

    private fun FtpEntry.isKnownBookFile(): Boolean =
        name.contentExtension() in AllSupportedExtensions

    private fun DbCatalogFile.toCatalogFile(): CatalogFile =
        CatalogFile(
            name = name,
            path = path,
            size = size,
            modifiedAtMillis = modifiedAtMillis,
            title = title?.takeIf { it != name.bookTitleWithoutExtension() },
            authors = authors,
            readProgressPercent = readProgressPercent,
            completed = completed,
            lastOpenedAtMillis = lastOpenedAtMillis,
            currentPage = cpage,
            totalPages = npage,
            series = series,
        )

    private data class DbCatalogFile(
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
        val series: String?,
    )

    private companion object {
        const val CacheDirectory = "pocketbook-catalog"
        const val RemoteDatabaseDirectory = "system/explorer-3"
        const val DatabaseName = "explorer-3.db"
        const val BooksRoot = "Books"
        const val ProgrammingRoot = "Programming"
        const val MangaRoot = "Manga"

        val OptionalDatabaseFiles = listOf("$DatabaseName-wal", "$DatabaseName-shm")
        val DatabaseFiles = listOf(DatabaseName) + OptionalDatabaseFiles

        val CatalogQuery = """
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
    }
}

private fun normalizePath(absolutePath: String): String? =
    absolutePath
        .removePrefix(PocketBookStoragePrefix)
        .trim('/')
        .takeIf { it.isNotBlank() }

private fun String.isUnder(root: String): Boolean =
    this == root || startsWith("$root/")

private fun String.directoryAfter(root: String): String? {
    if (!startsWith("$root/")) return null
    val relativePath = removePrefix("$root/")
    if ('/' !in relativePath) return null
    return relativePath.substringBefore('/').takeIf { it.isNotBlank() }
}

private fun String?.splitAuthors(): List<String> =
    orEmpty()
        .split(',')
        .mapNotNull { it.cleanText() }

private fun String.cleanText(): String? =
    trim()
        .replace(Regex("\\s+"), " ")
        .takeIf { it.isNotBlank() }

private fun readProgressPercent(
    completed: Boolean,
    cpage: Int?,
    npage: Int?,
): Int? {
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

package com.cybercat.ebooksender.data.catalog

import com.cybercat.ebooksender.domain.AllSupportedExtensions
import com.cybercat.ebooksender.domain.NaturalSort
import com.cybercat.ebooksender.domain.bookTitleWithoutExtension
import com.cybercat.ebooksender.domain.contentExtension
import com.cybercat.ebooksender.model.AppSettings
import com.cybercat.ebooksender.model.CatalogFallbackNames
import com.cybercat.ebooksender.model.CatalogFile
import com.cybercat.ebooksender.model.CatalogGroup
import com.cybercat.ebooksender.model.DeviceCatalog
import com.cybercat.ebooksender.model.MangaSeriesGroup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogTreeBuilder @Inject constructor() {
    internal fun buildFromDatabaseFiles(
        files: List<DbCatalogFile>,
        settings: AppSettings,
        scannedAtMillis: Long
    ): DeviceCatalog {
        val supportedFiles = files.filter { it.path.contentExtension() in AllSupportedExtensions }
        return DeviceCatalog(
            books = supportedFiles.toBookGroups(settings),
            documents = supportedFiles.toDocumentsGroups(settings),
            manga = supportedFiles.toMangaGroups(settings),
            scannedAtMillis = scannedAtMillis,
            isLoading = false,
            errorMessage = null
        )
    }

    private fun List<DbCatalogFile>.toBookGroups(settings: AppSettings): List<CatalogGroup> =
        toCatalogGroups(
            root = settings.booksFolderName,
            fallbackName = CatalogFallbackNames.UNKNOWN_AUTHOR,
            groupName = { file ->
                file.path.directoryAfter(settings.booksFolderName) ?: file.authors.firstOrNull()
            }
        )

    private fun List<DbCatalogFile>.toDocumentsGroups(settings: AppSettings): List<CatalogGroup> =
        toCatalogGroups(
            root = settings.documentsFolderName,
            fallbackName = CatalogFallbackNames.UNTAGGED_DOCUMENTS
        )

    private fun List<DbCatalogFile>.toMangaGroups(settings: AppSettings): List<MangaSeriesGroup> =
        groupCatalogFiles(
            root = settings.mangaFolderName,
            fallbackName = CatalogFallbackNames.UNSORTED_MANGA,
            filePath = DbCatalogFile::path,
            deduplicate = { files -> files.deduplicateIn(settings.mangaFolderName) },
            toCatalogFiles = { files -> files.toCatalogFiles() },
            createGroup = { name, path, files ->
                MangaSeriesGroup(
                    name = name,
                    path = path,
                    latestFile = files.lastOrNull(),
                    lastReadFile = files.lastReadFile(),
                    files = files
                )
            },
            groupFiles = MangaSeriesGroup::files,
            groupSortName = MangaSeriesGroup::name
        )

    private fun List<DbCatalogFile>.toCatalogGroups(
        root: String,
        fallbackName: String,
        groupName: (DbCatalogFile) -> String? = { file -> file.path.directoryAfter(root) }
    ): List<CatalogGroup> = groupCatalogFiles(
        root = root,
        fallbackName = fallbackName,
        filePath = DbCatalogFile::path,
        deduplicate = { files -> files.deduplicateIn(root) },
        groupName = groupName,
        toCatalogFiles = { files -> files.toCatalogFiles() },
        createGroup = { name, path, files ->
            CatalogGroup(
                name = name,
                path = path,
                files = files
            )
        },
        groupFiles = CatalogGroup::files,
        groupSortName = CatalogGroup::name
    )

    private fun List<DbCatalogFile>.toCatalogFiles(): List<CatalogFile> = map { it.toCatalogFile() }
        .distinctBy(CatalogFile::path)
        .sortedWith(NaturalSort.by { it.path })

    private fun List<DbCatalogFile>.deduplicateIn(root: String): List<DbCatalogFile> =
        groupBy { file -> file.bookId?.let { "book:$it" } ?: "path:${file.path}" }
            .values
            .map { duplicates -> duplicates.preferredFor(root) }

    private fun List<DbCatalogFile>.preferredFor(root: String): DbCatalogFile = maxWith(
        compareBy<DbCatalogFile> { it.path.directoryAfter(root) != null }
            .thenBy { it.modifiedAtMillis ?: 0L }
            .thenBy { it.fileId ?: 0L }
    )

    private fun DbCatalogFile.toCatalogFile(): CatalogFile = CatalogFile(
        name = name,
        path = path,
        size = size,
        modifiedAtMillis = modifiedAtMillis,
        title = title?.takeIf { it.isNotBlank() && it != name.bookTitleWithoutExtension() },
        authors = authors,
        readingProgressAvailable = true,
        readProgressPercent = readProgressPercent,
        completed = completed,
        lastOpenedAtMillis = lastOpenedAtMillis,
        currentPage = cpage,
        totalPages = npage,
        series = series
    )
}

internal fun List<CatalogFile>.lastReadFile(): CatalogFile? =
    filter { it.lastOpenedAtMillis != null }
        .maxByOrNull { it.lastOpenedAtMillis ?: 0L }

internal fun String.isUnder(root: String): Boolean = this == root || startsWith("$root/")

internal fun String.isUnderFolder(folder: String): Boolean {
    val normalizedFolder = folder.trim('/')
    return this != normalizedFolder && startsWith("$normalizedFolder/")
}

internal fun String.directoryAfter(root: String): String? {
    if (!startsWith("$root/")) return null
    val relativePath = removePrefix("$root/")
    if ('/' !in relativePath) return null
    return relativePath.substringBefore('/').takeIf { it.isNotBlank() }
}

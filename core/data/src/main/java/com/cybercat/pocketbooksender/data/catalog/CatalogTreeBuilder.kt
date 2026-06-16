package com.cybercat.pocketbooksender.data.catalog

import com.cybercat.pocketbooksender.domain.AllSupportedExtensions
import com.cybercat.pocketbooksender.domain.NaturalSort
import com.cybercat.pocketbooksender.domain.bookTitleWithoutExtension
import com.cybercat.pocketbooksender.domain.contentExtension
import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.CatalogFile
import com.cybercat.pocketbooksender.model.CatalogGroup
import com.cybercat.pocketbooksender.model.DeviceCatalog
import com.cybercat.pocketbooksender.model.MangaSeriesGroup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogTreeBuilder @Inject constructor() {
    internal fun buildFromDatabaseFiles(
        files: List<DbCatalogFile>,
        settings: AppSettings,
        scannedAtMillis: Long,
    ): DeviceCatalog {
        val supportedFiles = files.filter { it.path.contentExtension() in AllSupportedExtensions }
        return DeviceCatalog(
            books = supportedFiles.toBookGroups(settings),
            documents = supportedFiles.toDocumentsGroups(settings),
            manga = supportedFiles.toMangaGroups(settings),
            scannedAtMillis = scannedAtMillis,
            isLoading = false,
            errorMessage = null,
        )
    }

    private fun List<DbCatalogFile>.toBookGroups(settings: AppSettings): List<CatalogGroup> =
        filter { it.path.isUnder(settings.booksFolderName) }
            .deduplicateIn(settings.booksFolderName)
            .groupBy { file ->
                file.path.directoryAfter(settings.booksFolderName)
                    ?: file.authors.firstOrNull()
                    ?: "Unknown Author"
            }
            .map { (name, files) ->
                CatalogGroup(
                    name = name,
                    path = "${settings.booksFolderName}/$name",
                    files = files.toCatalogFiles(),
                )
            }
            .filter { it.files.isNotEmpty() }
            .sortedWith(NaturalSort.by { it.name })

    private fun List<DbCatalogFile>.toDocumentsGroups(settings: AppSettings): List<CatalogGroup> =
        filter { it.path.isUnder(settings.documentsFolderName) }
            .deduplicateIn(settings.documentsFolderName)
            .groupBy { file ->
                file.path.directoryAfter(settings.documentsFolderName) ?: "Untagged"
            }
            .map { (name, files) ->
                CatalogGroup(
                    name = name,
                    path = "${settings.documentsFolderName}/$name",
                    files = files.toCatalogFiles(),
                )
            }
            .filter { it.files.isNotEmpty() }
            .sortedWith(NaturalSort.by { it.name })

    private fun List<DbCatalogFile>.toMangaGroups(settings: AppSettings): List<MangaSeriesGroup> =
        filter { it.path.isUnder(settings.mangaFolderName) }
            .deduplicateIn(settings.mangaFolderName)
            .groupBy { file ->
                file.path.directoryAfter(settings.mangaFolderName) ?: "Unsorted Manga"
            }
            .map { (name, files) ->
                val catalogFiles = files.toCatalogFiles()
                MangaSeriesGroup(
                    name = name,
                    path = "${settings.mangaFolderName}/$name",
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
}

internal fun List<CatalogFile>.lastReadFile(): CatalogFile? =
    filter { it.lastOpenedAtMillis != null }
        .maxByOrNull { it.lastOpenedAtMillis ?: 0L }

internal fun String.isUnder(root: String): Boolean =
    this == root || startsWith("$root/")

internal fun String.isUnderFolder(folder: String): Boolean {
    val normalizedFolder = folder.trim('/')
    return this != normalizedFolder && startsWith("$normalizedFolder/")
}

private fun String.directoryAfter(root: String): String? {
    if (!startsWith("$root/")) return null
    val relativePath = removePrefix("$root/")
    if ('/' !in relativePath) return null
    return relativePath.substringBefore('/').takeIf { it.isNotBlank() }
}

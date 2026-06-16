package com.cybercat.pocketbooksender.data.catalog

import com.cybercat.pocketbooksender.data.ftp.FtpEntry
import com.cybercat.pocketbooksender.data.ftp.FtpGateway
import com.cybercat.pocketbooksender.domain.AllSupportedExtensions
import com.cybercat.pocketbooksender.domain.MangaArchiveExtensions
import com.cybercat.pocketbooksender.domain.NaturalSort
import com.cybercat.pocketbooksender.domain.contentExtension
import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.CatalogFile
import com.cybercat.pocketbooksender.model.CatalogGroup
import com.cybercat.pocketbooksender.model.DeviceCatalog
import com.cybercat.pocketbooksender.model.MangaSeriesGroup
import com.cybercat.pocketbooksender.model.PocketBookDevice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogFolderScanner @Inject constructor(
    private val ftpGateway: FtpGateway,
) {
    suspend fun scan(
        device: PocketBookDevice,
        settings: AppSettings,
        scannedAtMillis: Long,
    ): DeviceCatalog =
        DeviceCatalog(
            books = loadGroupedFiles(device, settings.booksFolderName, settings),
            documents = loadGroupedFiles(device, settings.documentsFolderName, settings),
            manga = loadMangaSeries(device, settings.mangaFolderName),
            scannedAtMillis = scannedAtMillis,
            isLoading = false,
            errorMessage = null,
        )

    private suspend fun loadGroupedFiles(
        device: PocketBookDevice,
        root: String,
        settings: AppSettings,
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
                name = if (root == settings.documentsFolderName) "Untagged" else "Unknown Author",
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
    ): List<MangaSeriesGroup> =
        ftpGateway.listEntries(device, root)
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

    private fun FtpEntry.toCatalogFile(): CatalogFile =
        CatalogFile(
            name = name,
            path = path,
            size = size,
            modifiedAtMillis = modifiedAtMillis,
        )

    private fun FtpEntry.isKnownBookFile(): Boolean =
        name.contentExtension() in AllSupportedExtensions
}

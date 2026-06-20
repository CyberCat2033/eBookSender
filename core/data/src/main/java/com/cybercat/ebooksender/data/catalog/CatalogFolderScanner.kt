package com.cybercat.ebooksender.data.catalog

import com.cybercat.ebooksender.data.ftp.FtpEntry
import com.cybercat.ebooksender.data.ftp.FtpGateway
import com.cybercat.ebooksender.domain.AllSupportedExtensions
import com.cybercat.ebooksender.domain.MangaArchiveExtensions
import com.cybercat.ebooksender.domain.NaturalSort
import com.cybercat.ebooksender.domain.contentExtension
import com.cybercat.ebooksender.model.AppSettings
import com.cybercat.ebooksender.model.CatalogFallbackNames
import com.cybercat.ebooksender.model.CatalogFile
import com.cybercat.ebooksender.model.CatalogGroup
import com.cybercat.ebooksender.model.DeviceCatalog
import com.cybercat.ebooksender.model.MangaSeriesGroup
import com.cybercat.ebooksender.model.RemoteDevice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogFolderScanner @Inject constructor(private val ftpGateway: FtpGateway) :
    DeviceCatalogSource {
    override suspend fun load(
        device: RemoteDevice,
        settings: AppSettings,
        scannedAtMillis: Long
    ): DeviceCatalog = DeviceCatalog(
        books = loadGroupedFiles(device, settings.booksFolderName, settings),
        documents = loadGroupedFiles(device, settings.documentsFolderName, settings),
        manga = loadMangaSeries(device, settings.mangaFolderName),
        scannedAtMillis = scannedAtMillis,
        isLoading = false,
        errorMessage = null
    )

    private suspend fun loadGroupedFiles(
        device: RemoteDevice,
        root: String,
        settings: AppSettings
    ): List<CatalogGroup> {
        val fallbackName = if (root == settings.documentsFolderName) {
            CatalogFallbackNames.UNTAGGED_DOCUMENTS
        } else {
            CatalogFallbackNames.UNKNOWN_AUTHOR
        }
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
                files.toCatalogGroupOrNull(name = group.name, path = group.path)
            }

        return buildCatalogGroupsWithFallback(
            root = root,
            rootFiles = rootFiles,
            fallbackName = fallbackName,
            folderGroups = folderGroups
        )
    }

    private suspend fun loadMangaSeries(
        device: RemoteDevice,
        root: String
    ): List<MangaSeriesGroup> = ftpGateway.listEntries(device, root)
        .getOrThrow()
        .filter(FtpEntry::isDirectory)
        .mapNotNull { series ->
            val files = ftpGateway.listEntries(device, series.path)
                .getOrDefault(emptyList())
                .filterNot(FtpEntry::isDirectory)
                .filter { it.name.contentExtension() in MangaArchiveExtensions }
                .map { it.toCatalogFile() }
            files.toMangaSeriesGroupOrNull(name = series.name, path = series.path)
        }
        .sortedWith(NaturalSort.by { it.name })

    private fun FtpEntry.toCatalogFile(): CatalogFile = CatalogFile(
        name = name,
        path = path,
        size = size,
        modifiedAtMillis = modifiedAtMillis
    )

    private fun FtpEntry.isKnownBookFile(): Boolean =
        name.contentExtension() in AllSupportedExtensions
}

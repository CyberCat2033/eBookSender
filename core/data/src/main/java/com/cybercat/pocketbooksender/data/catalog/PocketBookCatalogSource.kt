package com.cybercat.pocketbooksender.data.catalog

import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.DeviceCatalog
import com.cybercat.pocketbooksender.model.RemoteDevice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PocketBookCatalogSource @Inject constructor(
    private val databaseReader: PocketBookDatabaseReader,
    private val catalogTreeBuilder: CatalogTreeBuilder
) : DeviceCatalogSource {
    override suspend fun load(
        device: RemoteDevice,
        settings: AppSettings,
        scannedAtMillis: Long
    ): DeviceCatalog = catalogTreeBuilder.buildFromDatabaseFiles(
        files = databaseReader.readCatalogFiles(device, settings),
        settings = settings,
        scannedAtMillis = scannedAtMillis
    )
}

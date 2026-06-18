package com.cybercat.ebooksender.data.catalog

import com.cybercat.ebooksender.model.AppSettings
import com.cybercat.ebooksender.model.DeviceCatalog
import com.cybercat.ebooksender.model.RemoteDevice
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

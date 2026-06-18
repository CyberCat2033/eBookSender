package com.cybercat.ebooksender.data.catalog

import com.cybercat.ebooksender.model.AppSettings
import com.cybercat.ebooksender.model.DeviceCatalog
import com.cybercat.ebooksender.model.RemoteDevice

interface DeviceCatalogSource {
    suspend fun load(
        device: RemoteDevice,
        settings: AppSettings,
        scannedAtMillis: Long
    ): DeviceCatalog
}

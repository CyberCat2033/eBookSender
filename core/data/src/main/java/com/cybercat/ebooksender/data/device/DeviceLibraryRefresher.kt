package com.cybercat.ebooksender.data.device

import com.cybercat.ebooksender.data.pocketbook.PocketBookRescanCoordinator
import com.cybercat.ebooksender.model.DeviceProfile
import com.cybercat.ebooksender.model.RemoteDevice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class DeviceLibraryRefresher(
    private val pocketBookRescanCoordinator: PocketBookRescanCoordinator?,
    @Suppress("UNUSED_PARAMETER") dummy: Boolean
) {
    @Inject
    constructor(
        pocketBookRescanCoordinator: PocketBookRescanCoordinator
    ) : this(pocketBookRescanCoordinator, true)

    open suspend fun refreshAndWait(device: RemoteDevice): Result<Unit> =
        if (device.profile == DeviceProfile.PocketBook && device.supportsRescan) {
            pocketBookRescanCoordinator?.requestRescanAndWait(device) ?: Result.success(Unit)
        } else {
            Result.success(Unit)
        }
}

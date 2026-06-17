package com.cybercat.pocketbooksender.data.device

import com.cybercat.pocketbooksender.data.pocketbook.PocketBookRescanCoordinator
import com.cybercat.pocketbooksender.model.DeviceProfile
import com.cybercat.pocketbooksender.model.RemoteDevice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceLibraryRefresher @Inject constructor(
    private val pocketBookRescanCoordinator: PocketBookRescanCoordinator
) {
    suspend fun refreshAndWait(device: RemoteDevice): Result<Unit> =
        if (device.profile == DeviceProfile.PocketBook && device.supportsRescan) {
            pocketBookRescanCoordinator.requestRescanAndWait(device)
        } else {
            Result.success(Unit)
        }
}

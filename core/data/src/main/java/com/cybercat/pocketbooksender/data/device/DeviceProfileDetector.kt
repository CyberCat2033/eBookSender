package com.cybercat.pocketbooksender.data.device

import com.cybercat.pocketbooksender.data.ftp.FtpGateway
import com.cybercat.pocketbooksender.data.pocketbook.PocketBookLibraryPaths
import com.cybercat.pocketbooksender.model.DEFAULT_FTP_RELATIVE_ROOT_PATH
import com.cybercat.pocketbooksender.model.DeviceProfile
import com.cybercat.pocketbooksender.model.RemoteDevice
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class DeviceProfileDetector @Inject constructor(private val ftpGateway: FtpGateway) {
    suspend fun detect(device: RemoteDevice): RemoteDevice {
        val profile = runCatching { detectProfile(device) }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                DeviceProfile.GenericFtp
            }
        return device.withProfile(profile)
    }

    private suspend fun detectProfile(device: RemoteDevice): DeviceProfile =
        if (hasPocketBookDatabase(device)) {
            DeviceProfile.PocketBook
        } else {
            DeviceProfile.GenericFtp
        }

    private suspend fun hasPocketBookDatabase(device: RemoteDevice): Boolean {
        val mountRootDevice = device.copy(relativeRootPath = DEFAULT_FTP_RELATIVE_ROOT_PATH)
        return ftpGateway.listEntries(
            device = mountRootDevice,
            remoteRelativePath = PocketBookLibraryPaths.REMOTE_DATABASE_DIRECTORY
        ).getOrDefault(emptyList())
            .any { entry ->
                !entry.isDirectory && entry.name == PocketBookLibraryPaths.DATABASE_NAME
            }
    }
}

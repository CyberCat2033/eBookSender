package com.cybercat.ebooksender.data.update

import com.cybercat.ebooksender.data.pocketbook.PocketBookControlClient
import com.cybercat.ebooksender.model.RemoteDevice
import com.cybercat.ebooksender.model.update.PocketBookServerVersionInfo
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

internal class PocketBookServerVersionReader(private val controlClient: PocketBookControlClient) {
    suspend fun readInstalledVersionOrNull(device: RemoteDevice): PocketBookServerVersionInfo? =
        controlClient.readVersion(device).getOrNull()
            ?.takeIf { version ->
                version.appName == PocketBookServerUpdateConfig.POCKETBOOK_SERVER_APP_NAME &&
                    version.versionName.isNotBlank() &&
                    version.versionCode > 0L
            }

    suspend fun waitForInstalledVersion(
        device: RemoteDevice,
        update: AvailablePocketBookServerUpdate
    ): PocketBookServerVersionInfo? {
        repeat(PocketBookServerUpdateConfig.UPDATE_VERSION_POLL_ATTEMPTS) {
            coroutineContext.ensureActive()
            delay(PocketBookServerUpdateConfig.UPDATE_VERSION_POLL_DELAY_MS)
            val version = readInstalledVersionOrNull(device)
            if (version != null && update.matchesInstalledVersion(version)) {
                return version
            }
        }
        return null
    }

    private fun AvailablePocketBookServerUpdate.matchesInstalledVersion(
        installedVersion: PocketBookServerVersionInfo
    ): Boolean =
        installedVersion.appName == PocketBookServerUpdateConfig.POCKETBOOK_SERVER_APP_NAME &&
            installedVersion.versionCode == versionCode &&
            installedVersion.versionName == versionName &&
            (
                buildId.isNullOrBlank() ||
                    installedVersion.buildId.isNullOrBlank() ||
                    installedVersion.buildId == buildId
                )
}

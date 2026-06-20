package com.cybercat.ebooksender.data.update

import com.cybercat.ebooksender.model.update.PocketBookServerUpdateArtifact
import com.cybercat.ebooksender.model.update.PocketBookServerUpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Test

class PocketBookServerUpdateInstallerTest {

    @Test
    fun stagedLauncherRemotePath_keepsLauncherFileNameWithoutVersionPrefix() {
        val update = AvailablePocketBookServerUpdate(
            manifest = PocketBookServerUpdateManifest(
                appName = "pb-ftp",
                versionName = "1.0.3",
                versionCode = 22,
                releasedAt = "2026-06-20T14:30:29Z",
                artifacts = emptyList()
            ),
            launcherArtifact = PocketBookServerUpdateArtifact(
                type = "launcher",
                fileName = "pb-ftp.app",
                installPath = "/mnt/ext1/applications/pb-ftp.app",
                url = "https://example.com/pb-ftp.app",
                sha256 = "bf84abdb008c4521fac0dfda50e339617247aad17f9dfdc582b64df9fbb4db75"
            )
        )

        assertEquals(
            "applications/.pb-ftp-update/pb-ftp.app",
            update.stagedLauncherRemotePath()
        )
    }
}

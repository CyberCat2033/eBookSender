package com.cybercat.ebooksender.data.update

import com.cybercat.ebooksender.model.RemoteDevice
import com.cybercat.ebooksender.model.update.PocketBookServerUpdateArtifact
import com.cybercat.ebooksender.model.update.PocketBookServerUpdateManifest
import com.cybercat.ebooksender.model.update.PocketBookServerVersionInfo
import java.net.URL

internal class PocketBookServerUpdateResolver(
    private val manifestLoader: UpdateManifestLoader,
    private val versionReader: PocketBookServerVersionReader
) {
    suspend fun findAvailableUpdate(device: RemoteDevice): PocketBookServerUpdateCheckResult {
        val installedVersion = versionReader.readInstalledVersionOrNull(device)
        val manifest = fetchManifest()
        validateManifest(manifest)
        val update = buildAvailableUpdate(manifest)

        return when {
            installedVersion == null -> {
                PocketBookServerUpdateCheckResult(
                    installedVersion = null,
                    availableUpdate = update,
                    status = PocketBookServerUpdateStatus.InstalledVersionUnknown(update)
                )
            }

            !manifest.isNewerThan(installedVersion) -> {
                PocketBookServerUpdateCheckResult(
                    installedVersion = installedVersion,
                    availableUpdate = null,
                    status = PocketBookServerUpdateStatus.NoUpdateAvailable
                )
            }

            else -> {
                PocketBookServerUpdateCheckResult(
                    installedVersion = installedVersion,
                    availableUpdate = update,
                    status = PocketBookServerUpdateStatus.UpdateAvailable(update)
                )
            }
        }
    }

    private fun fetchManifest(): PocketBookServerUpdateManifest = manifestLoader.load(
        UpdateManifestRequest(
            url = PocketBookServerUpdateConfig.UPDATE_MANIFEST_URL,
            serializer = PocketBookServerUpdateManifest.serializer(),
            userAgent = PocketBookServerUpdateConfig.USER_AGENT,
            connectTimeoutMs = PocketBookServerUpdateConfig.CONNECT_TIMEOUT_MS,
            readTimeoutMs = PocketBookServerUpdateConfig.READ_TIMEOUT_MS,
            maxBytes = PocketBookServerUpdateConfig.MAX_MANIFEST_BYTES,
            invalidManifestException = { cause ->
                PocketBookServerUpdateException(
                    PocketBookServerUpdateErrorReason.InvalidManifest,
                    cause
                )
            },
            networkException = { cause ->
                PocketBookServerUpdateException(
                    PocketBookServerUpdateErrorReason.Network,
                    cause
                )
            }
        )
    )

    private fun validateManifest(manifest: PocketBookServerUpdateManifest) {
        if (manifest.schemaVersion != 1 ||
            manifest.appName != PocketBookServerUpdateConfig.POCKETBOOK_SERVER_APP_NAME ||
            manifest.versionName.isBlank() ||
            manifest.versionCode <= 0L ||
            manifest.artifacts.isEmpty()
        ) {
            throw PocketBookServerUpdateException(PocketBookServerUpdateErrorReason.InvalidManifest)
        }
    }

    private fun buildAvailableUpdate(
        manifest: PocketBookServerUpdateManifest
    ): AvailablePocketBookServerUpdate {
        val launcher = manifest.artifacts.firstOrNull {
            it.type == PocketBookServerUpdateConfig.LAUNCHER_ARTIFACT_TYPE
        }
        if (launcher == null) {
            throw PocketBookServerUpdateException(
                PocketBookServerUpdateErrorReason.MissingArtifacts
            )
        }
        validateArtifact(
            artifact = launcher,
            expectedInstallPath = PocketBookServerUpdateConfig.EXPECTED_LAUNCHER_INSTALL_PATH,
            expectedExtension = ".app"
        )
        return AvailablePocketBookServerUpdate(
            manifest = manifest,
            launcherArtifact = launcher
        )
    }

    private fun validateArtifact(
        artifact: PocketBookServerUpdateArtifact,
        expectedInstallPath: String,
        expectedExtension: String
    ) {
        val url = URL(artifact.url)
        if (url.protocol != "https" ||
            artifact.fileName.isBlank() ||
            !artifact.fileName.endsWith(expectedExtension, ignoreCase = true) ||
            artifact.installPath != expectedInstallPath ||
            !PocketBookServerUpdateConfig.SHA256_PATTERN.matches(artifact.sha256)
        ) {
            throw PocketBookServerUpdateException(PocketBookServerUpdateErrorReason.InvalidManifest)
        }
    }

    private fun PocketBookServerUpdateManifest.isNewerThan(
        installedVersion: PocketBookServerVersionInfo
    ): Boolean = when {
        versionCode > installedVersion.versionCode -> true

        versionCode < installedVersion.versionCode -> false

        else -> buildId.isMeaningfulBuildId() &&
            installedVersion.buildId.isMeaningfulBuildId() &&
            buildId != installedVersion.buildId
    }

    private fun String?.isMeaningfulBuildId(): Boolean = !isNullOrBlank()
}

internal data class PocketBookServerUpdateCheckResult(
    val installedVersion: PocketBookServerVersionInfo?,
    val availableUpdate: AvailablePocketBookServerUpdate?,
    val status: PocketBookServerUpdateStatus
)

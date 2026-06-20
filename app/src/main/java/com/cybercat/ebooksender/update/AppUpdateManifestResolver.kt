package com.cybercat.ebooksender.update

import android.content.Context
import android.os.Build
import com.cybercat.ebooksender.BuildConfig
import com.cybercat.ebooksender.data.update.AppUpdateErrorReason
import com.cybercat.ebooksender.data.update.AvailableAppUpdate
import com.cybercat.ebooksender.data.update.UpdateManifestLoader
import com.cybercat.ebooksender.data.update.UpdateManifestRequest
import com.cybercat.ebooksender.model.update.AppUpdateArtifact
import com.cybercat.ebooksender.model.update.AppUpdateManifest
import java.net.URL

internal class AppUpdateManifestResolver(
    private val context: Context,
    private val manifestLoader: UpdateManifestLoader
) {
    fun loadAvailableUpdate(currentVersionCode: Long): AvailableAppUpdate? {
        val manifest = fetchManifest()
        validateManifest(manifest)
        if (manifest.versionCode <= currentVersionCode) return null

        val artifact = selectArtifact(manifest)
            ?: throw AppUpdateException(AppUpdateErrorReason.NoCompatibleArtifact)
        validateArtifact(artifact)
        return AvailableAppUpdate(manifest, artifact)
    }

    private fun fetchManifest(): AppUpdateManifest = manifestLoader.load(
        UpdateManifestRequest(
            url = BuildConfig.UPDATE_MANIFEST_URL,
            serializer = AppUpdateManifest.serializer(),
            userAgent = AppUpdateConfig.USER_AGENT,
            connectTimeoutMs = AppUpdateConfig.CONNECT_TIMEOUT_MS,
            readTimeoutMs = AppUpdateConfig.READ_TIMEOUT_MS,
            maxBytes = AppUpdateConfig.MAX_MANIFEST_BYTES,
            invalidManifestException = { cause ->
                AppUpdateException(AppUpdateErrorReason.InvalidManifest, cause)
            },
            networkException = { cause ->
                AppUpdateException(AppUpdateErrorReason.Network, cause)
            }
        )
    )

    private fun validateManifest(manifest: AppUpdateManifest) {
        if (manifest.schemaVersion != 1 ||
            manifest.packageName != context.packageName ||
            manifest.versionName.isBlank() ||
            manifest.versionCode <= 0 ||
            manifest.minSdk > Build.VERSION.SDK_INT ||
            manifest.artifacts.isEmpty()
        ) {
            throw AppUpdateException(AppUpdateErrorReason.InvalidManifest)
        }
    }

    private fun validateArtifact(artifact: AppUpdateArtifact) {
        val url = URL(artifact.url)
        if (url.protocol != "https" ||
            artifact.fileName.isBlank() ||
            !artifact.fileName.endsWith(".apk", ignoreCase = true) ||
            !AppUpdateConfig.SHA256_PATTERN.matches(artifact.sha256)
        ) {
            throw AppUpdateException(AppUpdateErrorReason.InvalidManifest)
        }
    }

    private fun selectArtifact(manifest: AppUpdateManifest): AppUpdateArtifact? {
        val artifactsByAbi = manifest.artifacts.associateBy { it.abi }
        return Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi -> artifactsByAbi[abi] }
            ?: artifactsByAbi[AppUpdateConfig.UNIVERSAL_ABI]
    }
}

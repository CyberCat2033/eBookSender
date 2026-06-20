package com.cybercat.ebooksender.data.update

import android.content.Context
import com.cybercat.ebooksender.model.update.PocketBookServerUpdateArtifact
import java.io.File

internal class PocketBookServerArtifactRepository(
    private val context: Context,
    private val artifactDownloader: UpdateArtifactDownloader
) {
    private val artifactLoader = UpdateVerifiedArtifactLoader(artifactDownloader)

    suspend fun getVerifiedArtifactFile(
        artifact: PocketBookServerUpdateArtifact,
        onDownloadProgress: (Long) -> Unit
    ): File {
        val target = cachedArtifactFile(artifact)
        return artifactLoader.getVerifiedArtifact(
            UpdateVerifiedArtifactRequest(
                url = artifact.url,
                target = target,
                sha256 = artifact.sha256,
                userAgent = PocketBookServerUpdateConfig.USER_AGENT,
                connectTimeoutMs = PocketBookServerUpdateConfig.CONNECT_TIMEOUT_MS,
                readTimeoutMs = PocketBookServerUpdateConfig.DOWNLOAD_READ_TIMEOUT_MS,
                downloadFailedException = { cause ->
                    PocketBookServerUpdateException(
                        PocketBookServerUpdateErrorReason.DownloadFailed,
                        cause
                    )
                },
                checksumMismatchException = {
                    PocketBookServerUpdateException(
                        PocketBookServerUpdateErrorReason.ChecksumMismatch
                    )
                },
                onProgress = { bytesRead, _ -> onDownloadProgress(bytesRead) }
            )
        )
    }

    private fun cachedArtifactFile(artifact: PocketBookServerUpdateArtifact): File {
        val safeName = artifact.fileName.toSafeUpdateFileName()
        return File(updateCacheDir(), safeName)
    }

    private fun updateCacheDir(): File =
        File(context.cacheDir, PocketBookServerUpdateConfig.UPDATE_CACHE_DIR)
}

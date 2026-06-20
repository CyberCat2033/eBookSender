package com.cybercat.ebooksender.update

import android.content.Context
import com.cybercat.ebooksender.data.update.AppUpdateErrorReason
import com.cybercat.ebooksender.data.update.AvailableAppUpdate
import com.cybercat.ebooksender.data.update.UpdateArtifactDownloader
import com.cybercat.ebooksender.data.update.UpdateVerifiedArtifactLoader
import com.cybercat.ebooksender.data.update.UpdateVerifiedArtifactRequest
import com.cybercat.ebooksender.data.update.toSafeUpdateFileName
import java.io.File

internal class AppUpdateApkRepository(
    private val context: Context,
    private val artifactDownloader: UpdateArtifactDownloader
) {
    private val artifactLoader = UpdateVerifiedArtifactLoader(artifactDownloader)

    suspend fun getVerifiedApk(
        update: AvailableAppUpdate,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit
    ): File {
        val apk = cachedApkFile(update)
        return artifactLoader.getVerifiedArtifact(
            UpdateVerifiedArtifactRequest(
                url = update.artifact.url,
                target = apk,
                sha256 = update.artifact.sha256,
                userAgent = AppUpdateConfig.USER_AGENT,
                connectTimeoutMs = AppUpdateConfig.CONNECT_TIMEOUT_MS,
                readTimeoutMs = AppUpdateConfig.DOWNLOAD_READ_TIMEOUT_MS,
                accept = APK_ACCEPT_HEADER,
                useCaches = true,
                downloadFailedException = { cause ->
                    AppUpdateException(AppUpdateErrorReason.DownloadFailed, cause)
                },
                checksumMismatchException = {
                    AppUpdateException(AppUpdateErrorReason.ChecksumMismatch)
                },
                beforeDownload = { cleanupStaleApks(keep = null) },
                afterVerified = { verifiedApk -> cleanupStaleApks(keep = verifiedApk) },
                onProgress = onProgress
            )
        )
    }

    fun cleanupInstalledUpdateCache(currentVersionCode: Long, changelogCacheDir: File) {
        cleanupInstalledCachedApks(currentVersionCode)
        cleanupInstalledCachedChangelogs(currentVersionCode, changelogCacheDir)
    }

    private fun cachedApkFile(update: AvailableAppUpdate): File {
        val safeName = update.artifact.fileName.toSafeUpdateFileName()
        return File(updateCacheDir(), "${update.versionCode}-$safeName")
    }

    private fun cleanupStaleApks(keep: File?) {
        updateCacheDir().listFiles().orEmpty().forEach { file ->
            if (file != keep) runCatching { file.deleteRecursively() }
        }
    }

    private fun cleanupInstalledCachedApks(currentVersionCode: Long) {
        val packageManager = context.packageManager
        updateCacheDir().listFiles().orEmpty().forEach { file ->
            if (!file.isFile || !file.name.endsWith(".apk", ignoreCase = true)) {
                runCatching { file.deleteRecursively() }
                return@forEach
            }

            val archiveInfo = packageManager.getPackageArchiveInfoCompat(file.absolutePath)
            val shouldDelete = archiveInfo == null ||
                archiveInfo.packageName != context.packageName ||
                archiveInfo.longVersionCodeCompat() <= currentVersionCode
            if (shouldDelete) {
                runCatching { file.delete() }
            }
        }
    }

    private fun cleanupInstalledCachedChangelogs(
        currentVersionCode: Long,
        changelogCacheDir: File
    ) {
        changelogCacheDir.listFiles().orEmpty().forEach { file ->
            val cachedVersionCode = file.name.substringBefore('-').toLongOrNull()
            if (!file.isFile ||
                !file.name.endsWith(".md", ignoreCase = true) ||
                cachedVersionCode == null ||
                cachedVersionCode <= currentVersionCode
            ) {
                runCatching { file.deleteRecursively() }
            }
        }
    }

    private fun updateCacheDir(): File = File(context.cacheDir, AppUpdateConfig.UPDATE_CACHE_DIR)

    private companion object {
        const val APK_ACCEPT_HEADER = "application/vnd.android.package-archive"
    }
}

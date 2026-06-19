package com.cybercat.ebooksender.data.update

import com.cybercat.ebooksender.model.update.AppUpdateArtifact
import com.cybercat.ebooksender.model.update.AppUpdateManifest
import kotlinx.coroutines.flow.StateFlow

interface AppUpdateManager {
    val state: StateFlow<AppUpdateState>

    fun checkForUpdates(trigger: AppUpdateCheckTrigger)

    fun installAvailableUpdate()

    fun cancelUpdateDownload()

    fun clearStatus()

    suspend fun clearUpdateCache(): Long
}

enum class AppUpdateCheckTrigger {
    AppOpen,
    Manual
}

data class AppUpdateState(
    val currentVersionName: String = "",
    val currentVersionCode: Long = 0L,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: AppUpdateDownloadProgress? = null,
    val availableUpdate: AvailableAppUpdate? = null,
    val status: AppUpdateStatus? = null,
    val statusEventId: Long = 0L
)

data class AppUpdateDownloadProgress(val bytesRead: Long = 0L, val totalBytes: Long? = null) {
    val fraction: Float? = totalBytes
        ?.takeIf { it > 0L }
        ?.let { total -> (bytesRead.toFloat() / total.toFloat()).coerceIn(0f, 1f) }
}

data class AvailableAppUpdate(val manifest: AppUpdateManifest, val artifact: AppUpdateArtifact) {
    val versionName: String get() = manifest.versionName
    val versionCode: Long get() = manifest.versionCode
    val changelogUrl: String? get() = manifest.changelogUrl
}

sealed class AppUpdateStatus {
    data object NoUpdateAvailable : AppUpdateStatus()
    data class UpdateAvailable(val update: AvailableAppUpdate) : AppUpdateStatus()
    data class Downloading(val update: AvailableAppUpdate) : AppUpdateStatus()
    data class ReadyToInstall(val update: AvailableAppUpdate) : AppUpdateStatus()
    data object DownloadCanceled : AppUpdateStatus()
    data class Error(val reason: AppUpdateErrorReason) : AppUpdateStatus()
}

enum class AppUpdateErrorReason {
    Network,
    InvalidManifest,
    NoCompatibleArtifact,
    DownloadFailed,
    ChecksumMismatch,
    SignatureMismatch,
    InstallUnavailable,
    Unknown
}

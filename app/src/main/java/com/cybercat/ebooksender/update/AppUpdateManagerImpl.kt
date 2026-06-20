package com.cybercat.ebooksender.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.cybercat.ebooksender.BuildConfig
import com.cybercat.ebooksender.data.update.AppUpdateCheckTrigger
import com.cybercat.ebooksender.data.update.AppUpdateDownloadProgress
import com.cybercat.ebooksender.data.update.AppUpdateErrorReason
import com.cybercat.ebooksender.data.update.AppUpdateManager
import com.cybercat.ebooksender.data.update.AppUpdateState
import com.cybercat.ebooksender.data.update.AppUpdateStatus
import com.cybercat.ebooksender.data.update.AvailableAppUpdate
import com.cybercat.ebooksender.data.update.UpdateArtifactDownloadRequest
import com.cybercat.ebooksender.data.update.UpdateArtifactDownloader
import com.cybercat.ebooksender.data.update.UpdateChangelogLoader
import com.cybercat.ebooksender.data.update.UpdateJobController
import com.cybercat.ebooksender.data.update.UpdateManifestLoader
import com.cybercat.ebooksender.data.update.UpdateManifestRequest
import com.cybercat.ebooksender.data.update.clearUpdateCacheDirectories
import com.cybercat.ebooksender.data.update.toSafeUpdateFileName
import com.cybercat.ebooksender.data.update.updateFileSha256
import com.cybercat.ebooksender.di.ApplicationScope
import com.cybercat.ebooksender.model.update.AppUpdateArtifact
import com.cybercat.ebooksender.model.update.AppUpdateManifest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class AppUpdateManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope
) : AppUpdateManager {
    private val json = Json { ignoreUnknownKeys = true }
    private val manifestLoader = UpdateManifestLoader(json)
    private val artifactDownloader = UpdateArtifactDownloader()
    private val packageManager = context.packageManager
    private var pendingInstallPermissionUpdate: AvailableAppUpdate? = null

    private val _state = MutableStateFlow(
        AppUpdateState(
            currentVersionName = currentVersionName(),
            currentVersionCode = currentVersionCode()
        )
    )
    override val state: StateFlow<AppUpdateState> = _state.asStateFlow()
    private val updateJobs = UpdateJobController(
        scope = applicationScope,
        state = _state,
        updateState = { reducer -> _state.update(reducer) },
        statusEventId = AppUpdateState::statusEventId,
        clearStatus = { it.copy(status = null) },
        statusAutoClearDelayMs = STATUS_AUTO_CLEAR_MS
    )

    init {
        applicationScope.launch(Dispatchers.IO) {
            cleanupInstalledUpdateCache()
        }
    }

    override fun checkForUpdates(trigger: AppUpdateCheckTrigger) {
        updateJobs.launchCheck {
            _state.update {
                it.copy(
                    isChecking = true,
                    status = null
                )
            }
            val result = withContext(Dispatchers.IO) { runCatching { loadAvailableUpdate() } }
            result
                .onSuccess { update ->
                    _state.update { state ->
                        if (update == null) {
                            state.withStatus(
                                status = if (trigger == AppUpdateCheckTrigger.Manual) {
                                    AppUpdateStatus.NoUpdateAvailable
                                } else {
                                    null
                                },
                                isChecking = false,
                                availableUpdate = null
                            )
                        } else {
                            state.withStatus(
                                status = AppUpdateStatus.UpdateAvailable(update),
                                isChecking = false,
                                availableUpdate = update
                            )
                        }
                    }
                    scheduleStatusClearIfTransient(_state.value.status)
                }
                .onFailure { throwable ->
                    _state.update {
                        val status = if (trigger == AppUpdateCheckTrigger.Manual) {
                            AppUpdateStatus.Error(throwable.toUpdateErrorReason())
                        } else {
                            null
                        }
                        it.withStatus(
                            status = status,
                            isChecking = false
                        )
                    }
                    scheduleStatusClearIfTransient(_state.value.status)
                }
        }
    }

    override fun installAvailableUpdate() {
        val update = state.value.availableUpdate ?: return
        updateJobs.launchInstall {
            _state.update {
                it.withStatus(
                    status = AppUpdateStatus.Downloading(update),
                    isDownloading = true
                ).copy(downloadProgress = null)
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val latestUpdate = loadAvailableUpdate()
                        ?: throw AppUpdateException(AppUpdateErrorReason.NoCompatibleArtifact)
                    val apk = getVerifiedApk(latestUpdate)
                    verifyApkPackage(apk)
                    latestUpdate to apk
                }
            }
            result
                .onSuccess { (latestUpdate, apk) ->
                    val installLaunchResult = launchInstaller(latestUpdate, apk)
                    val status = when (installLaunchResult) {
                        InstallLaunchResult.InstallerStarted ->
                            AppUpdateStatus.ReadyToInstall(latestUpdate)

                        InstallLaunchResult.PermissionRequired -> null

                        InstallLaunchResult.InstallUnavailable ->
                            AppUpdateStatus.Error(AppUpdateErrorReason.InstallUnavailable)
                    }
                    _state.update {
                        it.withStatus(
                            status = status,
                            isDownloading = false,
                            availableUpdate = latestUpdate
                        ).copy(downloadProgress = null)
                    }
                    scheduleStatusClearIfTransient(_state.value.status)
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) {
                        _state.update {
                            it.withStatus(
                                status = AppUpdateStatus.DownloadCanceled,
                                isDownloading = false
                            ).copy(downloadProgress = null)
                        }
                        scheduleStatusClearIfTransient(_state.value.status)
                        return@onFailure
                    }
                    _state.update {
                        it.withStatus(
                            status = AppUpdateStatus.Error(throwable.toUpdateErrorReason()),
                            isDownloading = false
                        ).copy(downloadProgress = null)
                    }
                    scheduleStatusClearIfTransient(_state.value.status)
                }
        }
    }

    override fun resumePendingInstall() {
        val update = pendingInstallPermissionUpdate ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            return
        }

        pendingInstallPermissionUpdate = null
        _state.update {
            it.withStatus(
                status = AppUpdateStatus.UpdateAvailable(update),
                isDownloading = false,
                availableUpdate = update
            ).copy(downloadProgress = null)
        }
        installAvailableUpdate()
    }

    override fun cancelUpdateDownload() {
        updateJobs.cancelInstall()
    }

    override fun clearStatus() {
        updateJobs.clearStatus()
    }

    override suspend fun loadChangelog(update: AvailableAppUpdate, languageCode: String): String? =
        update.changelogUrlFor(languageCode)?.let { changelogUrl ->
            UpdateChangelogLoader.load(
                changelogUrl = changelogUrl,
                cacheDir = changelogCacheDir(),
                versionCode = update.versionCode,
                versionName = update.versionName,
                languageCode = languageCode,
                userAgent = USER_AGENT
            )
        }

    override suspend fun clearUpdateCache(): Long =
        clearUpdateCacheDirectories(listOf(updateCacheDir(), changelogCacheDir()))

    private fun loadAvailableUpdate(): AvailableAppUpdate? {
        val manifest = fetchManifest()
        validateManifest(manifest)
        if (manifest.versionCode <= currentVersionCode()) return null

        val artifact = selectArtifact(manifest)
            ?: throw AppUpdateException(AppUpdateErrorReason.NoCompatibleArtifact)
        validateArtifact(artifact)
        return AvailableAppUpdate(manifest, artifact)
    }

    private fun fetchManifest(): AppUpdateManifest = manifestLoader.load(
        UpdateManifestRequest(
            url = BuildConfig.UPDATE_MANIFEST_URL,
            serializer = AppUpdateManifest.serializer(),
            userAgent = USER_AGENT,
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            readTimeoutMs = READ_TIMEOUT_MS,
            maxBytes = MAX_MANIFEST_BYTES,
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
            !SHA256_PATTERN.matches(artifact.sha256)
        ) {
            throw AppUpdateException(AppUpdateErrorReason.InvalidManifest)
        }
    }

    private fun selectArtifact(manifest: AppUpdateManifest): AppUpdateArtifact? {
        val artifactsByAbi = manifest.artifacts.associateBy { it.abi }
        return Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi -> artifactsByAbi[abi] }
            ?: artifactsByAbi[UNIVERSAL_ABI]
    }

    private suspend fun getVerifiedApk(update: AvailableAppUpdate): File {
        val apk = cachedApkFile(update)
        val expectedHash = update.artifact.sha256.lowercase()
        if (apk.isFile && apk.updateFileSha256() == expectedHash) {
            cleanupStaleApks(keep = apk)
            return apk
        }

        cleanupStaleApks(keep = null)
        downloadApk(update, apk)
        val actualHash = apk.updateFileSha256()
        if (actualHash != expectedHash) {
            runCatching { apk.delete() }
            throw AppUpdateException(AppUpdateErrorReason.ChecksumMismatch)
        }
        cleanupStaleApks(keep = apk)
        return apk
    }

    private suspend fun downloadApk(update: AvailableAppUpdate, target: File) {
        artifactDownloader.download(
            UpdateArtifactDownloadRequest(
                url = update.artifact.url,
                target = target,
                userAgent = USER_AGENT,
                connectTimeoutMs = CONNECT_TIMEOUT_MS,
                readTimeoutMs = DOWNLOAD_READ_TIMEOUT_MS,
                accept = "application/vnd.android.package-archive",
                useCaches = true,
                downloadFailedException = { cause ->
                    AppUpdateException(AppUpdateErrorReason.DownloadFailed, cause)
                },
                onProgress = ::updateDownloadProgress
            )
        )
    }

    private fun updateDownloadProgress(bytesRead: Long, totalBytes: Long?) {
        _state.update { state ->
            state.copy(
                downloadProgress = AppUpdateDownloadProgress(
                    bytesRead = bytesRead,
                    totalBytes = totalBytes
                )
            )
        }
    }

    private fun verifyApkPackage(apk: File) {
        val archiveInfo = packageManager.getPackageArchiveInfoCompat(apk.absolutePath)
            ?: throw AppUpdateException(AppUpdateErrorReason.InvalidManifest)
        if (archiveInfo.packageName != context.packageName) {
            throw AppUpdateException(AppUpdateErrorReason.InvalidManifest)
        }
        val archiveVersionCode = archiveInfo.longVersionCodeCompat()
        if (archiveVersionCode <= currentVersionCode()) {
            throw AppUpdateException(AppUpdateErrorReason.InvalidManifest)
        }

        val installedInfo = packageManager.getPackageInfoCompat(context.packageName)
        val installedSignatures = installedInfo.signingCertificateBytes()
        val archiveSignatures = archiveInfo.signingCertificateBytes()
        if (installedSignatures.isEmpty() || installedSignatures != archiveSignatures) {
            throw AppUpdateException(AppUpdateErrorReason.SignatureMismatch)
        }
    }

    private fun launchInstaller(update: AvailableAppUpdate, apk: File): InstallLaunchResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallPermissionUpdate = update
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return runCatching { context.startActivity(settingsIntent) }
                .fold(
                    onSuccess = { InstallLaunchResult.PermissionRequired },
                    onFailure = {
                        pendingInstallPermissionUpdate = null
                        InstallLaunchResult.InstallUnavailable
                    }
                )
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )

        @Suppress("DEPRECATION")
        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(installIntent) }
            .fold(
                onSuccess = { InstallLaunchResult.InstallerStarted },
                onFailure = { InstallLaunchResult.InstallUnavailable }
            )
    }

    private fun cachedApkFile(update: AvailableAppUpdate): File {
        val safeName = update.artifact.fileName.toSafeUpdateFileName()
        return File(updateCacheDir(), "${update.versionCode}-$safeName")
    }

    private fun cleanupStaleApks(keep: File?) {
        val dir = updateCacheDir()
        dir.listFiles().orEmpty().forEach { file ->
            if (file != keep) runCatching { file.deleteRecursively() }
        }
    }

    private fun cleanupInstalledUpdateCache() {
        cleanupInstalledCachedApks()
        cleanupInstalledCachedChangelogs()
    }

    private fun cleanupInstalledCachedApks() {
        val installedVersionCode = currentVersionCode()
        updateCacheDir().listFiles().orEmpty().forEach { file ->
            if (!file.isFile || !file.name.endsWith(".apk", ignoreCase = true)) {
                runCatching { file.deleteRecursively() }
                return@forEach
            }

            val archiveInfo = packageManager.getPackageArchiveInfoCompat(file.absolutePath)
            val shouldDelete = archiveInfo == null ||
                archiveInfo.packageName != context.packageName ||
                archiveInfo.longVersionCodeCompat() <= installedVersionCode
            if (shouldDelete) {
                runCatching { file.delete() }
            }
        }
    }

    private fun cleanupInstalledCachedChangelogs() {
        val installedVersionCode = currentVersionCode()
        changelogCacheDir().listFiles().orEmpty().forEach { file ->
            val cachedVersionCode = file.name.substringBefore('-').toLongOrNull()
            if (!file.isFile ||
                !file.name.endsWith(".md", ignoreCase = true) ||
                cachedVersionCode == null ||
                cachedVersionCode <= installedVersionCode
            ) {
                runCatching { file.deleteRecursively() }
            }
        }
    }

    private fun updateCacheDir(): File = File(context.cacheDir, UPDATE_CACHE_DIR)

    private fun changelogCacheDir(): File = File(context.cacheDir, CHANGELOG_CACHE_DIR)

    private fun currentVersionName(): String {
        val info = packageManager.getPackageInfoCompat(context.packageName)
        return info.versionName ?: BuildConfig.VERSION_NAME
    }

    private fun currentVersionCode(): Long =
        packageManager.getPackageInfoCompat(context.packageName).longVersionCodeCompat()

    private fun Throwable.toUpdateErrorReason(): AppUpdateErrorReason =
        (this as? AppUpdateException)?.reason ?: AppUpdateErrorReason.Unknown

    private fun AppUpdateState.withStatus(
        status: AppUpdateStatus?,
        isChecking: Boolean = this.isChecking,
        isDownloading: Boolean = this.isDownloading,
        availableUpdate: AvailableAppUpdate? = this.availableUpdate
    ): AppUpdateState = copy(
        isChecking = isChecking,
        isDownloading = isDownloading,
        availableUpdate = availableUpdate,
        status = status,
        statusEventId = if (status == null && this.status == null) {
            statusEventId
        } else {
            statusEventId + 1L
        }
    )

    private fun scheduleStatusClearIfTransient(status: AppUpdateStatus?) {
        updateJobs.scheduleStatusClearIf(
            when (status) {
                AppUpdateStatus.NoUpdateAvailable,
                AppUpdateStatus.DownloadCanceled,
                is AppUpdateStatus.Error,
                is AppUpdateStatus.ReadyToInstall -> true

                else -> false
            }
        )
    }

    private companion object {
        const val UPDATE_CACHE_DIR = "update-apks"
        const val CHANGELOG_CACHE_DIR = "update-changelogs"
        const val UNIVERSAL_ABI = "universal"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 10_000
        const val DOWNLOAD_READ_TIMEOUT_MS = 30_000
        const val MAX_MANIFEST_BYTES = 512 * 1024
        const val STATUS_AUTO_CLEAR_MS = 3_000L
        const val USER_AGENT = "eBookSender/${BuildConfig.VERSION_NAME}"
        val SHA256_PATTERN = Regex("^[a-fA-F0-9]{64}$")
    }
}

private enum class InstallLaunchResult {
    InstallerStarted,
    PermissionRequired,
    InstallUnavailable
}

private class AppUpdateException(val reason: AppUpdateErrorReason, cause: Throwable? = null) :
    Exception(cause)

private fun PackageManager.getPackageInfoCompat(packageName: String): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
    }

private fun PackageManager.getPackageArchiveInfoCompat(path: String): PackageInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
    } else {
        @Suppress("DEPRECATION")
        getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
    }

private fun PackageInfo.longVersionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }

private fun PackageInfo.signingCertificateBytes(): Set<List<Byte>> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        signingInfo?.apkContentsSigners.orEmpty().map { signature ->
            signature.toByteArray().asList()
        }.toSet()
    } else {
        @Suppress("DEPRECATION")
        signatures.orEmpty().map { signature ->
            signature.toByteArray().asList()
        }.toSet()
    }

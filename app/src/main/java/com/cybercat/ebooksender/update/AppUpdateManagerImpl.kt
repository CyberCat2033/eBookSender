package com.cybercat.ebooksender.update

import android.content.Context
import com.cybercat.ebooksender.BuildConfig
import com.cybercat.ebooksender.data.update.AppUpdateCheckTrigger
import com.cybercat.ebooksender.data.update.AppUpdateDownloadProgress
import com.cybercat.ebooksender.data.update.AppUpdateErrorReason
import com.cybercat.ebooksender.data.update.AppUpdateManager
import com.cybercat.ebooksender.data.update.AppUpdateState
import com.cybercat.ebooksender.data.update.AppUpdateStatus
import com.cybercat.ebooksender.data.update.AvailableAppUpdate
import com.cybercat.ebooksender.data.update.UpdateArtifactDownloader
import com.cybercat.ebooksender.data.update.UpdateChangelogLoader
import com.cybercat.ebooksender.data.update.UpdateJobController
import com.cybercat.ebooksender.data.update.UpdateManifestLoader
import com.cybercat.ebooksender.data.update.clearUpdateCacheDirectories
import com.cybercat.ebooksender.data.update.nextUpdateStatusEventId
import com.cybercat.ebooksender.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
    private val manifestResolver = AppUpdateManifestResolver(context, manifestLoader)
    private val apkRepository = AppUpdateApkRepository(context, artifactDownloader)
    private val packageVerifier = AppUpdatePackageVerifier(context)
    private val installerLauncher = AppUpdateInstallerLauncher(context)

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
        statusAutoClearDelayMs = AppUpdateConfig.STATUS_AUTO_CLEAR_MS
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
                    val apk = apkRepository.getVerifiedApk(
                        update = latestUpdate,
                        onProgress = ::updateDownloadProgress
                    )
                    packageVerifier.verifyApkPackage(apk, currentVersionCode())
                    latestUpdate to apk
                }
            }
            result
                .onSuccess { (latestUpdate, apk) ->
                    val installLaunchResult = installerLauncher.launchInstaller(latestUpdate, apk)
                    val status = when (installLaunchResult) {
                        AppUpdateInstallLaunchResult.InstallerStarted ->
                            AppUpdateStatus.ReadyToInstall(latestUpdate)

                        AppUpdateInstallLaunchResult.PermissionRequired -> null

                        AppUpdateInstallLaunchResult.InstallUnavailable ->
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
        if (!installerLauncher.canResumePendingInstall()) return
        val update = installerLauncher.consumePendingPermissionUpdate() ?: return

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
                userAgent = AppUpdateConfig.USER_AGENT
            )
        }

    override suspend fun clearUpdateCache(): Long =
        clearUpdateCacheDirectories(listOf(updateCacheDir(), changelogCacheDir()))

    private fun loadAvailableUpdate(): AvailableAppUpdate? =
        manifestResolver.loadAvailableUpdate(currentVersionCode())

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

    private fun cleanupInstalledUpdateCache() {
        apkRepository.cleanupInstalledUpdateCache(
            currentVersionCode = currentVersionCode(),
            changelogCacheDir = changelogCacheDir()
        )
    }

    private fun updateCacheDir(): File = File(context.cacheDir, AppUpdateConfig.UPDATE_CACHE_DIR)

    private fun changelogCacheDir(): File =
        File(context.cacheDir, AppUpdateConfig.CHANGELOG_CACHE_DIR)

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
        statusEventId = nextUpdateStatusEventId(
            currentStatus = this.status,
            nextStatus = status,
            currentEventId = statusEventId
        )
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
}

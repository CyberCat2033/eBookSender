package com.cybercat.ebooksender.data.update

import android.content.Context
import com.cybercat.ebooksender.data.ftp.FtpGateway
import com.cybercat.ebooksender.data.pocketbook.PocketBookControlClient
import com.cybercat.ebooksender.di.ApplicationScope
import com.cybercat.ebooksender.model.DeviceProfile
import com.cybercat.ebooksender.model.RemoteDevice
import com.cybercat.ebooksender.model.update.PocketBookServerUpdateArtifact
import com.cybercat.ebooksender.model.update.PocketBookServerUpdateManifest
import com.cybercat.ebooksender.model.update.PocketBookServerVersionInfo
import com.cybercat.ebooksender.transfer.ConnectionManager
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class PocketBookServerUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: ConnectionManager,
    private val controlClient: PocketBookControlClient,
    private val ftpGateway: FtpGateway,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val manifestLoader = UpdateManifestLoader(json)
    private val artifactDownloader = UpdateArtifactDownloader()
    private val versionReader = PocketBookServerVersionReader(controlClient)
    private val updateResolver = PocketBookServerUpdateResolver(manifestLoader, versionReader)
    private val artifactRepository = PocketBookServerArtifactRepository(context, artifactDownloader)
    private val updateInstaller = PocketBookServerUpdateInstaller(
        controlClient = controlClient,
        ftpGateway = ftpGateway,
        artifactRepository = artifactRepository,
        versionReader = versionReader
    )
    private var lastAutoCheckedConnectionKey: String? = null

    private val _state = MutableStateFlow(PocketBookServerUpdateState())
    val state: StateFlow<PocketBookServerUpdateState> = _state.asStateFlow()
    private val updateJobs = UpdateJobController(
        scope = applicationScope,
        state = _state,
        updateState = { reducer -> _state.update(reducer) },
        statusEventId = PocketBookServerUpdateState::statusEventId,
        clearStatus = { it.copy(status = null) },
        statusAutoClearDelayMs = PocketBookServerUpdateConfig.STATUS_AUTO_CLEAR_MS
    )

    init {
        applicationScope.launch {
            connectionManager.connectedDevice
                .map { device -> device?.takeIf { it.profile == DeviceProfile.PocketBook } }
                .map { device -> device?.autoUpdateConnectionKey() }
                .distinctUntilChanged()
                .collect { connectionKey ->
                    if (connectionKey == null) {
                        lastAutoCheckedConnectionKey = null
                        return@collect
                    }
                    if (connectionKey != lastAutoCheckedConnectionKey) {
                        lastAutoCheckedConnectionKey = connectionKey
                        checkForUpdates(PocketBookServerUpdateCheckTrigger.DeviceConnected)
                    }
                }
        }
    }

    fun checkForUpdates(
        trigger: PocketBookServerUpdateCheckTrigger = PocketBookServerUpdateCheckTrigger.Manual
    ) {
        updateJobs.launchCheck {
            val device = connectedPocketBookOrNull()
            if (device == null) {
                if (trigger == PocketBookServerUpdateCheckTrigger.Manual) {
                    setStatus(PocketBookServerUpdateStatus.NoPocketBookConnected)
                    scheduleStatusClearIfTransient(_state.value.status)
                }
                return@launchCheck
            }

            _state.update { it.copy(isChecking = true, status = null) }

            val result = withContext(Dispatchers.IO) {
                runCatching { updateResolver.findAvailableUpdate(device) }
            }
            result
                .onSuccess { checkResult ->
                    _state.update { state ->
                        val status = when {
                            trigger == PocketBookServerUpdateCheckTrigger.Manual -> {
                                checkResult.status
                            }

                            checkResult.status.isPromptingUpdate -> checkResult.status

                            else -> null
                        }
                        state.withStatus(
                            status = status,
                            isChecking = false,
                            installedVersion = checkResult.installedVersion,
                            availableUpdate = checkResult.availableUpdate
                        )
                    }
                    scheduleStatusClearIfTransient(_state.value.status)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.update {
                        val status = if (trigger == PocketBookServerUpdateCheckTrigger.Manual) {
                            PocketBookServerUpdateStatus.Error(
                                error.toPocketBookServerUpdateErrorReason()
                            )
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

    fun installAvailableUpdate() {
        val update = state.value.availableUpdate ?: return
        val device = connectedPocketBookOrNull()
        if (device == null) {
            setStatus(PocketBookServerUpdateStatus.NoPocketBookConnected)
            scheduleStatusClearIfTransient(_state.value.status)
            return
        }

        updateJobs.launchInstall {
            _state.update {
                it.withStatus(
                    status = PocketBookServerUpdateStatus.Installing(update),
                    isInstalling = true,
                    availableUpdate = update
                ).copy(installProgress = null)
            }

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    updateInstaller.installUpdate(device, update) { progress ->
                        _state.update { it.copy(installProgress = progress) }
                    }
                }
            }
            result
                .onSuccess { installedVersion ->
                    clearUpdateCache()
                    _state.update {
                        it.withStatus(
                            status = PocketBookServerUpdateStatus.Installed(installedVersion),
                            isInstalling = false,
                            installedVersion = installedVersion,
                            availableUpdate = null
                        ).copy(installProgress = null)
                    }
                    scheduleStatusClearIfTransient(_state.value.status)
                }
                .onFailure { error ->
                    if (error is CancellationException) {
                        _state.update {
                            it.withStatus(
                                status = PocketBookServerUpdateStatus.InstallCanceled,
                                isInstalling = false
                            ).copy(installProgress = null)
                        }
                        scheduleStatusClearIfTransient(_state.value.status)
                        return@onFailure
                    }
                    _state.update {
                        it.withStatus(
                            status = PocketBookServerUpdateStatus.Error(
                                error.toPocketBookServerUpdateErrorReason()
                            ),
                            isInstalling = false
                        ).copy(installProgress = null)
                    }
                    scheduleStatusClearIfTransient(_state.value.status)
                }
        }
    }

    fun cancelInstall() {
        updateJobs.cancelActiveInstallIfPresent {
            _state.update {
                it.withStatus(
                    status = PocketBookServerUpdateStatus.InstallCanceled,
                    isInstalling = false
                ).copy(installProgress = null)
            }
            scheduleStatusClearIfTransient(_state.value.status)
        }
    }

    fun clearStatus() {
        updateJobs.clearStatus()
    }

    suspend fun clearUpdateCache(): Long =
        clearUpdateCacheDirectories(listOf(updateCacheDir(), changelogCacheDir()))

    suspend fun loadChangelog(
        update: AvailablePocketBookServerUpdate,
        languageCode: String
    ): String? = update.changelogUrlFor(languageCode)?.let { changelogUrl ->
        UpdateChangelogLoader.load(
            changelogUrl = changelogUrl,
            cacheDir = changelogCacheDir(),
            versionCode = update.versionCode,
            versionName = update.versionName,
            languageCode = languageCode,
            userAgent = PocketBookServerUpdateConfig.USER_AGENT
        )
    }

    private fun connectedPocketBookOrNull(): RemoteDevice? = connectionManager.connectedDevice.value
        ?.takeIf { it.profile == DeviceProfile.PocketBook }

    private fun setStatus(status: PocketBookServerUpdateStatus?) {
        _state.update { it.withStatus(status = status) }
    }

    private fun updateCacheDir(): File =
        File(context.cacheDir, PocketBookServerUpdateConfig.UPDATE_CACHE_DIR)

    private fun changelogCacheDir(): File =
        File(context.cacheDir, PocketBookServerUpdateConfig.CHANGELOG_CACHE_DIR)

    private fun Throwable.toPocketBookServerUpdateErrorReason(): PocketBookServerUpdateErrorReason =
        (this as? PocketBookServerUpdateException)?.reason
            ?: PocketBookServerUpdateErrorReason.Unknown

    private fun PocketBookServerUpdateState.withStatus(
        status: PocketBookServerUpdateStatus?,
        isChecking: Boolean = this.isChecking,
        isInstalling: Boolean = this.isInstalling,
        installedVersion: PocketBookServerVersionInfo? = this.installedVersion,
        availableUpdate: AvailablePocketBookServerUpdate? = this.availableUpdate
    ): PocketBookServerUpdateState = copy(
        isChecking = isChecking,
        isInstalling = isInstalling,
        installedVersion = installedVersion,
        availableUpdate = availableUpdate,
        status = status,
        statusEventId = nextUpdateStatusEventId(
            currentStatus = this.status,
            nextStatus = status,
            currentEventId = statusEventId
        )
    )

    private fun scheduleStatusClearIfTransient(status: PocketBookServerUpdateStatus?) {
        updateJobs.scheduleStatusClearIf(
            when (status) {
                PocketBookServerUpdateStatus.NoPocketBookConnected,
                PocketBookServerUpdateStatus.NoUpdateAvailable,
                PocketBookServerUpdateStatus.InstallCanceled,
                is PocketBookServerUpdateStatus.Installed,
                is PocketBookServerUpdateStatus.Error -> true

                else -> false
            }
        )
    }

    private fun RemoteDevice.autoUpdateConnectionKey(): String = listOf(
        host,
        port.toString(),
        username,
        rootPath
    ).joinToString("|")
}

data class PocketBookServerUpdateState(
    val installedVersion: PocketBookServerVersionInfo? = null,
    val isChecking: Boolean = false,
    val isInstalling: Boolean = false,
    val installProgress: PocketBookServerUpdateProgress? = null,
    val availableUpdate: AvailablePocketBookServerUpdate? = null,
    val status: PocketBookServerUpdateStatus? = null,
    val statusEventId: Long = 0L
)

data class AvailablePocketBookServerUpdate(
    val manifest: PocketBookServerUpdateManifest,
    val launcherArtifact: PocketBookServerUpdateArtifact
) {
    val versionName: String get() = manifest.versionName
    val versionCode: Long get() = manifest.versionCode
    val buildId: String? get() = manifest.buildId
    val changelogUrl: String? get() = manifest.changelogUrl
    val changelogUrls: Map<String, String> get() = manifest.changelogUrls

    fun changelogUrlFor(languageCode: String): String? = localizedUpdateUrl(
        urls = changelogUrls,
        fallbackUrl = changelogUrl,
        languageCode = languageCode
    )
}

data class PocketBookServerUpdateProgress(
    val phase: PocketBookServerUpdatePhase,
    val bytesCompleted: Long = 0L,
    val totalBytes: Long? = null
) {
    val fraction: Float? = totalBytes
        ?.takeIf { it > 0L }
        ?.let { total -> (bytesCompleted.toFloat() / total.toFloat()).coerceIn(0f, 1f) }
}

enum class PocketBookServerUpdatePhase {
    Downloading,
    Uploading
}

sealed class PocketBookServerUpdateStatus {
    data object NoPocketBookConnected : PocketBookServerUpdateStatus()
    data object NoUpdateAvailable : PocketBookServerUpdateStatus()
    data class UpdateAvailable(val update: AvailablePocketBookServerUpdate) :
        PocketBookServerUpdateStatus()

    data class InstalledVersionUnknown(val update: AvailablePocketBookServerUpdate) :
        PocketBookServerUpdateStatus()

    data class Installing(val update: AvailablePocketBookServerUpdate) :
        PocketBookServerUpdateStatus()

    data class Installed(val version: PocketBookServerVersionInfo) : PocketBookServerUpdateStatus()
    data object InstallCanceled : PocketBookServerUpdateStatus()
    data class Error(val reason: PocketBookServerUpdateErrorReason) : PocketBookServerUpdateStatus()
}

enum class PocketBookServerUpdateErrorReason {
    Network,
    InvalidManifest,
    MissingArtifacts,
    DownloadFailed,
    ChecksumMismatch,
    UploadFailed,
    ApplyFailed,
    RestartNotConfirmed,
    Unknown
}

enum class PocketBookServerUpdateCheckTrigger {
    DeviceConnected,
    Manual
}

private val PocketBookServerUpdateStatus.isPromptingUpdate: Boolean
    get() = this is PocketBookServerUpdateStatus.UpdateAvailable ||
        this is PocketBookServerUpdateStatus.InstalledVersionUnknown

package com.cybercat.ebooksender.data.update

import android.content.Context
import com.cybercat.ebooksender.data.ftp.FtpGateway
import com.cybercat.ebooksender.data.pocketbook.PocketBookControlClient
import com.cybercat.ebooksender.data.pocketbook.PocketBookServerApplyUpdateRequest
import com.cybercat.ebooksender.data.pocketbook.PocketBookUpdateEndpointUnavailableException
import com.cybercat.ebooksender.di.ApplicationScope
import com.cybercat.ebooksender.model.DEFAULT_FTP_RELATIVE_ROOT_PATH
import com.cybercat.ebooksender.model.DeviceProfile
import com.cybercat.ebooksender.model.RemoteDevice
import com.cybercat.ebooksender.model.update.PocketBookServerUpdateArtifact
import com.cybercat.ebooksender.model.update.PocketBookServerUpdateManifest
import com.cybercat.ebooksender.model.update.PocketBookServerVersionInfo
import com.cybercat.ebooksender.transfer.ConnectionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
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
    private var checkJob: Job? = null
    private var installJob: Job? = null
    private var statusClearJob: Job? = null

    private val _state = MutableStateFlow(PocketBookServerUpdateState())
    val state: StateFlow<PocketBookServerUpdateState> = _state.asStateFlow()

    fun checkForUpdates() {
        if (checkJob?.isActive == true) return
        checkJob = applicationScope.launch {
            val device = connectedPocketBookOrNull()
            if (device == null) {
                setStatus(PocketBookServerUpdateStatus.NoPocketBookConnected)
                scheduleStatusClearIfTransient(_state.value.status)
                return@launch
            }

            _state.update { it.copy(isChecking = true, status = null) }

            val result = withContext(Dispatchers.IO) {
                runCatching { findAvailableUpdate(device) }
            }
            result
                .onSuccess { checkResult ->
                    _state.update { state ->
                        state.withStatus(
                            status = checkResult.status,
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
                        it.withStatus(
                            status = PocketBookServerUpdateStatus.Error(
                                error.toPocketBookServerUpdateErrorReason()
                            ),
                            isChecking = false
                        )
                    }
                    scheduleStatusClearIfTransient(_state.value.status)
                }
        }
    }

    fun installAvailableUpdate() {
        if (installJob?.isActive == true) return
        val update = state.value.availableUpdate ?: return
        val device = connectedPocketBookOrNull()
        if (device == null) {
            setStatus(PocketBookServerUpdateStatus.NoPocketBookConnected)
            scheduleStatusClearIfTransient(_state.value.status)
            return
        }

        installJob = applicationScope.launch {
            _state.update {
                it.withStatus(
                    status = PocketBookServerUpdateStatus.Installing(update),
                    isInstalling = true,
                    availableUpdate = update
                ).copy(installProgress = null)
            }

            val result = withContext(Dispatchers.IO) {
                runCatching { installUpdate(device, update) }
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
        val job = installJob?.takeIf { it.isActive } ?: return
        _state.update {
            it.withStatus(
                status = PocketBookServerUpdateStatus.InstallCanceled,
                isInstalling = false
            ).copy(installProgress = null)
        }
        scheduleStatusClearIfTransient(_state.value.status)
        job.cancel()
    }

    fun clearStatus() {
        statusClearJob?.cancel()
        _state.update { it.copy(status = null) }
    }

    suspend fun clearUpdateCache(): Long = withContext(Dispatchers.IO) {
        val dir = updateCacheDir()
        val size = dir.folderSize()
        if (size > 0L) {
            runCatching { dir.deleteRecursively() }
        }
        size
    }

    private suspend fun findAvailableUpdate(
        device: RemoteDevice
    ): PocketBookServerUpdateCheckResult {
        val installedVersion = readInstalledVersionOrNull(device)
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

    private suspend fun readInstalledVersionOrNull(
        device: RemoteDevice
    ): PocketBookServerVersionInfo? = controlClient.readVersion(device).getOrNull()
        ?.takeIf { version ->
            version.appName == POCKETBOOK_SERVER_APP_NAME &&
                version.versionName.isNotBlank() &&
                version.versionCode > 0L
        }

    private fun fetchManifest(): PocketBookServerUpdateManifest {
        val url = URL(UPDATE_MANIFEST_URL)
        if (url.protocol != "https") {
            throw PocketBookServerUpdateException(PocketBookServerUpdateErrorReason.InvalidManifest)
        }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw PocketBookServerUpdateException(PocketBookServerUpdateErrorReason.Network)
            }
            val body = connection.inputStream.use { it.readLimitedText(MAX_MANIFEST_BYTES) }
            return json.decodeFromString(PocketBookServerUpdateManifest.serializer(), body)
        } catch (exception: SerializationException) {
            throw PocketBookServerUpdateException(
                PocketBookServerUpdateErrorReason.InvalidManifest,
                exception
            )
        } catch (exception: IOException) {
            throw PocketBookServerUpdateException(
                PocketBookServerUpdateErrorReason.Network,
                exception
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun validateManifest(manifest: PocketBookServerUpdateManifest) {
        if (manifest.schemaVersion != 1 ||
            manifest.appName != POCKETBOOK_SERVER_APP_NAME ||
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
        val launcher = manifest.artifacts.firstOrNull { it.type == LAUNCHER_ARTIFACT_TYPE }
        if (launcher == null) {
            throw PocketBookServerUpdateException(
                PocketBookServerUpdateErrorReason.MissingArtifacts
            )
        }
        validateArtifact(launcher, EXPECTED_LAUNCHER_INSTALL_PATH, ".app")
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
            !SHA256_PATTERN.matches(artifact.sha256)
        ) {
            throw PocketBookServerUpdateException(PocketBookServerUpdateErrorReason.InvalidManifest)
        }
    }

    private suspend fun installUpdate(
        device: RemoteDevice,
        update: AvailablePocketBookServerUpdate
    ): PocketBookServerVersionInfo {
        val artifact = update.launcherArtifact
        val totalArtifactBytes = artifact.sizeBytes
        val totalWorkBytes = totalArtifactBytes?.times(2L)
        var completedWorkBytes = 0L

        fun report(phase: PocketBookServerUpdatePhase, bytes: Long) {
            _state.update { state ->
                state.copy(
                    installProgress = PocketBookServerUpdateProgress(
                        phase = phase,
                        bytesCompleted = bytes,
                        totalBytes = totalWorkBytes
                    )
                )
            }
        }

        report(PocketBookServerUpdatePhase.Downloading, 0L)
        coroutineContext.ensureActive()
        val file = getVerifiedArtifactFile(artifact) { bytesRead ->
            report(PocketBookServerUpdatePhase.Downloading, completedWorkBytes + bytesRead)
        }
        completedWorkBytes += artifact.sizeBytes ?: file.length()
        report(PocketBookServerUpdatePhase.Downloading, completedWorkBytes)

        val mountRootDevice = device.copy(relativeRootPath = DEFAULT_FTP_RELATIVE_ROOT_PATH)
        val stagedRemotePath = update.stagedLauncherRemotePath()
        coroutineContext.ensureActive()
        report(PocketBookServerUpdatePhase.Uploading, completedWorkBytes)
        uploadLauncherFile(
            device = mountRootDevice,
            remotePath = stagedRemotePath,
            file = file,
            onProgress = { uploadedBytes ->
                report(
                    PocketBookServerUpdatePhase.Uploading,
                    completedWorkBytes + uploadedBytes
                )
            }
        )
        completedWorkBytes += artifact.sizeBytes ?: file.length()
        report(PocketBookServerUpdatePhase.Uploading, completedWorkBytes)

        val appliedThroughEndpoint = applyStagedUpdateOrFallback(
            device = device,
            mountRootDevice = mountRootDevice,
            update = update,
            file = file,
            stagedRemotePath = stagedRemotePath,
            onFallbackUploadProgress = { uploadedBytes ->
                report(
                    PocketBookServerUpdatePhase.Uploading,
                    completedWorkBytes + uploadedBytes
                )
            }
        )

        if (appliedThroughEndpoint) {
            waitForInstalledVersion(device, update)?.let { return it }
        }

        return PocketBookServerVersionInfo(
            schemaVersion = 1,
            appName = update.manifest.appName,
            versionName = update.versionName,
            versionCode = update.versionCode,
            buildId = update.buildId,
            releasedAt = update.manifest.releasedAt
        )
    }

    private suspend fun uploadLauncherFile(
        device: RemoteDevice,
        remotePath: String,
        file: File,
        onProgress: (Long) -> Unit
    ) {
        file.inputStream().use { input ->
            val result = ftpGateway.uploadAtomically(
                device = device,
                remoteRelativePath = remotePath,
                input = input,
                onProgress = onProgress
            )
            result.getOrElse { error ->
                if (error is CancellationException) throw error
                throw PocketBookServerUpdateException(
                    PocketBookServerUpdateErrorReason.UploadFailed,
                    error
                )
            }
        }
    }

    private suspend fun applyStagedUpdateOrFallback(
        device: RemoteDevice,
        mountRootDevice: RemoteDevice,
        update: AvailablePocketBookServerUpdate,
        file: File,
        stagedRemotePath: String,
        onFallbackUploadProgress: (Long) -> Unit
    ): Boolean {
        val applyResult = controlClient.applyUpdate(
            device = device,
            request = PocketBookServerApplyUpdateRequest(
                sourcePath = "$POCKETBOOK_MOUNT_ROOT_WITH_SLASH$stagedRemotePath",
                versionName = update.versionName,
                versionCode = update.versionCode,
                releasedAt = update.manifest.releasedAt,
                buildId = update.buildId,
                sha256 = update.launcherArtifact.sha256
            )
        )

        applyResult
            .onSuccess { return true }
            .onFailure { error ->
                if (error is CancellationException) throw error
                if (error !is PocketBookUpdateEndpointUnavailableException) {
                    throw PocketBookServerUpdateException(
                        PocketBookServerUpdateErrorReason.ApplyFailed,
                        error
                    )
                }
            }

        val directInstallPath = update.launcherArtifact.installPath.toRemoteInstallPath()
        uploadLauncherFile(
            device = mountRootDevice,
            remotePath = directInstallPath,
            file = file,
            onProgress = onFallbackUploadProgress
        )
        runCatching { ftpGateway.deleteFile(mountRootDevice, stagedRemotePath) }
        return false
    }

    private suspend fun waitForInstalledVersion(
        device: RemoteDevice,
        update: AvailablePocketBookServerUpdate
    ): PocketBookServerVersionInfo? {
        repeat(UPDATE_VERSION_POLL_ATTEMPTS) {
            coroutineContext.ensureActive()
            delay(UPDATE_VERSION_POLL_DELAY_MS)
            val version = readInstalledVersionOrNull(device)
            if (version != null && update.matchesInstalledVersion(version)) {
                return version
            }
        }
        return null
    }

    private suspend fun getVerifiedArtifactFile(
        artifact: PocketBookServerUpdateArtifact,
        onDownloadProgress: (Long) -> Unit
    ): File {
        val target = cachedArtifactFile(artifact)
        val expectedHash = artifact.sha256.lowercase()
        if (target.isFile && target.sha256() == expectedHash) {
            return target
        }

        runCatching { target.delete() }
        downloadArtifact(artifact, target, onDownloadProgress)
        if (target.sha256() != expectedHash) {
            runCatching { target.delete() }
            throw PocketBookServerUpdateException(
                PocketBookServerUpdateErrorReason.ChecksumMismatch
            )
        }
        return target
    }

    private suspend fun downloadArtifact(
        artifact: PocketBookServerUpdateArtifact,
        target: File,
        onProgress: (Long) -> Unit
    ) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.download")
        runCatching { temporary.delete() }
        val connection = (URL(artifact.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = DOWNLOAD_READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
        }
        val cancellationHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                runCatching { connection.disconnect() }
            }
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw PocketBookServerUpdateException(
                    PocketBookServerUpdateErrorReason.DownloadFailed
                )
            }
            var bytesRead = 0L
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        coroutineContext.ensureActive()
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        onProgress(bytesRead)
                    }
                }
            }
            coroutineContext.ensureActive()
            if (!temporary.renameTo(target)) {
                throw PocketBookServerUpdateException(
                    PocketBookServerUpdateErrorReason.DownloadFailed
                )
            }
        } catch (exception: IOException) {
            if (coroutineContext[Job]?.isCancelled == true) {
                throw CancellationException("PocketBook server update download canceled")
            }
            throw PocketBookServerUpdateException(
                PocketBookServerUpdateErrorReason.DownloadFailed,
                exception
            )
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
            runCatching { temporary.delete() }
        }
    }

    private fun cachedArtifactFile(artifact: PocketBookServerUpdateArtifact): File {
        val safeName = artifact.fileName
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(updateCacheDir(), safeName)
    }

    private fun AvailablePocketBookServerUpdate.stagedLauncherRemotePath(): String {
        val safeName = launcherArtifact.fileName
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "$STAGING_REMOTE_DIR/$versionCode-$safeName"
    }

    private fun String?.toRemoteInstallPath(): String {
        val installPath = this
            ?: throw PocketBookServerUpdateException(
                PocketBookServerUpdateErrorReason.InvalidManifest
            )
        if (!installPath.startsWith(POCKETBOOK_MOUNT_ROOT_WITH_SLASH)) {
            throw PocketBookServerUpdateException(PocketBookServerUpdateErrorReason.InvalidManifest)
        }
        return installPath.removePrefix(POCKETBOOK_MOUNT_ROOT_WITH_SLASH)
    }

    private fun connectedPocketBookOrNull(): RemoteDevice? = connectionManager.connectedDevice.value
        ?.takeIf { it.profile == DeviceProfile.PocketBook }

    private fun PocketBookServerUpdateManifest.isNewerThan(
        installedVersion: PocketBookServerVersionInfo
    ): Boolean = when {
        versionCode > installedVersion.versionCode -> true

        versionCode < installedVersion.versionCode -> false

        else -> buildId.isMeaningfulBuildId() &&
            installedVersion.buildId.isMeaningfulBuildId() &&
            buildId != installedVersion.buildId
    }

    private fun AvailablePocketBookServerUpdate.matchesInstalledVersion(
        installedVersion: PocketBookServerVersionInfo
    ): Boolean = installedVersion.appName == POCKETBOOK_SERVER_APP_NAME &&
        installedVersion.versionCode == versionCode &&
        installedVersion.versionName == versionName &&
        (
            buildId.isNullOrBlank() ||
                installedVersion.buildId.isNullOrBlank() ||
                installedVersion.buildId == buildId
            )

    private fun String?.isMeaningfulBuildId(): Boolean = !isNullOrBlank()

    private fun setStatus(status: PocketBookServerUpdateStatus?) {
        _state.update { it.withStatus(status = status) }
    }

    private fun updateCacheDir(): File = File(context.cacheDir, UPDATE_CACHE_DIR)

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
        statusEventId = if (status == null && this.status == null) {
            statusEventId
        } else {
            statusEventId + 1L
        }
    )

    private fun scheduleStatusClearIfTransient(status: PocketBookServerUpdateStatus?) {
        val shouldClear = when (status) {
            PocketBookServerUpdateStatus.NoPocketBookConnected,
            PocketBookServerUpdateStatus.NoUpdateAvailable,
            PocketBookServerUpdateStatus.InstallCanceled,
            is PocketBookServerUpdateStatus.Installed,
            is PocketBookServerUpdateStatus.Error -> true

            else -> false
        }
        if (!shouldClear) return

        statusClearJob?.cancel()
        val eventId = _state.value.statusEventId
        statusClearJob = applicationScope.launch {
            delay(STATUS_AUTO_CLEAR_MS)
            _state.update { state ->
                if (state.statusEventId == eventId) {
                    state.copy(status = null)
                } else {
                    state
                }
            }
        }
    }

    private companion object {
        const val UPDATE_MANIFEST_URL = "https://cybercat2033.github.io/pb-ftp/updates/latest.json"
        const val UPDATE_CACHE_DIR = "pocketbook-server-updates"
        const val POCKETBOOK_SERVER_APP_NAME = "pb-ftp"
        const val LAUNCHER_ARTIFACT_TYPE = "launcher"
        const val EXPECTED_LAUNCHER_INSTALL_PATH = "/mnt/ext1/applications/pb-ftp.app"
        const val POCKETBOOK_MOUNT_ROOT_WITH_SLASH = "/mnt/ext1/"
        const val STAGING_REMOTE_DIR = "applications/.pb-ftp-update"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 10_000
        const val DOWNLOAD_READ_TIMEOUT_MS = 30_000
        const val MAX_MANIFEST_BYTES = 512 * 1024
        const val STATUS_AUTO_CLEAR_MS = 3_000L
        const val UPDATE_VERSION_POLL_ATTEMPTS = 12
        const val UPDATE_VERSION_POLL_DELAY_MS = 500L
        const val USER_AGENT = "eBookSender"
        val SHA256_PATTERN = Regex("^[a-fA-F0-9]{64}$")
    }
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
    Unknown
}

private data class PocketBookServerUpdateCheckResult(
    val installedVersion: PocketBookServerVersionInfo?,
    val availableUpdate: AvailablePocketBookServerUpdate?,
    val status: PocketBookServerUpdateStatus
)

private class PocketBookServerUpdateException(
    val reason: PocketBookServerUpdateErrorReason,
    cause: Throwable? = null
) : Exception(cause)

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun File.folderSize(): Long {
    if (!exists()) return 0L
    if (isFile) return length()
    return listFiles().orEmpty().sumOf { it.folderSize() }
}

private fun java.io.InputStream.readLimitedText(maxBytes: Int): String {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = ByteArrayOutputStream()
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        if (output.size() + read > maxBytes) {
            throw PocketBookServerUpdateException(PocketBookServerUpdateErrorReason.InvalidManifest)
        }
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}

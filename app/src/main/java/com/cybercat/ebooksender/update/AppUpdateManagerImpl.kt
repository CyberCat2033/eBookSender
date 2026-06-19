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
import com.cybercat.ebooksender.data.update.AppUpdateErrorReason
import com.cybercat.ebooksender.data.update.AppUpdateManager
import com.cybercat.ebooksender.data.update.AppUpdateState
import com.cybercat.ebooksender.data.update.AppUpdateStatus
import com.cybercat.ebooksender.data.update.AvailableAppUpdate
import com.cybercat.ebooksender.di.ApplicationScope
import com.cybercat.ebooksender.model.update.AppUpdateArtifact
import com.cybercat.ebooksender.model.update.AppUpdateManifest
import dagger.hilt.android.qualifiers.ApplicationContext
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
class AppUpdateManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope
) : AppUpdateManager {
    private val json = Json { ignoreUnknownKeys = true }
    private val packageManager = context.packageManager
    private var checkJob: Job? = null
    private var installJob: Job? = null
    private var statusClearJob: Job? = null
    private var pendingInstallPermissionUpdate: AvailableAppUpdate? = null

    private val _state = MutableStateFlow(
        AppUpdateState(
            currentVersionName = currentVersionName(),
            currentVersionCode = currentVersionCode()
        )
    )
    override val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    init {
        applicationScope.launch(Dispatchers.IO) {
            cleanupInstalledCachedApks()
        }
    }

    override fun checkForUpdates(trigger: AppUpdateCheckTrigger) {
        if (checkJob?.isActive == true) return
        checkJob = applicationScope.launch {
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
        if (installJob?.isActive == true) return
        val update = state.value.availableUpdate ?: return
        installJob = applicationScope.launch {
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
        installJob?.cancel()
    }

    override fun clearStatus() {
        statusClearJob?.cancel()
        _state.update { it.copy(status = null) }
    }

    override suspend fun clearUpdateCache(): Long = withContext(Dispatchers.IO) {
        val dir = updateCacheDir()
        val size = dir.folderSize()
        if (size > 0L) {
            runCatching { dir.deleteRecursively() }
        }
        size
    }

    private fun loadAvailableUpdate(): AvailableAppUpdate? {
        val manifest = fetchManifest()
        validateManifest(manifest)
        if (manifest.versionCode <= currentVersionCode()) return null

        val artifact = selectArtifact(manifest)
            ?: throw AppUpdateException(AppUpdateErrorReason.NoCompatibleArtifact)
        validateArtifact(artifact)
        return AvailableAppUpdate(manifest, artifact)
    }

    private fun fetchManifest(): AppUpdateManifest {
        val url = URL(BuildConfig.UPDATE_MANIFEST_URL)
        if (url.protocol != "https") {
            throw AppUpdateException(AppUpdateErrorReason.InvalidManifest)
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
            if (code !in 200..299) throw AppUpdateException(AppUpdateErrorReason.Network)
            val body = connection.inputStream.use { it.readLimitedText(MAX_MANIFEST_BYTES) }
            return json.decodeFromString(AppUpdateManifest.serializer(), body)
        } catch (exception: SerializationException) {
            throw AppUpdateException(AppUpdateErrorReason.InvalidManifest, exception)
        } catch (exception: IOException) {
            throw AppUpdateException(AppUpdateErrorReason.Network, exception)
        } finally {
            connection.disconnect()
        }
    }

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
        if (apk.isFile && apk.sha256() == expectedHash) {
            cleanupStaleApks(keep = apk)
            return apk
        }

        cleanupStaleApks(keep = null)
        downloadApk(update, apk)
        val actualHash = apk.sha256()
        if (actualHash != expectedHash) {
            runCatching { apk.delete() }
            throw AppUpdateException(AppUpdateErrorReason.ChecksumMismatch)
        }
        cleanupStaleApks(keep = apk)
        return apk
    }

    private suspend fun downloadApk(update: AvailableAppUpdate, target: File) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.download")
        runCatching { temporary.delete() }
        val connection = (URL(update.artifact.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = DOWNLOAD_READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.android.package-archive")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw AppUpdateException(AppUpdateErrorReason.DownloadFailed)
            val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
            var bytesRead = 0L
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        updateDownloadProgress(bytesRead, totalBytes)
                    }
                }
            }
            if (!temporary.renameTo(target)) {
                throw AppUpdateException(AppUpdateErrorReason.DownloadFailed)
            }
        } catch (exception: IOException) {
            throw AppUpdateException(AppUpdateErrorReason.DownloadFailed, exception)
        } finally {
            connection.disconnect()
            runCatching { temporary.delete() }
        }
    }

    private fun updateDownloadProgress(bytesRead: Long, totalBytes: Long?) {
        _state.update { state ->
            state.copy(
                downloadProgress = com.cybercat.ebooksender.data.update.AppUpdateDownloadProgress(
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
        val safeName = update.artifact.fileName
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(updateCacheDir(), "${update.versionCode}-$safeName")
    }

    private fun cleanupStaleApks(keep: File?) {
        val dir = updateCacheDir()
        dir.listFiles().orEmpty().forEach { file ->
            if (file != keep) runCatching { file.deleteRecursively() }
        }
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

    private fun updateCacheDir(): File = File(context.cacheDir, UPDATE_CACHE_DIR)

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
        val shouldClear = when (status) {
            AppUpdateStatus.NoUpdateAvailable,
            AppUpdateStatus.DownloadCanceled,
            is AppUpdateStatus.Error,
            is AppUpdateStatus.ReadyToInstall -> true

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
        const val UPDATE_CACHE_DIR = "update-apks"
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
    val output = java.io.ByteArrayOutputStream()
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        if (output.size() + read > maxBytes) {
            throw AppUpdateException(AppUpdateErrorReason.InvalidManifest)
        }
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}

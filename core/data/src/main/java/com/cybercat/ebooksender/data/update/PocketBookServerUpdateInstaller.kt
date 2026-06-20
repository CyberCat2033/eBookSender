package com.cybercat.ebooksender.data.update

import com.cybercat.ebooksender.data.ftp.FtpGateway
import com.cybercat.ebooksender.data.pocketbook.PocketBookControlClient
import com.cybercat.ebooksender.data.pocketbook.PocketBookServerApplyUpdateRequest
import com.cybercat.ebooksender.data.pocketbook.PocketBookUpdateEndpointUnavailableException
import com.cybercat.ebooksender.model.DEFAULT_FTP_RELATIVE_ROOT_PATH
import com.cybercat.ebooksender.model.RemoteDevice
import com.cybercat.ebooksender.model.update.PocketBookServerVersionInfo
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

internal class PocketBookServerUpdateInstaller(
    private val controlClient: PocketBookControlClient,
    private val ftpGateway: FtpGateway,
    private val artifactRepository: PocketBookServerArtifactRepository,
    private val versionReader: PocketBookServerVersionReader
) {
    suspend fun installUpdate(
        device: RemoteDevice,
        update: AvailablePocketBookServerUpdate,
        onProgress: (PocketBookServerUpdateProgress) -> Unit
    ): PocketBookServerVersionInfo {
        val artifact = update.launcherArtifact
        val totalArtifactBytes = artifact.sizeBytes
        val totalWorkBytes = totalArtifactBytes?.times(2L)
        var completedWorkBytes = 0L

        fun report(phase: PocketBookServerUpdatePhase, bytes: Long) {
            onProgress(
                PocketBookServerUpdateProgress(
                    phase = phase,
                    bytesCompleted = bytes,
                    totalBytes = totalWorkBytes
                )
            )
        }

        report(PocketBookServerUpdatePhase.Downloading, 0L)
        coroutineContext.ensureActive()
        val file = artifactRepository.getVerifiedArtifactFile(artifact) { bytesRead ->
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

        applyStagedUpdateOrFallback(
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

        return versionReader.waitForInstalledVersion(device, update)
            ?: throw PocketBookServerUpdateException(
                PocketBookServerUpdateErrorReason.RestartNotConfirmed
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
    ) {
        val applyResult = controlClient.applyUpdate(
            device = device,
            request = PocketBookServerApplyUpdateRequest(
                sourcePath = PocketBookServerUpdateConfig.POCKETBOOK_MOUNT_ROOT_WITH_SLASH +
                    stagedRemotePath,
                versionName = update.versionName,
                versionCode = update.versionCode,
                releasedAt = update.manifest.releasedAt,
                buildId = update.buildId,
                sha256 = update.launcherArtifact.sha256
            )
        )

        applyResult
            .onSuccess { return }
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
    }

    private fun AvailablePocketBookServerUpdate.stagedLauncherRemotePath(): String {
        val safeName = launcherArtifact.fileName.toSafeUpdateFileName()
        return "${PocketBookServerUpdateConfig.STAGING_REMOTE_DIR}/$versionCode-$safeName"
    }

    private fun String?.toRemoteInstallPath(): String {
        val installPath = this
            ?: throw PocketBookServerUpdateException(
                PocketBookServerUpdateErrorReason.InvalidManifest
            )
        if (!installPath.startsWith(
                PocketBookServerUpdateConfig.POCKETBOOK_MOUNT_ROOT_WITH_SLASH
            )
        ) {
            throw PocketBookServerUpdateException(PocketBookServerUpdateErrorReason.InvalidManifest)
        }
        return installPath.removePrefix(
            PocketBookServerUpdateConfig.POCKETBOOK_MOUNT_ROOT_WITH_SLASH
        )
    }
}

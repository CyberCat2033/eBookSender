package com.cybercat.ebooksender.transfer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import com.cybercat.ebooksender.data.device.DeviceLibraryRefresher
import com.cybercat.ebooksender.data.ftp.FtpGateway
import com.cybercat.ebooksender.localization.LocalizationManager
import com.cybercat.ebooksender.model.BookCategory
import com.cybercat.ebooksender.model.RemoteDevice
import com.cybercat.ebooksender.network.isLocalNetworkBypassBlocked
import com.cybercat.ebooksender.power.ScopedWakeLock
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class TransferForegroundService : Service() {
    @Inject lateinit var ftpGateway: FtpGateway

    @Inject lateinit var transferCoordinator: TransferCoordinator

    @Inject lateinit var localizationManager: LocalizationManager

    @Inject lateinit var deviceLibraryRefresher: DeviceLibraryRefresher

    @Inject lateinit var downloadCacheManager: DownloadCacheManager

    @Inject lateinit var localFileResolver: LocalFileResolver

    @Inject lateinit var uploadPreparationUseCase: UploadPreparationUseCase

    private val transferNotifications by lazy {
        TransferNotificationManager(
            context = this,
            localizationManager = localizationManager
        )
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeRequestId: String? = null
    private var activeTransferJob: Job? = null
    private val transferWakeLock by lazy {
        ScopedWakeLock(
            context = this,
            tag = "TransferForegroundService",
            timeoutMillis = TRANSFER_WAKE_LOCK_TIMEOUT_MILLIS
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        transferNotifications.ensureNotificationChannel()

        if (intent?.action == ACTION_CANCEL_TRANSFER) {
            cancelActiveTransfer(intent.getStringExtra(EXTRA_REQUEST_ID), startId)
            return START_NOT_STICKY
        }

        val request = transferCoordinator.takeRequest(intent?.getStringExtra(EXTRA_REQUEST_ID))
        if (request == null) {
            if (activeTransferJob?.isActive == true) {
                return START_NOT_STICKY
            }
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(
            TransferNotificationManager.FOREGROUND_NOTIFICATION_ID,
            transferNotifications.buildProgressNotification(
                localizationManager.currentStrings.value.transferNotificationUploadingBooks,
                0,
                request.items.size
            )
        )

        transferWakeLock.acquire()
        activeRequestId = request.id
        activeTransferJob = serviceScope.launch {
            try {
                runTransfer(request)
            } finally {
                transferCoordinator.finishActiveRequest(request.id)
                activeRequestId = null
                activeTransferJob = null
                transferWakeLock.release()
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        transferWakeLock.release()
        activeTransferJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun cancelActiveTransfer(requestId: String?, startId: Int) {
        val activeId = activeRequestId
        val activeJob = activeTransferJob
        if (activeJob == null || activeId == null) {
            stopSelf(startId)
            return
        }
        if (requestId == null || requestId == activeId) {
            transferNotifications.notifyProgress(
                localizationManager.currentStrings.value.get("transfer_notification_canceling"),
                completed = 0,
                total = 0
            )
            activeJob.cancel()
        }
    }

    private suspend fun runTransfer(request: TransferRequest) {
        var uploaded = 0
        var failed = 0
        val total = request.items.size
        var canceled = false

        try {
            request.items.forEachIndexed { index, item ->
                currentCoroutineContext().ensureActive()
                transferCoordinator.emit(
                    TransferEvent.ItemStarted(
                        itemId = item.id,
                        completed = index,
                        total = total
                    )
                )
                notifyProgress(
                    localizationManager.currentStrings.value.get(
                        "transfer_notification_uploading_progress",
                        index + 1,
                        total
                    ),
                    index,
                    total
                )

                val result = uploadItem(request, item)
                if (result.isSuccess) {
                    uploaded += 1
                    transferCoordinator.emit(
                        TransferEvent.ItemUploaded(
                            itemId = item.id,
                            completed = uploaded + failed,
                            total = total
                        )
                    )
                    downloadCacheManager.deleteDownloadSource(item.sourceUri)
                } else {
                    val error = result.exceptionOrNull()
                    if (error is CancellationException) {
                        throw error
                    }
                    val message = error?.message
                        ?: localizationManager.currentStrings.value.transferErrorFtpUploadFailed
                    failed += 1
                    transferCoordinator.emit(
                        TransferEvent.ItemFailed(
                            itemId = item.id,
                            message = message,
                            failureReason = error?.localNetworkBypassFailureReason()
                        )
                    )
                }

                notifyProgress(
                    localizationManager.currentStrings.value.get(
                        "transfer_notification_progress_summary",
                        uploaded,
                        failed
                    ),
                    uploaded + failed,
                    total
                )
            }
        } catch (error: CancellationException) {
            canceled = true
        } finally {
            withContext(NonCancellable) {
                if (uploaded > 0) {
                    refreshLibraryAfterTransfer(request.device)
                }
                if (canceled) {
                    transferCoordinator.emit(
                        TransferEvent.Canceled(
                            uploaded = uploaded,
                            failed = failed
                        )
                    )
                    showCanceledNotification(uploaded)
                } else {
                    transferCoordinator.emit(
                        TransferEvent.Completed(
                            uploaded = uploaded,
                            failed = failed
                        )
                    )
                    showFinishedNotification(uploaded, failed)
                }
            }
        }
    }

    private suspend fun uploadItem(
        request: TransferRequest,
        item: TransferUploadItem
    ): Result<Unit> {
        val uri = Uri.parse(item.sourceUri)
        val preparedInput = runCatching {
            uploadPreparationUseCase.prepareUploadInput(uri, item)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            return Result.failure(error)
        }
        val input = preparedInput.openStream() ?: contentResolver.openInputStream(preparedInput.uri)
            ?: run {
                preparedInput.cleanup()
                return Result.failure(
                    IllegalStateException(
                        localizationManager.currentStrings.value.get(
                            "transfer_error_cannot_open_file",
                            item.originalName
                        )
                    )
                )
            }

        val fileSize =
            preparedInput.size.takeIf { it > 0L } ?: localFileResolver.resolveFileSize(uri)

        return try {
            ftpGateway.uploadAtomically(
                device = request.device,
                remoteRelativePath = item.plannedPath,
                input = input,
                onProgress = { bytesRead ->
                    if (fileSize > 0) {
                        val progress = (bytesRead.toFloat() / fileSize.toFloat()).coerceIn(
                            0f,
                            0.99f
                        )
                        transferCoordinator.emit(
                            TransferEvent.ItemProgress(
                                itemId = item.id,
                                progress = progress
                            )
                        )
                    }
                }
            )
        } finally {
            input.close()
            preparedInput.cleanup()
        }
    }

    private suspend fun refreshLibraryAfterTransfer(device: RemoteDevice) {
        val refreshResult = withTimeoutOrNull(DEVICE_LIBRARY_REFRESH_TIMEOUT_MILLIS) {
            deviceLibraryRefresher.refreshAndWait(device)
        }

        when {
            refreshResult == null -> {
                Log.w(
                    TAG,
                    "Timed out refreshing device library after transfer after " +
                        "${DEVICE_LIBRARY_REFRESH_TIMEOUT_MILLIS}ms"
                )
            }

            refreshResult.isFailure -> {
                Log.w(
                    TAG,
                    "Device library refresh failed after transfer",
                    refreshResult.exceptionOrNull()
                )
            }
        }
    }

    private fun notifyProgress(text: String, completed: Int, total: Int) {
        transferNotifications.notifyProgress(text, completed, total)
    }

    private fun showFinishedNotification(uploaded: Int, failed: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        transferNotifications.showFinishedNotification(uploaded, failed)
    }

    private fun showCanceledNotification(uploaded: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        transferNotifications.showCanceledNotification(uploaded)
    }

    private fun Throwable.localNetworkBypassFailureReason(): TransferFailureReason? =
        if (isLocalNetworkBypassBlocked()) {
            TransferFailureReason.LocalNetworkBypassBlocked
        } else {
            null
        }

    companion object {
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val ACTION_CANCEL_TRANSFER =
            "com.cybercat.ebooksender.transfer.CANCEL_TRANSFER"
        private const val TRANSFER_WAKE_LOCK_TIMEOUT_MILLIS = 60 * 60 * 1000L
        private const val DEVICE_LIBRARY_REFRESH_TIMEOUT_MILLIS = 12_000L
        private const val TAG = "TransferForegroundSvc"

        fun createIntent(context: Context, requestId: String): Intent =
            Intent(context, TransferForegroundService::class.java)
                .putExtra(EXTRA_REQUEST_ID, requestId)

        fun createCancelIntent(context: Context, requestId: String): Intent =
            Intent(context, TransferForegroundService::class.java)
                .setAction(ACTION_CANCEL_TRANSFER)
                .putExtra(EXTRA_REQUEST_ID, requestId)
    }
}

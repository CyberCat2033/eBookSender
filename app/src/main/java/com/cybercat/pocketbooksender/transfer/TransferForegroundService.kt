package com.cybercat.pocketbooksender.transfer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import com.cybercat.pocketbooksender.data.ftp.FtpGateway
import com.cybercat.pocketbooksender.data.pocketbook.PocketBookRescanCoordinator
import com.cybercat.pocketbooksender.localization.LocalizationManager
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.power.ScopedWakeLock
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class TransferForegroundService : Service() {
    @Inject lateinit var ftpGateway: FtpGateway

    @Inject lateinit var transferCoordinator: TransferCoordinator

    @Inject lateinit var localizationManager: LocalizationManager

    @Inject lateinit var rescanCoordinator: PocketBookRescanCoordinator

    @Inject lateinit var downloadCacheManager: DownloadCacheManager

    @Inject lateinit var localFileResolver: LocalFileResolver

    private val transferNotifications by lazy {
        TransferNotificationManager(
            context = this,
            localizationManager = localizationManager
        )
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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

        val request = transferCoordinator.takeRequest(intent?.getStringExtra(EXTRA_REQUEST_ID))
        if (request == null) {
            startForeground(
                TransferNotificationManager.FOREGROUND_NOTIFICATION_ID,
                transferNotifications.buildProgressNotification(
                    localizationManager.currentStrings.value.transferNotificationNothingToUpload,
                    0,
                    0
                )
            )
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
        serviceScope.launch {
            try {
                runTransfer(request)
            } finally {
                transferWakeLock.release()
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        transferWakeLock.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun runTransfer(request: TransferRequest) {
        var uploaded = 0
        var failed = 0
        val total = request.items.size

        try {
            request.items.forEachIndexed { index, item ->
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
                    val message = error?.message
                        ?: localizationManager.currentStrings.value.transferErrorFtpUploadFailed
                    failed += 1
                    transferCoordinator.emit(
                        TransferEvent.ItemFailed(
                            itemId = item.id,
                            message = message
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
        } finally {
            if (uploaded > 0) {
                rescanCoordinator.requestRescanAndWait(request.device)
            }
            transferCoordinator.emit(
                TransferEvent.Completed(
                    uploaded = uploaded,
                    failed = failed
                )
            )
            showFinishedNotification(uploaded, failed)
        }
    }

    private suspend fun uploadItem(
        request: TransferRequest,
        item: TransferUploadItem
    ): Result<Unit> {
        val uri = Uri.parse(item.sourceUri)
        val preparedInput = runCatching {
            prepareUploadInput(uri, item)
        }.getOrElse { error ->
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

    private suspend fun prepareUploadInput(
        uri: Uri,
        item: TransferUploadItem
    ): PreparedUploadInput = withContext(Dispatchers.IO) {
        if (!item.needsCbzMetadataRewrite()) {
            return@withContext PreparedUploadInput(
                uri = uri,
                tempFile = null,
                size = localFileResolver.resolveFileSize(uri)
            )
        }

        val tempDir = File(cacheDir, "prepared-upload").apply { mkdirs() }
        val tempFile = File.createTempFile("cbz-metadata-", ".cbz", tempDir)

        try {
            val rootFolder = contentResolver.openInputStream(uri)?.use { input ->
                CbzMetadataRewriter.findSingleCommonRootFolder(input)
            }
            val source = contentResolver.openInputStream(uri)
                ?: throw IllegalStateException(
                    localizationManager.currentStrings.value.get(
                        "transfer_error_cannot_open_file",
                        item.originalName
                    )
                )
            source.use { input ->
                tempFile.outputStream().use { output ->
                    CbzMetadataRewriter.rewrite(
                        input = input,
                        output = output,
                        metadata = item.toCbzMetadata(),
                        rootFolder = rootFolder
                    )
                }
            }
            PreparedUploadInput(uri = uri, tempFile = tempFile, size = tempFile.length())
        } catch (error: Throwable) {
            tempFile.delete()
            throw error
        }
    }

    private fun notifyProgress(text: String, completed: Int, total: Int) {
        transferNotifications.notifyProgress(text, completed, total)
    }

    private fun showFinishedNotification(uploaded: Int, failed: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        transferNotifications.showFinishedNotification(uploaded, failed)
    }

    companion object {
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val TRANSFER_WAKE_LOCK_TIMEOUT_MILLIS = 60 * 60 * 1000L

        fun createIntent(context: Context, requestId: String): Intent =
            Intent(context, TransferForegroundService::class.java)
                .putExtra(EXTRA_REQUEST_ID, requestId)
    }
}

private data class PreparedUploadInput(val uri: Uri, val tempFile: File?, val size: Long) {
    fun openStream(): InputStream? = tempFile?.inputStream()

    fun cleanup() {
        tempFile?.delete()
    }
}

private fun TransferUploadItem.needsCbzMetadataRewrite(): Boolean =
    category == BookCategory.Manga && extension.equals("cbz", ignoreCase = true)

private fun TransferUploadItem.toCbzMetadata(): CbzMetadata = CbzMetadata(
    title = plannedPath.fileNameWithoutExtension().ifBlank { title },
    series = mangaSeries,
    number = mangaVolume
)

private fun String.fileNameWithoutExtension(): String {
    val fileName = trim('/').substringAfterLast('/')
    val dotIndex = fileName.lastIndexOf('.')
    return if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
}

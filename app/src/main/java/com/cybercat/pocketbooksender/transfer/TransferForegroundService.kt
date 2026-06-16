package com.cybercat.pocketbooksender.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cybercat.pocketbooksender.MainActivity
import com.cybercat.pocketbooksender.R
import com.cybercat.pocketbooksender.data.ftp.FtpGateway
import com.cybercat.pocketbooksender.data.pocketbook.PocketBookRescanCoordinator
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.power.ScopedWakeLock
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicInteger
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
    @Inject lateinit var localizationManager: com.cybercat.pocketbooksender.localization.LocalizationManager
    @Inject lateinit var rescanCoordinator: PocketBookRescanCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val transferWakeLock by lazy {
        ScopedWakeLock(
            context = this,
            tag = "TransferForegroundService",
            timeoutMillis = TransferWakeLockTimeoutMillis,
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()

        val request = transferCoordinator.takeRequest(intent?.getStringExtra(EXTRA_REQUEST_ID))
        if (request == null) {
            startForeground(NOTIFICATION_ID, buildProgressNotification(localizationManager.currentStrings.value.transferNotificationNothingToUpload, 0, 0))
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            buildProgressNotification(localizationManager.currentStrings.value.transferNotificationUploadingBooks, 0, request.items.size),
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
                        total = total,
                    ),
                )
                notifyProgress(localizationManager.currentStrings.value.get("transfer_notification_uploading_progress", index + 1, total), index, total)

                val result = uploadItem(request, item)
                result
                    .onSuccess {
                        uploaded += 1
                        transferCoordinator.emit(
                            TransferEvent.ItemUploaded(
                                itemId = item.id,
                                completed = uploaded + failed,
                                total = total,
                            ),
                        )
                    }
                    .onFailure { error ->
                        failed += 1
                        transferCoordinator.emit(
                            TransferEvent.ItemFailed(
                                itemId = item.id,
                                message = error.message ?: localizationManager.currentStrings.value.transferErrorFtpUploadFailed,
                            ),
                        )
                    }

                notifyProgress(localizationManager.currentStrings.value.get("transfer_notification_progress_summary", uploaded, failed), uploaded + failed, total)
            }
        } finally {
            if (uploaded > 0) {
                rescanCoordinator.requestRescanAndWait(request.device)
            }
            transferCoordinator.emit(
                TransferEvent.Completed(
                    uploaded = uploaded,
                    failed = failed,
                ),
            )
            showFinishedNotification(uploaded, failed)
        }
    }

    private suspend fun uploadItem(
        request: TransferRequest,
        item: TransferUploadItem,
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
                return Result.failure(IllegalStateException(localizationManager.currentStrings.value.get("transfer_error_cannot_open_file", item.originalName)))
            }

        val fileSize = preparedInput.size.takeIf { it > 0L } ?: getUriSize(uri)

        return try {
            ftpGateway.uploadAtomically(
                device = request.device,
                remoteRelativePath = item.plannedPath,
                input = input,
                onProgress = { bytesRead ->
                    if (fileSize > 0) {
                        val progress = (bytesRead.toFloat() / fileSize.toFloat()).coerceIn(0f, 0.99f)
                        transferCoordinator.emit(
                            TransferEvent.ItemProgress(
                                itemId = item.id,
                                progress = progress,
                            ),
                        )
                    }
                },
            )
        } finally {
            input.close()
            preparedInput.cleanup()
        }
    }

    private suspend fun prepareUploadInput(
        uri: Uri,
        item: TransferUploadItem,
    ): PreparedUploadInput = withContext(Dispatchers.IO) {
        if (!item.needsCbzMetadataRewrite()) {
            return@withContext PreparedUploadInput(uri = uri, tempFile = null, size = getUriSize(uri))
        }

        val tempDir = File(cacheDir, "prepared-upload").apply { mkdirs() }
        val tempFile = File.createTempFile("cbz-metadata-", ".cbz", tempDir)

        try {
            val rootFolder = contentResolver.openInputStream(uri)?.use { input ->
                CbzMetadataRewriter.findSingleCommonRootFolder(input)
            }
            val source = contentResolver.openInputStream(uri)
                ?: throw IllegalStateException(localizationManager.currentStrings.value.get("transfer_error_cannot_open_file", item.originalName))
            source.use { input ->
                tempFile.outputStream().use { output ->
                    CbzMetadataRewriter.rewrite(
                        input = input,
                        output = output,
                        metadata = item.toCbzMetadata(),
                        rootFolder = rootFolder,
                    )
                }
            }
            PreparedUploadInput(uri = uri, tempFile = tempFile, size = tempFile.length())
        } catch (error: Throwable) {
            tempFile.delete()
            throw error
        }
    }

    private fun getUriSize(uri: Uri): Long {
        if (uri.scheme == "file") {
            return try {
                java.io.File(uri.path.orEmpty()).length()
            } catch (e: Exception) {
                -1L
            }
        }
        var size = -1L
        try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (index != -1) {
                        size = cursor.getLong(index)
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        if (size <= 0L) {
            try {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                    size = it.length
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        return size
    }

    private fun notifyProgress(
        text: String,
        completed: Int,
        total: Int,
    ) {
        notificationManager().notify(
            NOTIFICATION_ID,
            buildProgressNotification(text, completed, total),
        )
    }

    private fun showFinishedNotification(uploaded: Int, failed: Int) {
        val strings = localizationManager.currentStrings.value
        val text = if (failed == 0) {
            strings.get("transfer_notification_complete_success", uploaded)
        } else {
            strings.get("transfer_notification_progress_summary", uploaded, failed)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)

        notificationManager().notify(
            nextCompletionNotificationId(),
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_upload)
                .setContentTitle(strings.transferNotificationCompleteTitle)
                .setContentText(text)
                .setContentIntent(contentIntent())
                .setAutoCancel(true)
                .setOngoing(false)
                .build(),
        )
    }

    private fun buildProgressNotification(
        text: String,
        completed: Int,
        total: Int,
    ): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_upload)
            .setContentTitle(localizationManager.currentStrings.value.transferNotificationTitle)
            .setContentText(text)
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, completed, total == 0)
            .build()

    private fun contentIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            flags,
        )
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Book transfers",
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "book_transfers"
        private const val NOTIFICATION_ID = 2001
        private const val COMPLETION_NOTIFICATION_ID_START = 2100
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val TransferWakeLockTimeoutMillis = 60 * 60 * 1000L
        private val completionNotificationIds = AtomicInteger(COMPLETION_NOTIFICATION_ID_START)

        private fun nextCompletionNotificationId(): Int =
            completionNotificationIds.getAndIncrement()

        fun createIntent(context: Context, requestId: String): Intent =
            Intent(context, TransferForegroundService::class.java)
                .putExtra(EXTRA_REQUEST_ID, requestId)
    }
}

private data class PreparedUploadInput(
    val uri: Uri,
    val tempFile: File?,
    val size: Long,
) {
    fun openStream(): InputStream? =
        tempFile?.inputStream()

    fun cleanup() {
        tempFile?.delete()
    }
}

private fun TransferUploadItem.needsCbzMetadataRewrite(): Boolean =
    category == BookCategory.Manga && extension.equals("cbz", ignoreCase = true)

private fun TransferUploadItem.toCbzMetadata(): CbzMetadata =
    CbzMetadata(
        title = plannedPath.fileNameWithoutExtension().ifBlank { title },
        series = mangaSeries,
        number = mangaVolume,
    )

private fun String.fileNameWithoutExtension(): String {
    val fileName = trim('/').substringAfterLast('/')
    val dotIndex = fileName.lastIndexOf('.')
    return if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
}

package com.cybercat.pocketbooksender.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cybercat.pocketbooksender.MainActivity
import com.cybercat.pocketbooksender.R
import com.cybercat.pocketbooksender.data.ftp.FtpGateway
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TransferForegroundService : Service() {
    @Inject lateinit var ftpGateway: FtpGateway

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()

        val request = TransferCoordinator.takeRequest(intent?.getStringExtra(EXTRA_REQUEST_ID))
        if (request == null) {
            startForeground(NOTIFICATION_ID, buildProgressNotification("Nothing to upload", 0, 0))
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            buildProgressNotification("Uploading books", 0, request.items.size),
        )

        serviceScope.launch {
            runTransfer(request)
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun runTransfer(request: TransferRequest) {
        var uploaded = 0
        var failed = 0
        val total = request.items.size

        try {
            request.items.forEachIndexed { index, item ->
                TransferCoordinator.emit(
                    TransferEvent.ItemStarted(
                        itemId = item.id,
                        completed = index,
                        total = total,
                    ),
                )
                notifyProgress("Uploading ${index + 1} of $total", index, total)

                val result = uploadItem(request, item)
                result
                    .onSuccess {
                        uploaded += 1
                        TransferCoordinator.emit(
                            TransferEvent.ItemUploaded(
                                itemId = item.id,
                                completed = uploaded + failed,
                                total = total,
                            ),
                        )
                    }
                    .onFailure { error ->
                        failed += 1
                        TransferCoordinator.emit(
                            TransferEvent.ItemFailed(
                                itemId = item.id,
                                message = error.message ?: "FTP upload failed",
                            ),
                        )
                    }

                notifyProgress("Uploaded $uploaded, failed $failed", uploaded + failed, total)
            }
        } finally {
            TransferCoordinator.emit(
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
        val input = contentResolver.openInputStream(uri)
            ?: return Result.failure(IllegalStateException("Cannot open ${item.originalName}"))

        val fileSize = getUriSize(uri)

        return try {
            ftpGateway.uploadAtomically(
                device = request.device,
                remoteRelativePath = item.plannedPath,
                input = input,
                onProgress = { bytesRead ->
                    if (fileSize > 0) {
                        val progress = (bytesRead.toFloat() / fileSize.toFloat()).coerceIn(0f, 0.99f)
                        TransferCoordinator.emit(
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
        val text = if (failed == 0) {
            "Uploaded $uploaded files"
        } else {
            "Uploaded $uploaded, failed $failed"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }

        notificationManager().notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_upload)
                .setContentTitle("PocketBook transfer complete")
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
            .setContentTitle("PocketBook Sender")
            .setContentText(text)
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, completed, total == 0)
            .build()

    private fun contentIntent(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }

        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            flags,
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

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
        private const val EXTRA_REQUEST_ID = "request_id"

        fun createIntent(context: Context, requestId: String): Intent =
            Intent(context, TransferForegroundService::class.java)
                .putExtra(EXTRA_REQUEST_ID, requestId)
    }
}

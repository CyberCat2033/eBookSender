package com.cybercat.ebooksender.transfer

import android.content.Context
import com.cybercat.ebooksender.model.UploadItem
import com.cybercat.ebooksender.model.UploadItemEntity
import com.cybercat.ebooksender.model.UploadStatus
import com.cybercat.ebooksender.model.toEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class QueueStorageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun restore(): List<UploadItemEntity> {
        val file = queueStoreFile()
        if (!file.isFile) return emptyList()

        return runCatching {
            QueueJson.decodeFromString<List<UploadItemEntity>>(file.readText())
        }.getOrDefault(emptyList())
    }

    fun persist(items: List<UploadItem>) {
        val file = queueStoreFile()
        val payload = QueueJson.encodeToString(
            items
                .filter { it.status != UploadStatus.Uploaded }
                .map { item -> item.toEntity() },
        )

        try {
            file.parentFile?.mkdirs()
            val tempFile = File(file.parentFile, "${file.name}.tmp")
            tempFile.writeText(payload)
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        } catch (_: IOException) {
            // Queue persistence is best effort; in-memory queue remains authoritative.
        }
    }

    private fun queueStoreFile(): File =
        File(context.filesDir, QueueStoreFileName)

    private companion object {
        val QueueJson = Json {
            ignoreUnknownKeys = true
        }

        const val QueueStoreFileName = "upload_queue.json"
    }
}

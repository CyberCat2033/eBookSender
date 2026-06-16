package com.cybercat.pocketbooksender.transfer

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFileResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun resolveDisplayName(uri: Uri): String? =
        runCatching {
            queryOpenableColumn(uri, OpenableColumns.DISPLAY_NAME) { index ->
                getString(index)
            }
        }.getOrNull()

    fun resolveFileSize(uri: Uri): Long =
        runCatching {
            queryOpenableColumn(uri, OpenableColumns.SIZE) { index ->
                getLong(index)
            } ?: 0L
        }.getOrDefault(0L)

    fun persistReadPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    fun canRead(uri: Uri): Boolean =
        when (uri.scheme?.lowercase()) {
            null, "file" -> canReadFile(uri)
            "content" -> canOpenInputStream(uri)
            else -> canOpenInputStream(uri)
        }

    private inline fun <T> queryOpenableColumn(
        uri: Uri,
        columnName: String,
        readColumn: Cursor.(Int) -> T,
    ): T? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(columnName)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.readColumn(index)
            } else {
                null
            }
        }

    private fun canReadFile(uri: Uri): Boolean {
        val path = uri.path.orEmpty()
        return path.isNotBlank() && File(path).let { file -> file.isFile && file.canRead() }
    }

    private fun canOpenInputStream(uri: Uri): Boolean =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { } ?: error("Cannot open source")
        }.isSuccess
}

package com.cybercat.ebooksender.transfer

import android.content.Context
import android.net.Uri
import com.cybercat.ebooksender.localization.LocalizationManager
import com.cybercat.ebooksender.model.BookCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class UploadPreparationUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localFileResolver: LocalFileResolver,
    private val localizationManager: LocalizationManager
) {
    suspend fun prepareUploadInput(uri: Uri, item: TransferUploadItem): PreparedUploadInput =
        withContext(Dispatchers.IO) {
            if (!item.needsCbzMetadataRewrite()) {
                return@withContext PreparedUploadInput(
                    uri = uri,
                    size = localFileResolver.resolveFileSize(uri),
                    openPreparedStream = null
                )
            }

            val size = localFileResolver.resolveFileSize(uri)
            val metadata = item.toCbzMetadata()
            val originalName = item.originalName
            PreparedUploadInput(
                uri = uri,
                size = size,
                openPreparedStream = {
                    CbzRewriteInputStream(
                        sourceName = originalName,
                        openSource = {
                            context.contentResolver.openInputStream(uri)
                                ?: throw IllegalStateException(
                                    localizationManager.currentStrings.value.get(
                                        "transfer_error_cannot_open_file",
                                        originalName
                                    )
                                )
                        },
                        metadata = metadata
                    )
                }
            )
        }
}

data class PreparedUploadInput(
    val uri: Uri,
    val size: Long,
    private val openPreparedStream: (() -> InputStream)?
) {
    fun openStream(): InputStream? = openPreparedStream?.invoke()

    fun cleanup() = Unit
}

private class CbzRewriteInputStream(
    sourceName: String,
    openSource: () -> InputStream,
    metadata: CbzMetadata
) : InputStream() {
    private val writerError = AtomicReference<Throwable?>()
    private val input = PipedInputStream(PIPE_BUFFER_SIZE)
    private val output = PipedOutputStream(input)
    private val writer = Thread(
        {
            try {
                openSource().use { source ->
                    output.use { target ->
                        CbzMetadataRewriter.rewrite(
                            input = source,
                            output = target,
                            metadata = metadata,
                            rootFolder = null
                        )
                    }
                }
            } catch (error: Throwable) {
                writerError.set(error)
                runCatching { output.close() }
            }
        },
        "cbz-rewrite-$sourceName"
    ).apply {
        isDaemon = true
        start()
    }

    override fun read(): Int = try {
        input.read().also {
            if (it < 0) throwWriterErrorIfPresent()
        }
    } catch (error: IOException) {
        throwWriterErrorIfPresent(error)
        throw error
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = try {
        input.read(buffer, offset, length).also {
            if (it < 0) throwWriterErrorIfPresent()
        }
    } catch (error: IOException) {
        throwWriterErrorIfPresent(error)
        throw error
    }

    override fun close() {
        input.close()
        writer.interrupt()
    }

    private fun throwWriterErrorIfPresent(readError: IOException? = null) {
        val error = writerError.get() ?: return
        if (readError != null) {
            readError.initCause(error)
            throw readError
        }
        throw IOException("Cannot rewrite CBZ metadata", error)
    }

    private companion object {
        const val PIPE_BUFFER_SIZE = 256 * 1024
    }
}

private fun TransferUploadItem.needsCbzMetadataRewrite(): Boolean =
    category == BookCategory.Manga && extension.equals("cbz", ignoreCase = true)

private fun TransferUploadItem.toCbzMetadata(): CbzMetadata = CbzMetadata(
    title = plannedPath.fileNameWithoutExtension().ifBlank { title },
    series = mangaSeries,
    number = seriesIndex ?: mangaVolume
)

private fun String.fileNameWithoutExtension(): String {
    val fileName = trim('/').substringAfterLast('/')
    val dotIndex = fileName.lastIndexOf('.')
    return if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
}

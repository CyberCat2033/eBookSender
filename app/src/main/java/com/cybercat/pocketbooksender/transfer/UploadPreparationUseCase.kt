package com.cybercat.pocketbooksender.transfer

import android.content.Context
import android.net.Uri
import com.cybercat.pocketbooksender.localization.LocalizationManager
import com.cybercat.pocketbooksender.model.BookCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
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
                    tempFile = null,
                    size = localFileResolver.resolveFileSize(uri)
                )
            }

            val tempDir = File(context.cacheDir, "prepared-upload").apply { mkdirs() }
            val tempFile = File.createTempFile("cbz-metadata-", ".cbz", tempDir)

            try {
                val rootFolder = context.contentResolver.openInputStream(uri)?.use { input ->
                    CbzMetadataRewriter.findSingleCommonRootFolder(input)
                }
                val source = context.contentResolver.openInputStream(uri)
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
}

data class PreparedUploadInput(val uri: Uri, val tempFile: File?, val size: Long) {
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

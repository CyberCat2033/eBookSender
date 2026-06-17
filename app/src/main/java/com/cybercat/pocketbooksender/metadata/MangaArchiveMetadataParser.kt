package com.cybercat.pocketbooksender.metadata

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.cybercat.pocketbooksender.archive.ArchiveFormat
import com.cybercat.pocketbooksender.archive.ArchiveFormatDetector
import com.cybercat.pocketbooksender.domain.NaturalSort
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

class MangaArchiveMetadataParser @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun extract(uri: Uri, fallbackTitle: String): BookMetadata {
        val format = open(uri).use(ArchiveFormatDetector::detect)
        val preview = when (format) {
            ArchiveFormat.Zip -> readFirstNaturalZipImage(uri)
            ArchiveFormat.Rar4 -> extractRarPreview(uri)
            ArchiveFormat.Rar5,
            ArchiveFormat.Unknown -> null
        }

        return BookMetadata(title = fallbackTitle, preview = preview)
    }

    private fun readFirstNaturalZipImage(uri: Uri): Bitmap? {
        open(uri).use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }

                    val normalizedName = entry.name
                        .replace('\\', '/')
                        .substringAfterLast("/")
                        .trim()

                    if (normalizedName.lowercase().isImageName()) {
                        val bitmap = MetadataPreviewDecoder.decodeBitmap(
                            zip.readCurrentEntry(METADATA_MAX_IMAGE_BYTES),
                        )
                        if (bitmap != null) return bitmap
                        continue
                    }
                    zip.closeEntry()
                }
            }
        }

        return null
    }

    private fun extractRarPreview(uri: Uri): Bitmap? =
        runCatching {
            open(uri).use { input ->
                Archive(input).use { archive ->
                    var bestHeader: FileHeader? = null
                    var bestBytes: ByteArray? = null

                    while (true) {
                        val header = archive.nextFileHeader() ?: break
                        if (header.isDirectory) continue
                        val name = header.normalizedName()
                        if (name.lowercase().isImageName()) {
                            val currentBest = bestHeader?.normalizedName()
                            if (currentBest == null || NaturalSort.compare(name, currentBest) < 0) {
                                bestHeader = header
                                bestBytes = archive.getInputStream(header)
                                    .use { it.readBytesLimited(METADATA_MAX_IMAGE_BYTES) }
                            }
                        }
                    }
                    bestBytes?.let(MetadataPreviewDecoder::decodeBitmap)
                }
            }
        }.getOrNull()

    private fun open(uri: Uri): InputStream =
        requireNotNull(context.contentResolver.openInputStream(uri)) {
            "Cannot open $uri"
        }

    private fun FileHeader.normalizedName(): String =
        fileName
            .replace('\\', '/')
            .substringAfterLast("/")
            .trim()
}

package com.cybercat.pocketbooksender.metadata

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.cybercat.pocketbooksender.domain.bookTitleWithoutExtension
import com.cybercat.pocketbooksender.domain.contentExtension
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fb2MetadataParser: Fb2MetadataParser,
    private val epubMetadataParser: EpubMetadataParser,
    private val docxMetadataParser: DocxMetadataParser,
    private val pdfMetadataParser: PdfMetadataParser,
    private val mangaArchiveMetadataParser: MangaArchiveMetadataParser
) : MetadataExtractor {
    override suspend fun extract(sourceUri: String, displayName: String): BookMetadata =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(sourceUri)
            val fallbackTitle = displayName.bookTitleWithoutExtension()

            runCatching {
                when (displayName.contentExtension()) {
                    "fb2" -> fb2MetadataParser.extract(uri, displayName, fallbackTitle) { open(it) }
                    "epub" -> epubMetadataParser.extract(uri, fallbackTitle) { open(it) }
                    "docx" -> docxMetadataParser.extract(uri, fallbackTitle) { open(it) }
                    "mobi", "azw3" -> extractMobi(uri, fallbackTitle)
                    "pdf" -> pdfMetadataParser.extract(uri, fallbackTitle)
                    "cbz", "cbr" -> mangaArchiveMetadataParser.extract(uri, fallbackTitle)
                    else -> BookMetadata(title = fallbackTitle)
                }
            }.getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                BookMetadata(title = fallbackTitle)
            }
        }

    private fun extractMobi(uri: Uri, fallbackTitle: String): BookMetadata {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return BookMetadata(title = fallbackTitle)
        val metadata = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            MobiMetadataParser.parse(
                channel = input.channel,
                declaredSize = descriptor.statSize,
                fallbackTitle = fallbackTitle
            )
        } ?: return BookMetadata(title = fallbackTitle)

        return BookMetadata(
            title = metadata.title,
            authors = metadata.authors,
            description = metadata.description,
            preview = metadata.coverBytes?.let(MetadataPreviewDecoder::decodeBitmap),
            language = metadata.language,
            year = metadata.year,
            publisher = metadata.publisher
        )
    }

    private fun open(uri: Uri): InputStream =
        requireNotNull(context.contentResolver.openInputStream(uri)) {
            "Cannot open $uri"
        }
}

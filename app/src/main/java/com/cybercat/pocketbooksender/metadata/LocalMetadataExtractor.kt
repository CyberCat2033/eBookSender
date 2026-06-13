package com.cybercat.pocketbooksender.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.cybercat.pocketbooksender.archive.ArchiveFormat
import com.cybercat.pocketbooksender.archive.ArchiveFormatDetector
import com.cybercat.pocketbooksender.domain.NaturalSort
import com.cybercat.pocketbooksender.domain.bookTitleWithoutExtension
import com.cybercat.pocketbooksender.domain.contentExtension
import com.cybercat.pocketbooksender.domain.isZipWrappedBook
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class LocalMetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) : MetadataExtractor {
    override suspend fun extract(sourceUri: String, displayName: String): BookMetadata =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(sourceUri)
            val fallbackTitle = displayName.bookTitleWithoutExtension()

            runCatching {
                when (displayName.contentExtension()) {
                    "fb2" -> extractFb2(uri, displayName, fallbackTitle)
                    "epub" -> extractEpub(uri, fallbackTitle)
                    "pdf" -> extractPdf(uri, fallbackTitle)
                    "cbz", "cbr" -> extractMangaArchive(uri, fallbackTitle)
                    else -> BookMetadata(title = fallbackTitle)
                }
            }.getOrElse {
                BookMetadata(title = fallbackTitle)
            }
        }

    private fun extractFb2(uri: Uri, displayName: String, fallbackTitle: String): BookMetadata {
        val bytes = if (displayName.isZipWrappedBook()) {
            open(uri).use { input ->
                ZipInputStream(input).use { zip ->
                    generateSequence { zip.nextEntry }
                        .firstOrNull { !it.isDirectory && it.name.lowercase().endsWith(".fb2") }
                        ?.let { zip.readCurrentEntry(MAX_TEXT_BYTES) }
                }
            }
        } else {
            open(uri).use { it.readBytesLimited(MAX_TEXT_BYTES) }
        }

        return bytes?.let { parseFb2(it, fallbackTitle) } ?: BookMetadata(title = fallbackTitle)
    }

    private fun parseFb2(bytes: ByteArray, fallbackTitle: String): BookMetadata {
        val parser = newParser(ByteArrayInputStream(bytes))
        var inTitleInfo = false
        var inAuthor = false
        var title: String? = null
        var coverId: String? = null
        val authors = mutableListOf<String>()
        val authorParts = mutableListOf<String>()
        var preview: Bitmap? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "title-info" -> inTitleInfo = true
                    "author" -> if (inTitleInfo) {
                        inAuthor = true
                        authorParts.clear()
                    }
                    "book-title" -> if (inTitleInfo) title = parser.nextTextSafe()
                    "first-name", "middle-name", "last-name", "nickname" -> {
                        if (inTitleInfo && inAuthor) authorParts += parser.nextTextSafe()
                    }
                    "image" -> if (inTitleInfo && coverId == null) {
                        coverId = parser.getAttributeValue("http://www.w3.org/1999/xlink", "href")
                            ?: parser.getAttributeValue(null, "href")
                        coverId = coverId?.removePrefix("#")
                    }
                    "binary" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val contentType = parser.getAttributeValue(null, "content-type").orEmpty()
                        val looksLikeImage = contentType.startsWith("image/") ||
                            id?.lowercase().orEmpty().isImageName()
                        if (id != null && looksLikeImage) {
                            val bitmap = decodeBase64Bitmap(parser.nextTextSafe())
                            if (id == coverId || preview == null) {
                                preview = bitmap
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "author" -> if (inAuthor) {
                        authorParts.joinToString(" ")
                            .replace(Regex("""\s+"""), " ")
                            .trim()
                            .takeIf { it.isNotBlank() }
                            ?.let(authors::add)
                        inAuthor = false
                    }
                    "title-info" -> inTitleInfo = false
                }
            }
        }

        return BookMetadata(
            title = title?.ifBlank { fallbackTitle } ?: fallbackTitle,
            authors = authors.distinct(),
            preview = preview,
        )
    }

    private fun extractEpub(uri: Uri, fallbackTitle: String): BookMetadata {
        val opfMetadata = open(uri).use { input ->
            ZipInputStream(input).use { zip ->
                var opfEntryName: String? = null
                var opfBytes: ByteArray? = null
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (entry.name.lowercase().endsWith(".opf")) {
                        opfEntryName = entry.name
                        opfBytes = zip.readCurrentEntry(MAX_TEXT_BYTES)
                        break
                    }
                    zip.closeEntry()
                }
                if (opfBytes != null && opfEntryName != null) {
                    val parsed = parseOpf(opfBytes, fallbackTitle)
                    Pair(opfEntryName, parsed)
                } else null
            }
        } ?: return BookMetadata(title = fallbackTitle)

        val (opfEntryName, parsed) = opfMetadata
        val opfDir = opfEntryName.substringBeforeLast('/', missingDelimiterValue = "")
        val coverPath = parsed.coverHref?.let { resolveZipPath(opfDir, it) }

        var coverBytes: ByteArray? = null
        if (coverPath != null) {
            open(uri).use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        if (entry.name.equals(coverPath, ignoreCase = true)) {
                            coverBytes = zip.readCurrentEntry(MAX_IMAGE_BYTES)
                            break
                        }
                        zip.closeEntry()
                    }
                }
            }
        }

        if (coverBytes == null) {
            val fallbackImageName = open(uri).use { input ->
                ZipInputStream(input).use { zip ->
                    val imageNames = mutableListOf<String>()
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (!entry.isDirectory && entry.name.lowercase().isImageName()) {
                            imageNames += entry.name
                        }
                        zip.closeEntry()
                    }
                    chooseBestFallbackImageName(imageNames)
                }
            }

            if (fallbackImageName != null) {
                open(uri).use { input ->
                    ZipInputStream(input).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            if (entry.name == fallbackImageName) {
                                coverBytes = zip.readCurrentEntry(MAX_IMAGE_BYTES)
                                break
                            }
                            zip.closeEntry()
                        }
                    }
                }
            }
        }

        return BookMetadata(
            title = parsed.title,
            authors = parsed.authors,
            preview = coverBytes?.let(::decodeBitmap),
        )
    }

    private fun chooseBestFallbackImageName(names: List<String>): String? {
        if (names.isEmpty()) return null
        val candidates = names.filter { name ->
            val lower = name.lowercase().substringAfterLast('/')
            lower.contains("cover") || lower.contains("front") || lower.contains("folder") || lower.contains("title")
        }
        if (candidates.isNotEmpty()) {
            return candidates.sortedWith(NaturalSort.by { it }).first()
        }
        return names.sortedWith(NaturalSort.by { it }).first()
    }

    private fun parseOpf(bytes: ByteArray, fallbackTitle: String): OpfMetadata {
        val parser = newParser(ByteArrayInputStream(bytes))
        var title: String? = null
        val authors = mutableListOf<String>()
        var coverId: String? = null
        val manifest = mutableMapOf<String, String>()
        var propertiesCoverHref: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name.substringAfter(':')) {
                "title" -> if (title == null) title = parser.nextTextSafe()
                "creator" -> authors += parser.nextTextSafe()
                "meta" -> {
                    if (parser.getAttributeValue(null, "name") == "cover") {
                        coverId = parser.getAttributeValue(null, "content")
                    }
                }
                "item" -> {
                    val id = parser.getAttributeValue(null, "id")
                    val href = parser.getAttributeValue(null, "href")
                    val properties = parser.getAttributeValue(null, "properties").orEmpty()
                    if (id != null && href != null) manifest[id] = href
                    if (href != null && properties.split(' ').contains("cover-image")) {
                        propertiesCoverHref = href
                    }
                }
            }
        }

        return OpfMetadata(
            title = title?.ifBlank { fallbackTitle } ?: fallbackTitle,
            authors = authors.filter { it.isNotBlank() }.distinct(),
            coverHref = propertiesCoverHref ?: coverId?.let(manifest::get),
        )
    }

    private fun extractPdf(uri: Uri, fallbackTitle: String): BookMetadata {
        val preview = context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            renderFirstPdfPage(descriptor)
        }
        return BookMetadata(title = fallbackTitle, preview = preview)
    }

    private fun renderFirstPdfPage(descriptor: ParcelFileDescriptor): Bitmap? {
        return runCatching {
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val width = PREVIEW_WIDTH
                    val height = (width * page.height.toFloat() / page.width.toFloat()).toInt()
                        .coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }.getOrNull()
    }

    private fun extractMangaArchive(uri: Uri, fallbackTitle: String): BookMetadata {
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
        var bestEntryName: String? = null

        open(uri).use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue

                    val normalizedName = entry.name
                        .replace('\\', '/')
                        .substringAfterLast("/")
                        .trim()

                    if (normalizedName.lowercase().isImageName()) {
                        val currentBest = bestEntryName?.replace('\\', '/')?.substringAfterLast("/")?.trim()
                        if (currentBest == null || NaturalSort.compare(normalizedName, currentBest) < 0) {
                            bestEntryName = entry.name
                        }
                    }
                    zip.closeEntry()
                }
            }
        }

        if (bestEntryName == null) return null

        var bytes: ByteArray? = null
        open(uri).use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == bestEntryName) {
                        bytes = zip.readCurrentEntry(MAX_IMAGE_BYTES)
                        break
                    }
                    zip.closeEntry()
                }
            }
        }

        return bytes?.let(::decodeBitmap)
    }

    private fun extractRarPreview(uri: Uri): Bitmap? =
        runCatching {
            open(uri).use { input ->
                Archive(input).use { archive ->
                    val firstImage = archive.fileHeaders()
                        .filterNot(FileHeader::isDirectory)
                        .filter { it.normalizedName().isImageName() }
                        .sortedWith(NaturalSort.by { it.normalizedName() })
                        .firstOrNull()

                    firstImage?.let { header ->
                        archive.getInputStream(header).use { imageInput ->
                            decodeBitmap(imageInput.readBytesLimited(MAX_IMAGE_BYTES))
                        }
                    }
                }
            }
        }.getOrNull()

    private fun open(uri: Uri): InputStream =
        requireNotNull(context.contentResolver.openInputStream(uri)) {
            "Cannot open $uri"
        }

    private fun newParser(input: InputStream): XmlPullParser =
        XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(input, null)
        }

    private fun XmlPullParser.nextTextSafe(): String =
        runCatching { nextText().trim() }.getOrDefault("")

    private fun decodeBase64Bitmap(value: String): Bitmap? =
        runCatching {
            val bytes = Base64.decode(value.replace(Regex("""\s+"""), ""), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()

    private fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun ZipInputStream.readCurrentEntry(limit: Int): ByteArray =
        readBytesLimited(limit)

    private fun Archive.fileHeaders(): List<FileHeader> {
        val headers = mutableListOf<FileHeader>()
        while (true) {
            val header = nextFileHeader() ?: break
            headers += header
        }
        return headers
    }

    private fun FileHeader.normalizedName(): String =
        fileNameString
            .replace('\\', '/')
            .substringAfterLast("/")
            .trim()

    private fun String.isImageName(): Boolean =
        endsWith(".jpg") ||
            endsWith(".jpeg") ||
            endsWith(".png") ||
            endsWith(".webp")

    private fun resolveZipPath(baseDir: String, href: String): String {
        val decoded = runCatching {
            URLDecoder.decode(href.substringBefore('#'), Charsets.UTF_8.name())
        }.getOrElse {
            href.substringBefore('#')
        }
        val raw = if (baseDir.isBlank()) decoded else "$baseDir/$decoded"
        val parts = mutableListOf<String>()
        raw.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += part
            }
        }
        return parts.joinToString("/")
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap? =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

    private data class OpfMetadata(
        val title: String,
        val authors: List<String>,
        val coverHref: String?,
    )

    private companion object {
        const val MAX_TEXT_BYTES = 32 * 1024 * 1024
        const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
        const val PREVIEW_WIDTH = 360
    }
}

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
            }.getOrElse { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
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
        data class EpubScan(
            val opfEntryName: String?,
            val opfBytes: ByteArray?,
            val images: Map<String, ByteArray>,
        )

        val scan = open(uri).use { input ->
            ZipInputStream(input).use { zip ->
                var opfEntryName: String? = null
                var opfBytes: ByteArray? = null
                val images = mutableMapOf<String, ByteArray>()

                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    val lowerName = entry.name.lowercase()
                    when {
                        lowerName.endsWith(".opf") && opfBytes == null -> {
                            opfEntryName = entry.name
                            opfBytes = zip.readCurrentEntry(MAX_TEXT_BYTES)
                        }
                        lowerName.isImageName() && images.size < 20 -> {
                            images[entry.name] = zip.readCurrentEntry(MAX_IMAGE_BYTES)
                        }
                        else -> zip.closeEntry()
                    }
                }
                EpubScan(opfEntryName, opfBytes, images)
            }
        }

        if (scan.opfBytes == null || scan.opfEntryName == null) {
            return BookMetadata(title = fallbackTitle)
        }

        val parsed = parseOpf(scan.opfBytes, fallbackTitle)
        val opfDir = scan.opfEntryName.substringBeforeLast('/', missingDelimiterValue = "")
        val coverPath = parsed.coverHref?.let { resolveZipPath(opfDir, it) }

        val coverBytes: ByteArray? = when {
            coverPath != null ->
                scan.images.entries
                    .firstOrNull { (name, _) -> name.equals(coverPath, ignoreCase = true) }
                    ?.value
            else -> {
                val bestName = chooseBestFallbackImageName(scan.images.keys.toList())
                bestName?.let { scan.images[it] }
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
                    val scale = minOf(
                        PREVIEW_MAX_WIDTH / page.width.toFloat(),
                        PREVIEW_MAX_HEIGHT / page.height.toFloat(),
                    )
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
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
                        val bitmap = decodeBitmap(zip.readCurrentEntry(MAX_IMAGE_BYTES))
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
                                bestBytes = archive.getInputStream(header).use { it.readBytesLimited(MAX_IMAGE_BYTES) }
                            }
                        }
                    }
                    bestBytes?.let(::decodeBitmap)
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
            decodeBitmap(bytes)
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

    private fun FileHeader.normalizedName(): String =
        fileName
            .replace('\\', '/')
            .substringAfterLast("/")
            .trim()

    private fun String.isImageName(): Boolean =
        endsWith(".jpg") ||
            endsWith(".jpeg") ||
            endsWith(".png") ||
            endsWith(".webp") ||
            endsWith(".gif") ||
            endsWith(".avif")

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
        runCatching {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                inSampleSize = calculateInSampleSize(
                    width = bounds.outWidth,
                    height = bounds.outHeight,
                    maxWidth = PREVIEW_MAX_WIDTH,
                    maxHeight = PREVIEW_MAX_HEIGHT,
                )
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxWidth: Int,
        maxHeight: Int,
    ): Int {
        if (width <= 0 || height <= 0) return 1

        var sampleSize = 1
        var sampledWidth = width
        var sampledHeight = height
        while (sampledWidth / 2 >= maxWidth || sampledHeight / 2 >= maxHeight) {
            sampleSize *= 2
            sampledWidth /= 2
            sampledHeight /= 2
        }
        return sampleSize
    }

    private data class OpfMetadata(
        val title: String,
        val authors: List<String>,
        val coverHref: String?,
    )

    private companion object {
        const val MAX_TEXT_BYTES = 32 * 1024 * 1024
        const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
        const val PREVIEW_MAX_WIDTH = 360
        const val PREVIEW_MAX_HEIGHT = 540
    }
}

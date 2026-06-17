package com.cybercat.pocketbooksender.metadata

import android.net.Uri
import com.cybercat.pocketbooksender.domain.NaturalSort
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream
import javax.inject.Inject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class EpubMetadataParser @Inject constructor() {
    fun extract(uri: Uri, fallbackTitle: String, openStream: (Uri) -> InputStream): BookMetadata {
        data class EpubScan(
            val opfEntryName: String?,
            val opfBytes: ByteArray?,
            val images: Map<String, ByteArray>,
        )

        val scan = openStream(uri).use { input ->
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
                            images[entry.name] = zip.readCurrentEntry(METADATA_MAX_IMAGE_BYTES)
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
            preview = coverBytes?.let(MetadataPreviewDecoder::decodeBitmap),
            series = parsed.series,
            seriesIndex = parsed.seriesIndex,
            publisher = parsed.publisher,
            year = parsed.year,
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

        var series: String? = null
        var seriesIndex: String? = null
        var publisher: String? = null
        var year: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            val localName = parser.name.substringAfter(':')
            when (localName) {
                "title" -> if (title == null) title = parser.nextTextSafe()
                "creator" -> authors += parser.nextTextSafe()
                "publisher" -> if (publisher == null) publisher = parser.nextTextSafe().trim()
                "date" -> {
                    val dateText = parser.nextTextSafe()
                    val regex = Regex("""\b\d{4}\b""")
                    val maybeYear = regex.find(dateText)?.value
                    if (maybeYear != null && year == null) {
                        year = maybeYear
                    }
                }
                "meta" -> {
                    val nameAttr = parser.getAttributeValue(null, "name")
                    val contentAttr = parser.getAttributeValue(null, "content")
                    val propertyAttr = parser.getAttributeValue(null, "property")

                    if (nameAttr == "cover") {
                        coverId = contentAttr
                    }

                    // Calibre EPUB 2 series tags
                    if (nameAttr == "calibre:series" && !contentAttr.isNullOrBlank()) {
                        series = contentAttr.trim()
                    }
                    if (nameAttr == "calibre:series_index" && !contentAttr.isNullOrBlank()) {
                        seriesIndex = contentAttr.trim()
                    }

                    // EPUB 3 series tags
                    if (propertyAttr == "belongs-to-collection") {
                        val collectionName = parser.nextTextSafe().trim()
                        if (collectionName.isNotBlank()) {
                            series = collectionName
                        }
                    }
                    if (propertyAttr == "group-position") {
                        val position = parser.nextTextSafe().trim()
                        if (position.isNotBlank()) {
                            seriesIndex = position
                        }
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
            series = series,
            seriesIndex = seriesIndex,
            publisher = publisher,
            year = year,
        )
    }

    private fun newParser(input: InputStream): XmlPullParser =
        XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(input, null)
        }

    private fun XmlPullParser.nextTextSafe(): String =
        runCatching { nextText().trim() }.getOrDefault("")

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

    private data class OpfMetadata(
        val title: String,
        val authors: List<String>,
        val coverHref: String?,
        val series: String? = null,
        val seriesIndex: String? = null,
        val publisher: String? = null,
        val year: String? = null,
    )

    private companion object {
        const val MAX_TEXT_BYTES = 32 * 1024 * 1024
    }
}
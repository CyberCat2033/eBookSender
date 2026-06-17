package com.cybercat.pocketbooksender.metadata

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.cybercat.pocketbooksender.domain.isZipWrappedBook
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream
import javax.inject.Inject
import org.xmlpull.v1.XmlPullParser

class Fb2MetadataParser @Inject constructor() {
    fun extract(
        uri: Uri,
        displayName: String,
        fallbackTitle: String,
        openStream: (Uri) -> InputStream
    ): BookMetadata {
        val bytes = if (displayName.isZipWrappedBook()) {
            openStream(uri).use { input ->
                ZipInputStream(input).use { zip ->
                    generateSequence { zip.nextEntry }
                        .firstOrNull { !it.isDirectory && it.name.lowercase().endsWith(".fb2") }
                        ?.let { zip.readCurrentEntry(MAX_TEXT_BYTES) }
                }
            }
        } else {
            openStream(uri).use { it.readBytesLimited(MAX_TEXT_BYTES) }
        }

        return bytes?.let { parseFb2(it, fallbackTitle) } ?: BookMetadata(title = fallbackTitle)
    }

    private fun parseFb2(bytes: ByteArray, fallbackTitle: String): BookMetadata {
        val parser = newMetadataXmlParser(ByteArrayInputStream(bytes))
        var inTitleInfo = false
        var inPublishInfo = false
        var inAuthor = false
        var inCoverpage = false
        var title: String? = null
        var coverId: String? = null
        val authors = mutableListOf<String>()
        val authorParts = mutableListOf<String>()
        val previewsById = mutableMapOf<String, Bitmap>()
        var coverPreview: Bitmap? = null
        var namedCoverPreview: Bitmap? = null
        var fallbackPreview: Bitmap? = null

        var series: String? = null
        var seriesIndex: String? = null
        var publisher: String? = null
        var year: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "title-info" -> inTitleInfo = true

                    "publish-info" -> inPublishInfo = true

                    "coverpage" -> if (inTitleInfo) inCoverpage = true

                    "author" -> if (inTitleInfo) {
                        inAuthor = true
                        authorParts.clear()
                    }

                    "book-title" -> if (inTitleInfo) title = parser.nextTextSafe()

                    "first-name", "middle-name", "last-name", "nickname" -> {
                        if (inTitleInfo && inAuthor) authorParts += parser.nextTextSafe()
                    }

                    "sequence" -> {
                        if (inTitleInfo && series == null) {
                            series = parser.getAttributeValue(null, "name")?.trim()
                            seriesIndex = parser.getAttributeValue(null, "number")?.trim()
                        }
                    }

                    "publisher" -> {
                        if (inPublishInfo) {
                            publisher = parser.nextTextSafe().trim()
                        }
                    }

                    "year" -> {
                        if (inPublishInfo) {
                            year = parser.nextTextSafe().trim()
                        }
                    }

                    "date" -> {
                        val valueAttr = parser.getAttributeValue(null, "value")?.trim()
                        val dateText = parser.nextTextSafe().trim()
                        val extracted = valueAttr ?: dateText
                        val regex = Regex("""\b\d{4}\b""")
                        val maybeYear = regex.find(extracted)?.value
                        if (maybeYear != null) {
                            if (inPublishInfo || (inTitleInfo && year == null)) {
                                year = maybeYear
                            }
                        }
                    }

                    "image" -> if (inTitleInfo && inCoverpage && coverId == null) {
                        coverId = parser.attributeValueByLocalName("href", XLINK_NAMESPACE)
                            ?.toFb2BinaryId()
                        coverId?.let { id ->
                            previewsById[id]?.let { coverPreview = it }
                        }
                    }

                    "binary" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val normalizedId = id?.toFb2BinaryId()
                        val contentType = parser.getAttributeValue(null, "content-type").orEmpty()
                        val looksLikeImage = contentType.startsWith("image/") ||
                            id?.lowercase().orEmpty().isImageName()
                        if (id != null && looksLikeImage) {
                            val bitmap = decodeBase64Bitmap(parser.nextTextSafe())
                            if (bitmap != null && normalizedId != null) {
                                if (previewsById.size < MAX_FB2_CACHED_IMAGE_PREVIEWS) {
                                    previewsById.putIfAbsent(normalizedId, bitmap)
                                }
                                when {
                                    normalizedId == coverId -> coverPreview = bitmap

                                    namedCoverPreview == null &&
                                        normalizedId.looksLikeCoverId() -> {
                                        namedCoverPreview = bitmap
                                    }
                                }
                            }
                            if (fallbackPreview == null) {
                                fallbackPreview = bitmap
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

                    "coverpage" -> inCoverpage = false

                    "publish-info" -> inPublishInfo = false
                }
            }
        }

        return BookMetadata(
            title = title?.ifBlank { fallbackTitle } ?: fallbackTitle,
            authors = authors.distinct(),
            preview = coverPreview ?: namedCoverPreview ?: fallbackPreview,
            series = series,
            seriesIndex = seriesIndex,
            publisher = publisher,
            year = year
        )
    }

    private fun decodeBase64Bitmap(value: String): Bitmap? = runCatching {
        val bytes = Base64.decode(value.replace(Regex("""\s+"""), ""), Base64.DEFAULT)
        MetadataPreviewDecoder.decodeBitmap(bytes)
    }.getOrNull()

    private fun String.toFb2BinaryId(): String {
        val reference = trim()
        val rawId = if (reference.startsWith("#")) {
            reference.drop(1)
        } else {
            reference.substringAfterLast('#')
        }
        return runCatching {
            URLDecoder.decode(rawId, Charsets.UTF_8.name())
        }.getOrDefault(rawId).trim()
    }

    private fun String.looksLikeCoverId(): Boolean {
        val name = lowercase().substringAfterLast('/').substringBeforeLast('.')
        return name == "cover" ||
            name == "front" ||
            name == "folder" ||
            name == "title" ||
            name.contains("cover") ||
            name.contains("front")
    }

    private companion object {
        const val MAX_TEXT_BYTES = 32 * 1024 * 1024
        const val MAX_FB2_CACHED_IMAGE_PREVIEWS = 8
        const val XLINK_NAMESPACE = "http://www.w3.org/1999/xlink"
    }
}

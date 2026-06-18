package com.cybercat.ebooksender.metadata

import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import org.xmlpull.v1.XmlPullParser

class DocxMetadataParser @Inject constructor() {
    fun extract(uri: Uri, fallbackTitle: String, openStream: (Uri) -> InputStream): BookMetadata {
        val coreProperties = openStream(uri).use(::extractCorePropertiesBytes)
            ?: return BookMetadata(title = fallbackTitle)

        return parseCoreProperties(coreProperties, fallbackTitle)
    }

    private fun extractCorePropertiesBytes(input: InputStream): ByteArray? =
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }

                if (entry.name.equals(CORE_PROPERTIES_ENTRY_NAME, ignoreCase = true)) {
                    return@use zip.readCurrentEntry(MAX_CORE_PROPERTIES_BYTES)
                }

                zip.closeEntry()
            }
            null
        }

    private fun parseCoreProperties(bytes: ByteArray, fallbackTitle: String): BookMetadata {
        val parser = newMetadataXmlParser(ByteArrayInputStream(bytes))
        var title: String? = null
        val authors = mutableListOf<String>()
        var description: String? = null
        var subject: String? = null
        var language: String? = null
        var year: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name.substringAfter(':')) {
                "title" -> if (title == null) title = parser.nextTextSafe()

                "creator" -> {
                    parser.nextTextSafe()
                        .takeIf { it.isNotBlank() }
                        ?.let(authors::add)
                }

                "description" -> if (description.isNullOrBlank()) {
                    description = parser.nextTextSafe()
                }

                "subject" -> if (subject.isNullOrBlank()) {
                    subject = parser.nextTextSafe()
                }

                "language" -> if (language.isNullOrBlank()) {
                    language = parser.nextTextSafe()
                }

                "created", "modified" -> if (year == null) {
                    year = parser.nextTextSafe().firstFourDigitYear()
                }
            }
        }

        return BookMetadata(
            title = title?.ifBlank { fallbackTitle } ?: fallbackTitle,
            authors = authors.distinct(),
            description = firstNonBlank(description, subject),
            language = language?.takeIf { it.isNotBlank() },
            year = year
        )
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun String.firstFourDigitYear(): String? = Regex("""\b\d{4}\b""").find(this)?.value

    private companion object {
        const val CORE_PROPERTIES_ENTRY_NAME = "docProps/core.xml"
        const val MAX_CORE_PROPERTIES_BYTES = 512 * 1024
    }
}

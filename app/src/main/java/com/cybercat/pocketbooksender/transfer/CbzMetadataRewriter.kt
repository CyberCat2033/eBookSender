package com.cybercat.pocketbooksender.transfer

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class CbzMetadata(
    val title: String,
    val series: String?,
    val number: String?,
)

object CbzMetadataRewriter {
    fun findSingleCommonRootFolder(input: InputStream): String? =
        ZipInputStream(input.buffered()).use { zip ->
            val roots = mutableSetOf<String>()
            var contentEntryCount = 0
            var everyContentEntryIsNested = true

            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val name = entry.name.normalizeZipEntryName()
                    if (!name.substringAfterLast('/').equals(ComicInfoName, ignoreCase = true)) {
                        contentEntryCount += 1
                        val slashIndex = name.indexOf('/')
                        if (slashIndex > 0) {
                            roots += name.substring(0, slashIndex)
                        } else {
                            everyContentEntryIsNested = false
                        }
                    }
                }
                zip.closeEntry()
            }

            roots.singleOrNull()?.takeIf {
                contentEntryCount > 0 && everyContentEntryIsNested
            }
        }

    fun rewrite(
        input: InputStream,
        output: OutputStream,
        metadata: CbzMetadata,
        rootFolder: String?,
    ) {
        val title = metadata.title.trim().ifBlank { "Manga" }
        val seenNames = mutableSetOf<String>()
        val buffer = ByteArray(CopyBufferSize)

        ZipInputStream(input.buffered()).use { sourceZip ->
            ZipOutputStream(output.buffered()).use { targetZip ->
                targetZip.setComment(title.take(ZipCommentMaxLength))

                while (true) {
                    val sourceEntry = sourceZip.nextEntry ?: break
                    if (sourceEntry.isDirectory) {
                        sourceZip.closeEntry()
                        continue
                    }

                    val normalizedName = sourceEntry.name.normalizeZipEntryName()
                    if (normalizedName.substringAfterLast('/').equals(ComicInfoName, ignoreCase = true)) {
                        sourceZip.closeEntry()
                        continue
                    }

                    val renamed = normalizedName.renameRootFolder(rootFolder, title)
                    if (!seenNames.add(renamed.lowercase())) {
                        sourceZip.closeEntry()
                        continue
                    }

                    val targetEntry = ZipEntry(renamed)
                    if (sourceEntry.time >= 0L) {
                        targetEntry.time = sourceEntry.time
                    }
                    if (!sourceEntry.comment.isNullOrBlank()) {
                        targetEntry.comment = sourceEntry.comment
                    }

                    targetZip.putNextEntry(targetEntry)
                    while (true) {
                        val read = sourceZip.read(buffer)
                        if (read < 0) break
                        targetZip.write(buffer, 0, read)
                    }
                    targetZip.closeEntry()
                    sourceZip.closeEntry()
                }

                targetZip.putNextEntry(ZipEntry(ComicInfoName))
                targetZip.write(metadata.toComicInfoXml(title).toByteArray(Charsets.UTF_8))
                targetZip.closeEntry()
            }
        }
    }

    private fun String.renameRootFolder(
        rootFolder: String?,
        newRoot: String,
    ): String {
        if (rootFolder == null || !startsWith("$rootFolder/")) return this
        return "${newRoot.toSafeZipPathSegment()}/${removePrefix("$rootFolder/")}"
    }

    private fun CbzMetadata.toComicInfoXml(title: String): String {
        val seriesTag = series?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { "  <Series>${it.escapeXml()}</Series>\n" }
            .orEmpty()
        val numberTag = number?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { "  <Number>${it.escapeXml()}</Number>\n" }
            .orEmpty()

        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append('\n')
            append("<ComicInfo>\n")
            append("  <Title>${title.escapeXml()}</Title>\n")
            append(seriesTag)
            append(numberTag)
            append("</ComicInfo>\n")
        }
    }

    private fun String.normalizeZipEntryName(): String =
        replace('\\', '/')
            .split('/')
            .filter { segment -> segment.isNotBlank() && segment != "." && segment != ".." }
            .joinToString("/")
            .ifBlank { "entry" }

    private fun String.toSafeZipPathSegment(): String =
        normalizeZipEntryName()
            .replace('/', '_')
            .trim()
            .ifBlank { "Manga" }

    private fun String.escapeXml(): String =
        buildString(length) {
            this@escapeXml.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(char)
                }
            }
        }

    private const val ComicInfoName = "ComicInfo.xml"
    private const val CopyBufferSize = 64 * 1024
    private const val ZipCommentMaxLength = 32_000
}

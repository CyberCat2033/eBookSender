package com.cybercat.ebooksender.transfer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CbzMetadataRewriterTest {

    @Test
    fun findSingleCommonRootFolder_singleRoot() {
        val zipData = createZip(
            listOf(
                "MangaFolder/page1.jpg" to byteArrayOf(1, 2, 3),
                "MangaFolder/page2.jpg" to byteArrayOf(4, 5, 6),
                "ComicInfo.xml" to byteArrayOf(7, 8)
            )
        )

        val root = CbzMetadataRewriter.findSingleCommonRootFolder(ByteArrayInputStream(zipData))
        assertEquals("MangaFolder", root)
    }

    @Test
    fun findSingleCommonRootFolder_noRoot() {
        val zipData = createZip(
            listOf(
                "page1.jpg" to byteArrayOf(1, 2, 3),
                "page2.jpg" to byteArrayOf(4, 5, 6)
            )
        )

        val root = CbzMetadataRewriter.findSingleCommonRootFolder(ByteArrayInputStream(zipData))
        assertNull(root)
    }

    @Test
    fun findSingleCommonRootFolder_multipleRoots() {
        val zipData = createZip(
            listOf(
                "FolderA/page1.jpg" to byteArrayOf(1),
                "FolderB/page2.jpg" to byteArrayOf(2)
            )
        )

        val root = CbzMetadataRewriter.findSingleCommonRootFolder(ByteArrayInputStream(zipData))
        assertNull(root)
    }

    @Test
    fun findSingleCommonRootFolder_emptyZip() {
        val zipData = createZip(emptyList())
        val root = CbzMetadataRewriter.findSingleCommonRootFolder(ByteArrayInputStream(zipData))
        assertNull(root)
    }

    @Test
    fun rewrite_generatesComicInfoAndRenamesRoot() {
        val zipData = createZip(
            listOf(
                "OldRoot/page1.jpg" to byteArrayOf(1, 2, 3),
                "ComicInfo.xml" to byteArrayOf(10, 20) // should be overwritten
            )
        )

        val metadata = CbzMetadata(
            title = "My <Manga> & More",
            series = "Special \"Series\"",
            number = "Volume 1 & 2"
        )

        val outputStream = ByteArrayOutputStream()
        CbzMetadataRewriter.rewrite(
            input = ByteArrayInputStream(zipData),
            output = outputStream,
            metadata = metadata,
            rootFolder = "OldRoot"
        )

        val (entries, comment) = readZip(outputStream.toByteArray())

        // Check ZIP Comment
        assertEquals("My <Manga> & More", comment)

        // Check that entries exist and are renamed
        assertEquals(2, entries.size)

        // Root folder should be renamed to safe version of title: My <Manga> & More
        println("DEBUG: Rewritten entries: ${entries.map { it.first }}")
        val imageEntry = entries.firstOrNull { it.first == "My <Manga> & More/page1.jpg" }
        val xmlEntry = entries.firstOrNull { it.first == "ComicInfo.xml" }

        assertTrue("Image entry should be renamed under new root", imageEntry != null)
        assertEquals(3, imageEntry?.second?.size)

        assertTrue("ComicInfo.xml must exist", xmlEntry != null)
        val xmlContent = String(xmlEntry!!.second, Charsets.UTF_8)

        // Assert XML contents and character escaping
        assertTrue(xmlContent.contains("<Title>My &lt;Manga&gt; &amp; More</Title>"))
        assertTrue(xmlContent.contains("<Series>Special &quot;Series&quot;</Series>"))
        assertTrue(xmlContent.contains("<Number>Volume 1 &amp; 2</Number>"))
    }

    @Test
    fun rewrite_suppressesDuplicatesCaseInsensitive() {
        val zipData = createZip(
            listOf(
                "page1.jpg" to byteArrayOf(1),
                "PAGE1.jpg" to byteArrayOf(2), // duplicate
                "page2.jpg" to byteArrayOf(3)
            )
        )

        val metadata = CbzMetadata(
            title = "Manga",
            series = null,
            number = null
        )

        val outputStream = ByteArrayOutputStream()
        CbzMetadataRewriter.rewrite(
            input = ByteArrayInputStream(zipData),
            output = outputStream,
            metadata = metadata,
            rootFolder = null
        )

        val (entries, _) = readZip(outputStream.toByteArray())

        // entries should contain: page1.jpg, page2.jpg, ComicInfo.xml
        assertEquals(3, entries.size)
        assertTrue(entries.any { it.first == "page1.jpg" })
        assertTrue(entries.any { it.first == "page2.jpg" })
        assertTrue(entries.any { it.first == "ComicInfo.xml" })
    }

    private fun createZip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for ((name, data) in entries) {
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(data)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun readZip(zipBytes: ByteArray): Pair<List<Pair<String, ByteArray>>, String?> {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        var comment: String? = null

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val bos = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (true) {
                    val read = zis.read(buffer)
                    if (read < 0) break
                    bos.write(buffer, 0, read)
                }
                entries += entry.name to bos.toByteArray()
                zis.closeEntry()
            }
        }

        comment = extractZipComment(zipBytes)

        return entries to comment
    }

    private fun extractZipComment(bytes: ByteArray): String? {
        for (i in bytes.size - 22 downTo 0) {
            if (bytes[i] == 0x50.toByte() &&
                bytes[i + 1] == 0x4b.toByte() &&
                bytes[i + 2] == 0x05.toByte() &&
                bytes[i + 3] == 0x06.toByte()
            ) {
                val commentLen = (
                    (bytes[i + 20].toInt() and 0xFF) or
                        ((bytes[i + 21].toInt() and 0xFF) shl 8)
                    )
                if (commentLen > 0 && i + 22 + commentLen <= bytes.size) {
                    return String(bytes, i + 22, commentLen, Charsets.UTF_8)
                }
            }
        }
        return null
    }
}

package com.cybercat.ebooksender.data.manga

import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaArchiveHelper @Inject constructor() {
    private val fileLock = Any()

    fun uniqueFile(directory: File, fileName: String): File = synchronized(fileLock) {
        val base = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        var candidate = File(directory, fileName)
        var index = 2
        while (candidate.exists() || !candidate.createNewFile()) {
            val suffix = if (extension.isBlank()) "" else ".$extension"
            candidate = File(directory, "$base-$index$suffix")
            index += 1
        }
        return candidate
    }

    fun writeCbz(pages: List<DownloadedMangaPage>, outputFile: File) {
        ZipOutputStream(outputFile.outputStream().buffered()).use { zip ->
            pages.forEachIndexed { index, page ->
                val extension = page.fileExtension.lowercase().trimStart('.').ifBlank { "jpg" }
                val entryName = "${(index + 1).toString().padStart(4, '0')}.$extension"
                zip.putNextEntry(ZipEntry(entryName))
                page.file.inputStream().buffered().use { input ->
                    input.copyTo(zip)
                }
                zip.closeEntry()
            }
        }
    }

    fun moveTempToUnique(tempFile: File, directory: File, fileName: String): File =
        synchronized(fileLock) {
            val outputFile = uniqueFile(directory, fileName)
            if (!outputFile.delete()) {
                throw IOException("Cannot prepare output file: ${outputFile.name}")
            }
            if (!tempFile.renameTo(outputFile)) {
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
            }
            outputFile
        }
}

data class DownloadedMangaPage(val index: Int, val file: File, val fileExtension: String)

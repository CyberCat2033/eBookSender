package com.cybercat.ebooksender.data.manga

import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class MangaArchiveHelper @Inject constructor() {
    private val fileMutex = Mutex()

    suspend fun createTempFile(directory: File, fileName: String): File = fileMutex.withLock {
        directory.mkdirs()
        File.createTempFile(
            tempFilePrefix(fileName),
            tempFileSuffix(fileName),
            directory
        )
    }

    suspend fun writeCbzToUnique(
        pages: List<DownloadedMangaPage>,
        directory: File,
        fileName: String
    ): File {
        val tempFile = createTempFile(directory, fileName)
        try {
            writeCbz(pages, tempFile)
            return moveTempToUnique(tempFile, directory, fileName)
        } finally {
            tempFile.delete()
        }
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

    suspend fun moveTempToUnique(tempFile: File, directory: File, fileName: String): File =
        fileMutex.withLock {
            directory.mkdirs()
            var candidate = File(directory, fileName)
            var index = 2
            while (true) {
                try {
                    Files.move(tempFile.toPath(), candidate.toPath())
                    return@withLock candidate
                } catch (e: FileAlreadyExistsException) {
                    val base = fileName.substringBeforeLast('.', fileName)
                    val extension = fileName.substringAfterLast('.', "")
                    val suffix = if (extension.isBlank()) "" else ".$extension"
                    candidate = File(directory, "$base-$index$suffix")
                    index += 1
                }
            }
            error("Unreachable unique manga archive target selection")
        }

    private fun tempFilePrefix(fileName: String): String {
        val base = fileName.substringBeforeLast('.', fileName)
            .take(48)
            .ifBlank { "manga" }
        return base.padEnd(3, '_') + "-"
    }

    private fun tempFileSuffix(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "")
        return if (extension.isBlank()) ".tmp" else ".$extension.tmp"
    }
}

data class DownloadedMangaPage(val index: Int, val file: File, val fileExtension: String)

package com.cybercat.pocketbooksender.domain

import com.cybercat.pocketbooksender.model.BookCategory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileClassifier @Inject constructor() {
    fun classify(fileName: String): BookCategory = when {
        fileName.contentExtension() in MangaArchiveExtensions -> BookCategory.Manga
        fileName.contentExtension() in DocumentFileExtensions -> BookCategory.Documents
        else -> BookCategory.Books
    }
}

fun String.bookExtension(): String {
    val name = fileNameOnly()
    val parts = name
        .split('.')
        .map { it.lowercase().trim() }

    if (parts.size >= 3 && parts.last() == "zip") {
        val wrappedExtension = parts[parts.lastIndex - 1]
        if (wrappedExtension in ZipWrappedBookExtensions) {
            return "$wrappedExtension.zip"
        }
    }

    return parts.lastOrNull().orEmpty()
}

fun String.bookTitleWithoutExtension(): String {
    val name = fileNameOnly()
    val extension = name.bookExtension()
    if (extension.isBlank()) return name

    val suffixLength = extension.length + 1
    return if (name.length > suffixLength) {
        name.dropLast(suffixLength)
    } else {
        name
    }
}

fun String.contentExtension(): String = bookExtension().removeSuffix(".zip")

fun String.isZipWrappedBook(): Boolean = bookExtension().endsWith(".zip")

private fun String.fileNameOnly(): String = substringAfterLast('/')
    .substringAfterLast('\\')
    .trim()

private val ZipWrappedBookExtensions = setOf(
    "fb2",
    "epub",
    "mobi",
    "azw3",
    "pdf",
    "djvu",
    "doc",
    "docx",
    "txt",
    "rtf"
)

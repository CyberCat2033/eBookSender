package com.cybercat.ebooksender.data.opds

enum class OpdsDownloadFormat(
    val label: String,
    val priority: Int,
) {
    Fb2("FB2", 0),
    Epub("EPUB", 1),
    Mobi("MOBI", 2),
}

fun OpdsAcquisition.supportedDownloadFormat(): OpdsDownloadFormat? {
    val mime = type.orEmpty().lowercase()
    val fileName = href.fileNameFromUrl()
    val extension = fileName.substringAfterLast('.', "")

    return when {
        mime.contains("fb2") || extension == "fb2" || fileName.endsWith(".fb2.zip") -> OpdsDownloadFormat.Fb2
        mime.contains("epub") || extension == "epub" -> OpdsDownloadFormat.Epub
        mime.contains("mobipocket") || mime.contains("mobi") || extension == "mobi" -> OpdsDownloadFormat.Mobi
        else -> null
    }
}

fun OpdsAcquisition.downloadFormatLabel(): String {
    supportedDownloadFormat()?.let { format -> return format.label }

    val explicitTitle = title?.takeIf { it.isNotBlank() && it.length <= 18 }
    val mime = type.orEmpty().lowercase()
    val fileName = href.fileNameFromUrl()
    val extension = fileName.substringAfterLast('.', "")

    return when {
        mime.contains("html") || extension == "html" -> "HTML"
        mime.contains("txt") || extension == "txt" -> "TXT"
        mime.contains("rtf") || extension == "rtf" -> "RTF"
        mime.contains("pdf") || extension == "pdf" -> "PDF"
        mime.contains("djvu") || extension == "djvu" -> "DJVU"
        mime.contains("comicbook+zip") || extension == "cbz" -> "CBZ"
        mime.contains("comicbook-rar") || mime.contains("rar") || extension == "cbr" -> "CBR"
        mime.contains("zip") || extension == "zip" -> "ZIP"
        explicitTitle != null -> explicitTitle
        extension.isNotBlank() -> extension.uppercase()
        else -> "Download"
    }
}

private fun String.fileNameFromUrl(): String =
    substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('/')
        .lowercase()

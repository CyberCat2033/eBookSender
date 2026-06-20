package com.cybercat.ebooksender.data.opds

import com.cybercat.ebooksender.domain.bookExtension
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

private const val FALLBACK_BOOK_NAME = "book"

internal fun normalizeOpdsUrl(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isBlank()) throw IllegalArgumentException("OPDS URL is empty")
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

internal fun chooseOpdsDownloadFileName(
    connection: HttpURLConnection,
    url: String,
    entry: OpdsEntry,
    acquisition: OpdsAcquisition
): String {
    val dispositionName = connection.getHeaderField("Content-Disposition")
        ?.let(::fileNameFromDisposition)
    val urlName = runCatching {
        URLDecoder.decode(URL(url).path.substringAfterLast('/'), Charsets.UTF_8.name())
    }.getOrNull()?.takeIf { it.isNotBlank() }

    val baseName = dispositionName
        ?: urlName
        ?: entry.title.ifBlank { acquisition.title.orEmpty() }
        ?: FALLBACK_BOOK_NAME

    val sanitized = baseName.sanitizeDownloadFileName().ifBlank { FALLBACK_BOOK_NAME }
    return if (sanitized.bookExtension().isNotBlank()) {
        sanitized
    } else {
        val extension = acquisition.extensionFromMimeType()
        if (extension.isBlank()) sanitized else "$sanitized.$extension"
    }
}

internal fun uniqueOpdsDownloadFile(directory: File, desiredName: String): File {
    val name = desiredName.sanitizeDownloadFileName().ifBlank { FALLBACK_BOOK_NAME }
    var candidate = File(directory, name)
    if (!candidate.exists()) return candidate

    val extension = name.bookExtension()
    val stem = if (extension.isBlank()) name else name.dropLast(extension.length + 1)
    var index = 2
    while (candidate.exists()) {
        val nextName = if (extension.isBlank()) {
            "$stem-$index"
        } else {
            "$stem-$index.$extension"
        }
        candidate = File(directory, nextName)
        index += 1
    }
    return candidate
}

private fun fileNameFromDisposition(disposition: String): String? {
    val utfName = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
        .find(disposition)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }

    if (!utfName.isNullOrBlank()) return utfName

    return Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
        .find(disposition)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun String.sanitizeDownloadFileName(): String = replace(Regex("[\\\\/:*?\"<>|]"), "_")
    .replace(Regex("\\s+"), "_")
    .trim('_', '.', ' ')

private fun OpdsAcquisition.extensionFromMimeType(): String {
    val mime = type.orEmpty().lowercase()
    return when {
        mime.contains("epub") -> "epub"
        mime.contains("fb2") -> "fb2"
        mime.contains("mobipocket") || mime.contains("mobi") -> "mobi"
        else -> ""
    }
}

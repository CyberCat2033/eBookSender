package com.cybercat.pocketbooksender.metadata

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

internal const val METADATA_MAX_IMAGE_BYTES = 8 * 1024 * 1024

internal fun InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) break
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

internal fun ZipInputStream.readCurrentEntry(limit: Int): ByteArray =
    readBytesLimited(limit)

internal fun String.isImageName(): Boolean =
    endsWith(".jpg") ||
        endsWith(".jpeg") ||
        endsWith(".png") ||
        endsWith(".webp") ||
        endsWith(".gif") ||
        endsWith(".avif")

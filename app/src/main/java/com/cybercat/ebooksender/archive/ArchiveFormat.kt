package com.cybercat.ebooksender.archive

import java.io.InputStream

enum class ArchiveFormat {
    Zip,
    Rar4,
    Rar5,
    Unknown,
}

object ArchiveFormatDetector {
    fun detect(input: InputStream): ArchiveFormat {
        val header = ByteArray(8)
        val read = input.read(header)
        if (read < 4) return ArchiveFormat.Unknown

        if (header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()) {
            return ArchiveFormat.Zip
        }

        val rarPrefix = byteArrayOf(
            'R'.code.toByte(),
            'a'.code.toByte(),
            'r'.code.toByte(),
            '!'.code.toByte(),
            0x1A,
            0x07,
        )
        if (read >= 7 && header.take(6) == rarPrefix.toList()) {
            return when (header[6].toInt()) {
                0x00 -> ArchiveFormat.Rar4
                0x01 -> ArchiveFormat.Rar5
                else -> ArchiveFormat.Unknown
            }
        }

        return ArchiveFormat.Unknown
    }
}

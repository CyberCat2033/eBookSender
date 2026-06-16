package com.cybercat.pocketbooksender.metadata

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset

internal object MobiMetadataParser {
    fun parse(
        channel: FileChannel,
        declaredSize: Long,
        fallbackTitle: String,
    ): MobiMetadata? {
        val header = channel.readRange(offset = 0, length = PDB_HEADER_LENGTH) ?: return null
        val recordCount = header.readUInt16BEOrNull(PDB_RECORD_COUNT_OFFSET) ?: return null
        if (recordCount !in 1..MAX_PDB_RECORDS) return null

        val recordInfoLength = PDB_RECORD_LIST_OFFSET + recordCount * PDB_RECORD_INFO_LENGTH
        val recordInfo = channel.readRange(offset = 0, length = recordInfoLength) ?: return null
        val fileSize = channel.safeSize() ?: declaredSize.takeIf { it > 0 }
        val records = PalmRecordTable.from(recordInfo, recordCount, recordInfoLength, fileSize) ?: return null
        val record0Range = records.range(0) ?: return null
        val record0 = channel.readRange(
            offset = record0Range.start,
            length = record0Range.length.coerceAtMost(MAX_MOBI_RECORD0_BYTES.toLong()).toInt(),
        ) ?: return null

        if (!record0.hasAscii(MOBI_IDENTIFIER_OFFSET, MOBI_IDENTIFIER)) return null
        val mobiHeaderLength = record0.readUInt32BEAsIntOrNull(MOBI_HEADER_LENGTH_OFFSET) ?: return null
        if (mobiHeaderLength < MIN_MOBI_HEADER_LENGTH ||
            mobiHeaderLength > record0.size - MOBI_IDENTIFIER_OFFSET
        ) {
            return null
        }
        val mobiHeaderEnd = MOBI_IDENTIFIER_OFFSET + mobiHeaderLength

        val charset = mobiCharset(
            record0.readMobiHeaderUInt32BEOrNull(MOBI_TEXT_ENCODING_OFFSET, mobiHeaderEnd),
        )
        val fullName = readFullName(channel, record0, record0Range, mobiHeaderEnd, charset)
        val palmDatabaseName = header.copyOfRange(0, PDB_NAME_LENGTH).decodeMobiText(charset)
        val exth = parseExth(record0, mobiHeaderLength, mobiHeaderEnd, charset)
        val firstImageIndex = record0
            .readMobiHeaderUInt32BEOrNull(MOBI_FIRST_IMAGE_INDEX_OFFSET, mobiHeaderEnd)
            ?.takeUnless { it == UInt32Max }
            ?.takeIf { it <= Int.MAX_VALUE }
            ?.toInt()

        val title = firstNonBlank(
            exth.updatedTitle,
            fullName,
            palmDatabaseName,
            fallbackTitle,
        ) ?: fallbackTitle

        return MobiMetadata(
            title = title,
            authors = exth.authors.distinct(),
            description = exth.description,
            coverBytes = readCoverBytes(channel, records, firstImageIndex, exth.coverOffset),
            language = exth.language,
            year = exth.publishingDate?.firstFourDigitYear(),
            publisher = exth.publisher,
        )
    }

    private fun parseExth(
        record0: ByteArray,
        mobiHeaderLength: Int,
        mobiHeaderEnd: Int,
        charset: Charset,
    ): ExthMetadata {
        val exthFlags = record0.readMobiHeaderUInt32BEOrNull(MOBI_EXTH_FLAGS_OFFSET, mobiHeaderEnd)
            ?: return ExthMetadata()
        if (exthFlags.toInt() and MOBI_EXTH_FLAG == 0) return ExthMetadata()

        val exthOffset = MOBI_IDENTIFIER_OFFSET + mobiHeaderLength
        if (!record0.hasAscii(exthOffset, EXTH_IDENTIFIER)) return ExthMetadata()

        val exthLength = record0.readUInt32BEAsIntOrNull(exthOffset + EXTH_LENGTH_RELATIVE_OFFSET)
            ?: return ExthMetadata()
        val recordCount = record0.readUInt32BEAsIntOrNull(exthOffset + EXTH_RECORD_COUNT_RELATIVE_OFFSET)
            ?: return ExthMetadata()
        if (exthLength < EXTH_HEADER_LENGTH || exthLength > record0.size - exthOffset) {
            return ExthMetadata()
        }

        val exthEnd = exthOffset + exthLength
        var offset = exthOffset + EXTH_HEADER_LENGTH
        val authors = mutableListOf<String>()
        var updatedTitle: String? = null
        var description: String? = null
        var publisher: String? = null
        var publishingDate: String? = null
        var language: String? = null
        var coverOffset: Int? = null
        val maxRecords = recordCount.coerceAtMost(MAX_EXTH_RECORDS)
        var parsedRecords = 0

        while (parsedRecords < maxRecords && offset <= exthEnd - EXTH_RECORD_HEADER_LENGTH) {
            parsedRecords += 1

            val type = record0.readUInt32BEAsIntOrNull(offset) ?: break
            val length = record0.readUInt32BEAsIntOrNull(offset + EXTH_RECORD_LENGTH_RELATIVE_OFFSET)
                ?: break
            if (length < EXTH_RECORD_HEADER_LENGTH || length > exthEnd - offset) break

            val dataStart = offset + EXTH_RECORD_HEADER_LENGTH
            val data = record0.copyOfRange(dataStart, offset + length)
            val text = data.decodeMobiText(charset)

            when (type) {
                EXTH_AUTHOR -> if (text.isNotBlank()) authors += text
                EXTH_PUBLISHER -> publisher = firstNonBlank(publisher, text)
                EXTH_DESCRIPTION -> description = firstNonBlank(description, text)
                EXTH_PUBLISHING_DATE -> publishingDate = firstNonBlank(publishingDate, text)
                EXTH_COVER_OFFSET -> coverOffset = data.readFlexibleUIntBEOrNull()
                EXTH_UPDATED_TITLE -> updatedTitle = firstNonBlank(updatedTitle, text)
                EXTH_LANGUAGE -> language = firstNonBlank(language, text)
            }

            offset += length
        }

        return ExthMetadata(
            updatedTitle = updatedTitle,
            authors = authors,
            description = description,
            publisher = publisher,
            publishingDate = publishingDate,
            language = language,
            coverOffset = coverOffset,
        )
    }

    private fun readFullName(
        channel: FileChannel,
        record0: ByteArray,
        record0Range: RecordRange,
        mobiHeaderEnd: Int,
        charset: Charset,
    ): String? {
        val offset = record0
            .readMobiHeaderUInt32BEOrNull(MOBI_FULL_NAME_OFFSET_OFFSET, mobiHeaderEnd)
            ?.takeIf { it <= Int.MAX_VALUE }
            ?.toInt()
            ?: return null
        val length = record0
            .readMobiHeaderUInt32BEOrNull(MOBI_FULL_NAME_LENGTH_OFFSET, mobiHeaderEnd)
            ?.takeIf { it in 1..MAX_MOBI_TEXT_FIELD_BYTES.toLong() }
            ?.toInt()
            ?: return null

        if (offset < 0 || offset.toLong() + length > record0Range.length) return null

        val bytes = if (offset.toLong() + length <= record0.size) {
            record0.copyOfRange(offset, offset + length)
        } else {
            channel.readRange(record0Range.start + offset, length)
        } ?: return null

        return bytes.decodeMobiText(charset)
    }

    private fun readCoverBytes(
        channel: FileChannel,
        records: PalmRecordTable,
        firstImageIndex: Int?,
        coverOffset: Int?,
    ): ByteArray? {
        if (firstImageIndex == null) return null

        val explicitCoverIndex = coverOffset
            ?.let { firstImageIndex.toLong() + it.toLong() }
            ?.takeIf { it in 0..Int.MAX_VALUE.toLong() }
            ?.toInt()
            ?.takeIf(records::contains)
        if (explicitCoverIndex != null) {
            readImageRecord(channel, records, explicitCoverIndex)?.let { return it }
        }

        val scanEnd = (firstImageIndex.toLong() + MAX_IMAGE_RECORD_SCAN_COUNT - 1)
            .coerceAtMost(records.lastIndex.toLong())
            .toInt()
        if (firstImageIndex > scanEnd) return null

        for (index in firstImageIndex..scanEnd) {
            readImageRecord(channel, records, index)?.let { return it }
        }

        return null
    }

    private fun readImageRecord(
        channel: FileChannel,
        records: PalmRecordTable,
        index: Int,
    ): ByteArray? {
        val range = records.range(index) ?: return null
        val length = range.length.coerceAtMost(MAX_IMAGE_BYTES.toLong()).toInt()
        return channel.readRange(range.start, length)
            ?.takeIf { it.looksLikeImage() }
    }

    private fun mobiCharset(encodingCode: Long?): Charset =
        when (encodingCode) {
            MOBI_ENCODING_UTF8 -> Charsets.UTF_8
            MOBI_ENCODING_WINDOWS_1252 -> WINDOWS_1252
            else -> Charsets.UTF_8
        }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun String.firstFourDigitYear(): String? =
        Regex("""\b\d{4}\b""").find(this)?.value

    private fun FileChannel.safeSize(): Long? =
        runCatching { size() }.getOrNull()?.takeIf { it > 0 }

    private fun FileChannel.readRange(offset: Long, length: Int): ByteArray? {
        if (offset < 0 || length <= 0) return null

        return runCatching {
            val buffer = ByteBuffer.allocate(length)
            var position = offset
            while (buffer.hasRemaining()) {
                val read = read(buffer, position)
                if (read <= 0) return null
                position += read
            }
            buffer.array()
        }.getOrNull()
    }

    private fun ByteArray.readMobiHeaderUInt32BEOrNull(offset: Int, mobiHeaderEnd: Int): Long? =
        if (offset + UInt32ByteCount <= mobiHeaderEnd) {
            readUInt32BEOrNull(offset)
        } else {
            null
        }

    private fun ByteArray.readUInt16BEOrNull(offset: Int): Int? {
        if (offset < 0 || offset + UInt16ByteCount > size) return null
        return ((this[offset].toInt() and 0xFF) shl 8) or
            (this[offset + 1].toInt() and 0xFF)
    }

    private fun ByteArray.readUInt32BEOrNull(offset: Int): Long? {
        if (offset < 0 || offset + UInt32ByteCount > size) return null
        return ((this[offset].toLong() and 0xFF) shl 24) or
            ((this[offset + 1].toLong() and 0xFF) shl 16) or
            ((this[offset + 2].toLong() and 0xFF) shl 8) or
            (this[offset + 3].toLong() and 0xFF)
    }

    private fun ByteArray.readUInt32BEAsIntOrNull(offset: Int): Int? =
        readUInt32BEOrNull(offset)
            ?.takeIf { it <= Int.MAX_VALUE }
            ?.toInt()

    private fun ByteArray.readFlexibleUIntBEOrNull(): Int? {
        if (isEmpty() || size > UInt32ByteCount) return null
        var value = 0
        forEach { byte ->
            value = (value shl 8) or (byte.toInt() and 0xFF)
        }
        return value
    }

    private fun ByteArray.decodeMobiText(charset: Charset): String =
        String(this, charset)
            .trim('\u0000', ' ', '\r', '\n', '\t')
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun ByteArray.hasAscii(offset: Int, value: String): Boolean {
        if (offset < 0 || offset + value.length > size) return false
        return value.indices.all { index -> this[offset + index].toInt() == value[index].code }
    }

    private fun ByteArray.looksLikeImage(): Boolean =
        startsWith(0xFF, 0xD8) ||
            startsWith(0x89, 0x50, 0x4E, 0x47) ||
            startsWith(0x47, 0x49, 0x46, 0x38) ||
            startsWith(0x42, 0x4D) ||
            (startsWith(0x52, 0x49, 0x46, 0x46) && hasAscii(WEBP_FORMAT_OFFSET, "WEBP"))

    private fun ByteArray.startsWith(vararg bytes: Int): Boolean {
        if (bytes.size > size) return false
        return bytes.indices.all { index -> (this[index].toInt() and 0xFF) == bytes[index] }
    }

    private data class PalmRecordTable(
        private val offsets: List<Long>,
        private val fileSize: Long?,
    ) {
        val lastIndex: Int = offsets.lastIndex

        fun contains(index: Int): Boolean =
            index in offsets.indices

        fun range(index: Int): RecordRange? {
            val start = offsets.getOrNull(index) ?: return null
            val end = offsets.getOrNull(index + 1) ?: fileSize ?: return null
            if (end <= start) return null
            return RecordRange(start = start, length = end - start)
        }

        companion object {
            fun from(
                recordInfo: ByteArray,
                recordCount: Int,
                recordInfoLength: Int,
                fileSize: Long?,
            ): PalmRecordTable? {
                val offsets = List(recordCount) { index ->
                    val offset = PDB_RECORD_LIST_OFFSET + index * PDB_RECORD_INFO_LENGTH
                    recordInfo.readUInt32BEOrNull(offset) ?: return null
                }

                if (offsets.firstOrNull()?.let { it < recordInfoLength } != false) return null
                if (offsets.zipWithNext().any { (current, next) -> next <= current }) return null
                if (fileSize != null && offsets.any { it >= fileSize }) return null

                return PalmRecordTable(offsets, fileSize)
            }
        }
    }

    private data class RecordRange(
        val start: Long,
        val length: Long,
    )

    private data class ExthMetadata(
        val updatedTitle: String? = null,
        val authors: List<String> = emptyList(),
        val description: String? = null,
        val publisher: String? = null,
        val publishingDate: String? = null,
        val language: String? = null,
        val coverOffset: Int? = null,
    )

    private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

    private const val PDB_HEADER_LENGTH = 78
    private const val PDB_NAME_LENGTH = 32
    private const val PDB_RECORD_COUNT_OFFSET = 76
    private const val PDB_RECORD_LIST_OFFSET = 78
    private const val PDB_RECORD_INFO_LENGTH = 8
    private const val MAX_PDB_RECORDS = 65_535

    private const val MOBI_IDENTIFIER = "MOBI"
    private const val MOBI_IDENTIFIER_OFFSET = 16
    private const val MOBI_HEADER_LENGTH_OFFSET = 20
    private const val MOBI_TEXT_ENCODING_OFFSET = 28
    private const val MOBI_FULL_NAME_OFFSET_OFFSET = 84
    private const val MOBI_FULL_NAME_LENGTH_OFFSET = 88
    private const val MOBI_FIRST_IMAGE_INDEX_OFFSET = 108
    private const val MOBI_EXTH_FLAGS_OFFSET = 128
    private const val MOBI_EXTH_FLAG = 0x40
    private const val MIN_MOBI_HEADER_LENGTH = 20
    private const val MOBI_ENCODING_WINDOWS_1252 = 1252L
    private const val MOBI_ENCODING_UTF8 = 65_001L
    private const val UInt32Max = 0xFFFF_FFFFL

    private const val EXTH_IDENTIFIER = "EXTH"
    private const val EXTH_LENGTH_RELATIVE_OFFSET = 4
    private const val EXTH_RECORD_COUNT_RELATIVE_OFFSET = 8
    private const val EXTH_HEADER_LENGTH = 12
    private const val EXTH_RECORD_HEADER_LENGTH = 8
    private const val EXTH_RECORD_LENGTH_RELATIVE_OFFSET = 4
    private const val MAX_EXTH_RECORDS = 2_048

    private const val EXTH_AUTHOR = 100
    private const val EXTH_PUBLISHER = 101
    private const val EXTH_DESCRIPTION = 103
    private const val EXTH_PUBLISHING_DATE = 106
    private const val EXTH_COVER_OFFSET = 201
    private const val EXTH_UPDATED_TITLE = 503
    private const val EXTH_LANGUAGE = 524

    private const val UInt16ByteCount = 2
    private const val UInt32ByteCount = 4
    private const val WEBP_FORMAT_OFFSET = 8
    private const val MAX_MOBI_RECORD0_BYTES = 512 * 1024
    private const val MAX_MOBI_TEXT_FIELD_BYTES = 64 * 1024
    private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    private const val MAX_IMAGE_RECORD_SCAN_COUNT = 8
}

internal data class MobiMetadata(
    val title: String,
    val authors: List<String>,
    val description: String?,
    val coverBytes: ByteArray?,
    val language: String?,
    val year: String?,
    val publisher: String?,
)

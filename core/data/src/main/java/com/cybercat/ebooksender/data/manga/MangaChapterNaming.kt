package com.cybercat.ebooksender.data.manga

import java.math.BigDecimal
import java.math.RoundingMode

private const val MANGA_CHAPTER_SORT_WIDTH = 4
private const val MANGA_CHAPTER_SORT_FRACTION_SCALE = 3

fun mangaChapterQueueSeriesIndex(chapter: MangaChapter): String? =
    chapter.numberForSort?.toChapterNumberLabel()

fun mangaChapterQueueVolume(chapter: MangaChapter): String {
    val number = chapter.numberForSort ?: return chapter.title
    val sortKey = number.toSortableChapterKey()
    val title = chapter.title.trim()
    val numberLabel = number.toChapterNumberLabel()

    return when {
        title.isBlank() -> sortKey
        title == numberLabel -> sortKey
        else -> "${sortKey}_$title"
    }
}

private fun Double.toChapterNumberLabel(): String =
    BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()

private fun Double.toSortableChapterKey(): String {
    val scaled = BigDecimal.valueOf(this).setScale(
        MANGA_CHAPTER_SORT_FRACTION_SCALE,
        RoundingMode.HALF_UP
    )
    val integerPart = scaled.setScale(0, RoundingMode.DOWN).toPlainString()
    val fractionPart = scaled
        .remainder(BigDecimal.ONE)
        .movePointRight(MANGA_CHAPTER_SORT_FRACTION_SCALE)
        .abs()
        .toInt()

    val paddedIntegerPart = integerPart.padStart(MANGA_CHAPTER_SORT_WIDTH, '0')
    return if (fractionPart == 0) {
        paddedIntegerPart
    } else {
        "$paddedIntegerPart.${fractionPart.toString().padStart(
            MANGA_CHAPTER_SORT_FRACTION_SCALE,
            '0'
        )}"
    }
}

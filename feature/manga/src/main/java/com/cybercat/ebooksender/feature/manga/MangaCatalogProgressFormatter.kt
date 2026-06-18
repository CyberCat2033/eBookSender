package com.cybercat.ebooksender.feature.manga

import com.cybercat.ebooksender.data.manga.MangaSeriesDetails
import com.cybercat.ebooksender.localization.AppStrings
import com.cybercat.ebooksender.model.DeviceCatalog

internal object MangaCatalogProgressFormatter {
    fun lastReadChapterText(
        series: MangaSeriesDetails,
        catalog: DeviceCatalog,
        strings: AppStrings
    ): String? {
        val seriesKey = series.title.catalogMatchKey()
        if (seriesKey.isBlank()) return null

        val group = catalog.manga.firstOrNull { mangaGroup ->
            val groupKey = mangaGroup.name.catalogMatchKey()
            groupKey.isNotBlank() &&
                (groupKey == seriesKey || groupKey in seriesKey || seriesKey in groupKey)
        } ?: return null

        val file = group.lastReadFile ?: return null
        val progress = when {
            file.completed -> strings.mangaProgressCompleted
            file.readProgressPercent != null -> "${file.readProgressPercent}%"
            else -> null
        }
        return if (progress == null) {
            file.title ?: file.name
        } else {
            "${file.title ?: file.name} · $progress"
        }
    }

    private fun String.catalogMatchKey(): String =
        lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "")
}

package com.cybercat.ebooksender.feature.catalog

import com.cybercat.ebooksender.domain.bookTitleWithoutExtension
import com.cybercat.ebooksender.localization.AppStrings
import com.cybercat.ebooksender.model.CatalogFile
import com.cybercat.ebooksender.model.DeviceCatalog
import com.cybercat.ebooksender.model.MangaSeriesGroup

/**
 * Pointer target used to map drag-selection pointer coordinates to catalog files.
 */
internal data class CatalogPointerTarget(val index: Int, val path: String)

/**
 * All group root paths in a [DeviceCatalog], used for drag-selection bounds bookkeeping.
 */
internal fun DeviceCatalog.groupPaths(): Set<String> = buildSet {
    books.forEach { group -> add(group.path) }
    documents.forEach { group -> add(group.path) }
    manga.forEach { group -> add(group.path) }
}

/**
 * Flat list of pointer targets for the currently expanded groups only, so drag selection
 * resolves only over files the user can actually see.
 */
internal fun DeviceCatalog.fileSelectionTargets(
    expandedGroupPaths: Set<String>
): List<CatalogPointerTarget> = buildList {
    fun addFiles(groupPath: String, files: List<CatalogFile>) {
        if (groupPath !in expandedGroupPaths) return
        files.forEach { file ->
            add(
                CatalogPointerTarget(
                    index = size,
                    path = file.path
                )
            )
        }
    }

    books.forEach { group -> addFiles(group.path, group.files) }
    documents.forEach { group -> addFiles(group.path, group.files) }
    manga.forEach { group -> addFiles(group.path, group.files) }
}

/**
 * One-line human-readable summary of a group's files: count, in-progress count, completed count.
 */
internal fun List<CatalogFile>.summary(strings: AppStrings): String {
    val withProgress = count { it.readProgressPercent != null }
    val completed = count(CatalogFile::completed)
    val fileCount = size
    return buildList {
        add(strings.get("catalog_group_files_count", fileCount))
        if (withProgress > 0) add(strings.get("catalog_group_progress_count", withProgress))
        if (completed > 0) add(strings.get("catalog_group_completed_count", completed))
    }.joinToString(", ")
}

internal fun CatalogFile.displayTitle(): String = title?.takeIf { it.isNotBlank() } ?: name

internal fun CatalogFile.mangaDisplayTitle(): String =
    name.bookTitleWithoutExtension().ifBlank { displayTitle() }

internal fun MangaSeriesGroup.subtitle(strings: AppStrings): String = lastReadFile?.let { file ->
    strings.get(
        "catalog_label_last_read",
        "${file.mangaDisplayTitle()}${file.progressSuffix(strings)}"
    )
} ?: latestFile?.let { file ->
    strings.get("catalog_label_latest", file.mangaDisplayTitle())
} ?: strings.catalogNoFiles

internal fun CatalogFile.progressSuffix(strings: AppStrings): String =
    progressText(strings)?.let { " | $it" }.orEmpty()

internal fun CatalogFile.progressText(strings: AppStrings): String? {
    val progress = readProgressPercent
    return when {
        completed -> strings.catalogStatusCompleted
        progress != null -> strings.get("catalog_read_percentage", progress)
        else -> null
    }
}

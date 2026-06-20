package com.cybercat.ebooksender.data.catalog

import com.cybercat.ebooksender.domain.NaturalSort
import com.cybercat.ebooksender.model.CatalogFile
import com.cybercat.ebooksender.model.CatalogGroup
import com.cybercat.ebooksender.model.MangaSeriesGroup

internal fun <T, G> List<T>.groupCatalogFiles(
    root: String,
    fallbackName: String,
    filePath: (T) -> String,
    deduplicate: (List<T>) -> List<T> = { it },
    groupName: (T) -> String? = { file -> filePath(file).directoryAfter(root) },
    toCatalogFiles: (List<T>) -> List<CatalogFile>,
    createGroup: (name: String, path: String, files: List<CatalogFile>) -> G,
    groupFiles: (G) -> List<CatalogFile>,
    groupSortName: (G) -> String
): List<G> = filter { file -> filePath(file).isUnder(root) }
    .let(deduplicate)
    .groupBy { file -> groupName(file) ?: fallbackName }
    .map { (name, files) ->
        createGroup(name, "$root/$name", toCatalogFiles(files))
    }
    .filterNot { group -> groupFiles(group).isEmpty() }
    .sortedWith(NaturalSort.by(groupSortName))

internal fun buildCatalogGroupsWithFallback(
    root: String,
    rootFiles: List<CatalogFile>,
    fallbackName: String,
    folderGroups: List<CatalogGroup>
): List<CatalogGroup> =
    (listOfNotNull(rootFiles.toCatalogGroupOrNull(fallbackName, root)) + folderGroups)
        .sortedWith(NaturalSort.by { group -> group.name })

internal fun List<CatalogFile>.toCatalogGroupOrNull(name: String, path: String): CatalogGroup? {
    if (isEmpty()) return null
    return CatalogGroup(
        name = name,
        path = path,
        files = sortedWith(NaturalSort.by { file -> file.path })
    )
}

internal fun List<CatalogFile>.toMangaSeriesGroupOrNull(
    name: String,
    path: String,
    lastReadFile: CatalogFile? = null
): MangaSeriesGroup? {
    if (isEmpty()) return null
    val sortedFiles = sortedWith(NaturalSort.by { file -> file.path })
    return MangaSeriesGroup(
        name = name,
        path = path,
        latestFile = sortedFiles.lastOrNull(),
        lastReadFile = lastReadFile,
        files = sortedFiles
    )
}

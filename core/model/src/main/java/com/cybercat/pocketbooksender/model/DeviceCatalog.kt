package com.cybercat.pocketbooksender.model

data class DeviceCatalog(
    val books: List<CatalogGroup> = emptyList(),
    val documents: List<CatalogGroup> = emptyList(),
    val manga: List<MangaSeriesGroup> = emptyList(),
    val scannedAtMillis: Long? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean
        get() = books.isEmpty() && documents.isEmpty() && manga.isEmpty()
}

data class CatalogGroup(
    val name: String,
    val path: String,
    val files: List<CatalogFile>,
)

data class MangaSeriesGroup(
    val name: String,
    val path: String,
    val latestFile: CatalogFile?,
    val lastReadFile: CatalogFile? = null,
    val files: List<CatalogFile>,
)

data class CatalogFile(
    val name: String,
    val path: String,
    val size: Long,
    val modifiedAtMillis: Long?,
    val title: String? = null,
    val authors: List<String> = emptyList(),
    val readProgressPercent: Int? = null,
    val completed: Boolean = false,
    val lastOpenedAtMillis: Long? = null,
    val currentPage: Int? = null,
    val totalPages: Int? = null,
    val series: String? = null,
)

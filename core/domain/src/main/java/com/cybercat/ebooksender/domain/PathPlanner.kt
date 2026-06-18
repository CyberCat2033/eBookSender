package com.cybercat.ebooksender.domain

import com.cybercat.ebooksender.model.AppSettings
import com.cybercat.ebooksender.model.BookCategory
import com.cybercat.ebooksender.model.UploadItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PathPlanner @Inject constructor() {
    fun plan(item: UploadItem, settings: AppSettings): String {
        val finalExtension = item.extension.lowercase()
        val title = FilenameSanitizer.fileTitle(item.title, "Untitled")

        return when (item.category) {
            BookCategory.Books -> {
                val author = FilenameSanitizer.directoryName(item.author, "Unknown_Author")
                val fileName = renderFileName(
                    item = item,
                    template = settings.bookFileNameTemplate,
                    fallback = title,
                )
                "${settings.booksFolderName}/$author/$fileName.$finalExtension"
            }
            BookCategory.Documents -> {
                val tag = FilenameSanitizer.directoryName(
                    item.documentsTag,
                    settings.defaultDocumentsTag,
                )
                val fileName = renderFileName(
                    item = item,
                    template = settings.documentsFileNameTemplate,
                    fallback = title,
                )
                "${settings.documentsFolderName}/$tag/$fileName.$finalExtension"
            }
            BookCategory.Manga -> {
                val series = FilenameSanitizer.directoryName(
                    item.mangaSeries,
                    settings.defaultMangaSeries,
                )
                val volume = FilenameSanitizer.fileTitle(item.mangaVolume ?: item.title, title)
                val fileName = renderFileName(
                    item = item,
                    template = settings.mangaFileNameTemplate,
                    fallback = volume,
                )
                "${settings.mangaFolderName}/$series/$fileName.$finalExtension"
            }
        }
    }

    private fun renderFileName(
        item: UploadItem,
        template: String,
        fallback: String,
    ): String {
        val tokens = mapOf(
            "title" to item.title,
            "author" to item.author.orEmpty(),
            "tag" to item.documentsTag.orEmpty(),
            "series" to (item.mangaSeries ?: item.series).orEmpty(),
            "volume" to item.mangaVolume.orEmpty(),
            "year" to item.year.orEmpty(),
            "language" to item.language.orEmpty(),
            "index" to item.seriesIndex.orEmpty(),
            "publisher" to item.publisher.orEmpty(),
            "ext" to item.extension,
            "original" to item.originalName.removeSuffix(".${item.extension}"),
        )

        val rendered = tokens.entries.fold(template.ifBlank { "{title}" }) { current, (key, value) ->
            current.replace("{$key}", value)
        }
        return FilenameSanitizer.fileTitle(rendered, fallback)
    }
}

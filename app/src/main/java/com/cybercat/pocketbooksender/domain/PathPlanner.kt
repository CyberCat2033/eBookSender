package com.cybercat.pocketbooksender.domain

import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.UploadItem
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
                    template = settings.bookFileNameTemplate,
                    fallback = title,
                    tokens = mapOf(
                        "title" to item.title,
                        "author" to item.author.orEmpty(),
                    ),
                )
                "Books/$author/$fileName.$finalExtension"
            }
            BookCategory.Programming -> {
                val tag = FilenameSanitizer.directoryName(
                    item.programmingTag,
                    settings.defaultProgrammingTag,
                )
                val fileName = renderFileName(
                    template = settings.programmingFileNameTemplate,
                    fallback = title,
                    tokens = mapOf(
                        "title" to item.title,
                        "tag" to tag,
                    ),
                )
                "Programming/$tag/$fileName.$finalExtension"
            }
            BookCategory.Manga -> {
                val series = FilenameSanitizer.directoryName(
                    item.mangaSeries,
                    settings.defaultMangaSeries,
                )
                val volume = FilenameSanitizer.fileTitle(item.mangaVolume ?: item.title, title)
                val fileName = renderFileName(
                    template = settings.mangaFileNameTemplate,
                    fallback = volume,
                    tokens = mapOf(
                        "title" to item.title,
                        "series" to series,
                        "volume" to volume,
                    ),
                )
                "Manga/$series/$fileName.$finalExtension"
            }
        }
    }

    private fun renderFileName(
        template: String,
        fallback: String,
        tokens: Map<String, String>,
    ): String {
        val rendered = tokens.entries.fold(template.ifBlank { "{title}" }) { current, (key, value) ->
            current.replace("{$key}", value)
        }
        return FilenameSanitizer.fileTitle(rendered, fallback)
    }
}

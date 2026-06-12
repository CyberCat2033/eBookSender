package com.cybercat.pocketbooksender.domain

import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.UploadItem

class PathPlanner {
    fun plan(item: UploadItem, settings: AppSettings): String {
        val finalExtension = item.extension.lowercase()
        val title = FilenameSanitizer.fileTitle(item.title, "Untitled")

        return when (item.category) {
            BookCategory.Books -> {
                val author = FilenameSanitizer.directoryName(item.author, "Unknown_Author")
                "Books/$author/$title.$finalExtension"
            }
            BookCategory.Programming -> {
                val tag = FilenameSanitizer.directoryName(
                    item.programmingTag,
                    settings.defaultProgrammingTag,
                )
                "Programming/$tag/$title.$finalExtension"
            }
            BookCategory.Manga -> {
                val series = FilenameSanitizer.directoryName(
                    item.mangaSeries,
                    settings.defaultMangaSeries,
                )
                val volume = FilenameSanitizer.fileTitle(item.mangaVolume ?: item.title, title)
                "Manga/$series/$volume.$finalExtension"
            }
        }
    }
}

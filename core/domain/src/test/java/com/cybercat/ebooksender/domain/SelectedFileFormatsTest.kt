package com.cybercat.ebooksender.domain

import com.cybercat.ebooksender.model.AppSettings
import com.cybercat.ebooksender.model.BookCategory
import com.cybercat.ebooksender.model.UploadItem
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedFileFormatsTest {
    @Test
    fun alternativeFormatsKeepTheirExtensionsAndDestinationCategories() {
        val expectedCategories = mapOf(
            "djv" to BookCategory.Documents,
            "dot" to BookCategory.Documents,
            "prc" to BookCategory.Books,
            "azw" to BookCategory.Books,
            "rtx" to BookCategory.Books,
            "text" to BookCategory.Books,
            "diff" to BookCategory.Books,
            "po" to BookCategory.Books,
            "log" to BookCategory.Books,
            "ini" to BookCategory.Books,
            "conf" to BookCategory.Books
        )
        val classifier = FileClassifier()
        val planner = PathPlanner()
        val settings = AppSettings(booksFolderName = "Books", documentsFolderName = "Documents")

        for ((extension, category) in expectedCategories) {
            for (suffix in listOf(extension, "$extension.zip")) {
                val name = "Selected file.${suffix.uppercase()}"
                assertEquals(name, suffix, name.bookExtension())
                assertEquals(name, extension, name.contentExtension())
                assertEquals(name, "Selected file", name.bookTitleWithoutExtension())
                assertEquals(name, category, classifier.classify(name))

                val item = UploadItem(
                    id = name,
                    sourceUri = "content://files/$name",
                    originalName = name,
                    extension = name.bookExtension(),
                    category = classifier.classify(name),
                    title = name.bookTitleWithoutExtension(),
                    author = "Author",
                    documentsTag = "Tag",
                    plannedPath = ""
                )
                val folder = if (category ==
                    BookCategory.Documents
                ) {
                    "Documents/Tag"
                } else {
                    "Books/Author"
                }
                assertEquals(name, "$folder/Selected_file.$suffix", planner.plan(item, settings))
            }
        }
    }
}

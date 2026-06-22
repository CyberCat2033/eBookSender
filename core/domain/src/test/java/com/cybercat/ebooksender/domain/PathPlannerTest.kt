package com.cybercat.ebooksender.domain

import com.cybercat.ebooksender.model.AppSettings
import com.cybercat.ebooksender.model.BookCategory
import com.cybercat.ebooksender.model.UploadItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PathPlannerTest {

    private val planner = PathPlanner()

    @Test
    fun testPlanBooksDefaultTemplate() {
        val item = UploadItem(
            id = "1",
            sourceUri = "content://file",
            originalName = "War and Peace.epub",
            extension = "epub",
            category = BookCategory.Books,
            title = "War and Peace",
            author = "Leo Tolstoy",
            plannedPath = ""
        )
        val settings = AppSettings(
            booksFolderName = "MyBooks",
            bookFileNameTemplate = "{title}"
        )
        val path = planner.plan(item, settings)
        assertEquals("MyBooks/Leo Tolstoy/War_and_Peace.epub", path)
    }

    @Test
    fun testPlanBooksCustomTemplate() {
        val item = UploadItem(
            id = "1",
            sourceUri = "content://file",
            originalName = "War and Peace.epub",
            extension = "epub",
            category = BookCategory.Books,
            title = "War and Peace",
            author = "Leo Tolstoy",
            year = "1869",
            plannedPath = ""
        )
        val settings = AppSettings(
            booksFolderName = "MyBooks",
            bookFileNameTemplate = "{author} - {title} ({year})"
        )
        val path = planner.plan(item, settings)
        assertEquals("MyBooks/Leo Tolstoy/Leo_Tolstoy_-_War_and_Peace_(1869).epub", path)
    }

    @Test
    fun testPlanDocuments() {
        val item = UploadItem(
            id = "2",
            sourceUri = "content://doc",
            originalName = "invoice.pdf",
            extension = "pdf",
            category = BookCategory.Documents,
            title = "invoice",
            documentsTag = "Finance",
            plannedPath = ""
        )
        val settings = AppSettings(
            documentsFolderName = "Docs",
            documentsFileNameTemplate = "Doc_{title}"
        )
        val path = planner.plan(item, settings)
        assertEquals("Docs/Finance/Doc_invoice.pdf", path)
    }

    @Test
    fun testPlanDocumentsDefaultTag() {
        val item = UploadItem(
            id = "2",
            sourceUri = "content://doc",
            originalName = "invoice.pdf",
            extension = "pdf",
            category = BookCategory.Documents,
            title = "invoice",
            documentsTag = null,
            plannedPath = ""
        )
        val settings = AppSettings(
            documentsFolderName = "Docs",
            defaultDocumentsTag = "General"
        )
        val path = planner.plan(item, settings)
        assertEquals("Docs/General/invoice.pdf", path)
    }

    @Test
    fun testPlanManga() {
        val item = UploadItem(
            id = "3",
            sourceUri = "content://manga",
            originalName = "Naruto_01.cbz",
            extension = "cbz",
            category = BookCategory.Manga,
            title = "Naruto - 01",
            mangaSeries = "Naruto",
            mangaVolume = "01",
            plannedPath = ""
        )
        val settings = AppSettings(
            mangaFolderName = "Comics",
            mangaFileNameTemplate = "{series} - Vol {volume}"
        )
        val path = planner.plan(item, settings)
        assertEquals("Comics/Naruto/Naruto_-_Vol_01.cbz", path)
    }

    @Test
    fun testPlanFallbackWhenEmptyMetadata() {
        val item = UploadItem(
            id = "4",
            sourceUri = "content://unknown",
            originalName = "unknown.epub",
            extension = "epub",
            category = BookCategory.Books,
            title = "",
            author = "",
            plannedPath = ""
        )
        val settings = AppSettings(
            booksFolderName = "Books",
            bookFileNameTemplate = "{title}"
        )
        val path = planner.plan(item, settings)
        // Author fallback is "Unknown_Author"
        // Title fallback is "Untitled"
        assertEquals("Books/Unknown_Author/Untitled.epub", path)
    }
}

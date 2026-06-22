package com.cybercat.ebooksender.data.catalog

import com.cybercat.ebooksender.model.AppSettings
import com.cybercat.ebooksender.model.CatalogFallbackNames
import com.cybercat.ebooksender.model.CatalogFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTreeBuilderTest {

    private val builder = CatalogTreeBuilder()
    private val settings = AppSettings()

    @Test
    fun buildFromDatabaseFilesFiltersUnsupportedExtensions() {
        val files = listOf(
            file("Books/book1.epub"),
            file("Books/book2.exe"), // exe is unsupported
            file("Books/book3.pdf")
        )

        val catalog = builder.buildFromDatabaseFiles(files, settings, 1000L)

        val booksGroup = catalog.books
        assertEquals(1, booksGroup.size)
        val groupFiles = booksGroup.single().files
        assertEquals(2, groupFiles.size)
        assertEquals("book1.epub", groupFiles[0].name)
        assertEquals("book3.pdf", groupFiles[1].name)
    }

    @Test
    fun buildsGroupsByDirectoryStructureForBooks() {
        val files = listOf(
            file("Books/Sci-Fi/book1.epub"),
            file("Books/Fantasy/book2.epub"),
            file("Books/Fantasy/book3.epub")
        )

        val catalog = builder.buildFromDatabaseFiles(files, settings, 1000L)

        assertEquals(2, catalog.books.size)

        val fantasyGroup = catalog.books.first { it.name == "Fantasy" }
        assertEquals("Books/Fantasy", fantasyGroup.path)
        assertEquals(2, fantasyGroup.files.size)

        val scifiGroup = catalog.books.first { it.name == "Sci-Fi" }
        assertEquals("Books/Sci-Fi", scifiGroup.path)
        assertEquals(1, scifiGroup.files.size)
    }

    @Test
    fun fallsBackToAuthorsOrUnknownAuthorForBooks() {
        val files = listOf(
            file("Books/book_no_author.epub"),
            file("Books/book_with_author.epub", authors = listOf("Isaac Asimov"))
        )

        val catalog = builder.buildFromDatabaseFiles(files, settings, 1000L)

        assertEquals(2, catalog.books.size)

        val asimovGroup = catalog.books.first { it.name == "Isaac Asimov" }
        assertEquals(1, asimovGroup.files.size)
        assertEquals("book_with_author.epub", asimovGroup.files.single().name)

        val unknownGroup = catalog.books.first { it.name == CatalogFallbackNames.UNKNOWN_AUTHOR }
        assertEquals(1, unknownGroup.files.size)
        assertEquals("book_no_author.epub", unknownGroup.files.single().name)
    }

    @Test
    fun deduplicatesBasedOnRules() {
        val files = listOf(
            file("Books/book1.epub", bookId = 1, fileId = 10, modifiedAtMillis = 100L),
            file("Books/Sci-Fi/book1.epub", bookId = 1, fileId = 11, modifiedAtMillis = 100L)
        )

        val catalog = builder.buildFromDatabaseFiles(files, settings, 1000L)
        val booksGroupFiles = catalog.books.flatMap { it.files }
        assertEquals(1, booksGroupFiles.size)
        assertEquals("Books/Sci-Fi/book1.epub", booksGroupFiles.single().path)
    }

    @Test
    fun directoryAfterHelperWorks() {
        assertEquals("Sci-Fi", "Books/Sci-Fi/book.epub".directoryAfter("Books"))
        assertNull("Books/book.epub".directoryAfter("Books"))
        assertNull("Other/book.epub".directoryAfter("Books"))
    }

    @Test
    fun isUnderHelperWorks() {
        assertTrue("Books/Sci-Fi".isUnder("Books"))
        assertTrue("Books".isUnder("Books"))
        assertFalse("Book".isUnder("Books"))
    }

    @Test
    fun lastReadFileReturnsMostRecent() {
        val f1 = CatalogFile(
            name = "f1.epub",
            path = "Books/f1.epub",
            size = 100L,
            modifiedAtMillis = 1L,
            title = null,
            authors = emptyList(),
            readingProgressAvailable = true,
            readProgressPercent = null,
            completed = false,
            lastOpenedAtMillis = 100L,
            currentPage = null,
            totalPages = null,
            series = null
        )
        val f2 = CatalogFile(
            name = "f2.epub",
            path = "Books/f2.epub",
            size = 100L,
            modifiedAtMillis = 1L,
            title = null,
            authors = emptyList(),
            readingProgressAvailable = true,
            readProgressPercent = null,
            completed = false,
            lastOpenedAtMillis = 200L,
            currentPage = null,
            totalPages = null,
            series = null
        )

        assertEquals(f2, listOf(f1, f2).lastReadFile())
    }

    private fun file(
        path: String,
        name: String = path.substringAfterLast('/'),
        authors: List<String> = emptyList(),
        modifiedAtMillis: Long? = null,
        title: String? = null,
        bookId: Long? = null,
        fileId: Long? = null
    ) = DbCatalogFile(
        fileId = fileId,
        bookId = bookId,
        name = name,
        path = path,
        size = 100L,
        modifiedAtMillis = modifiedAtMillis,
        title = title,
        authors = authors,
        readProgressPercent = null,
        completed = false,
        lastOpenedAtMillis = null,
        cpage = null,
        npage = null,
        series = null
    )
}

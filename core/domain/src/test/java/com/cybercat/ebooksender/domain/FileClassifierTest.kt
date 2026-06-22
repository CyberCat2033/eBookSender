package com.cybercat.ebooksender.domain

import com.cybercat.ebooksender.model.BookCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileClassifierTest {

    private val classifier = FileClassifier()

    @Test
    fun testClassify() {
        assertEquals(BookCategory.Manga, classifier.classify("naruto.cbz"))
        assertEquals(BookCategory.Manga, classifier.classify("bleach.cbr"))
        assertEquals(BookCategory.Documents, classifier.classify("document.pdf"))
        assertEquals(BookCategory.Documents, classifier.classify("thesis.djvu"))
        assertEquals(BookCategory.Documents, classifier.classify("letter.docx"))
        assertEquals(BookCategory.Books, classifier.classify("novel.epub"))
        assertEquals(BookCategory.Books, classifier.classify("book.fb2"))
        assertEquals(BookCategory.Books, classifier.classify("story.txt"))
        assertEquals(BookCategory.Books, classifier.classify("notes.rtf"))

        // Zipped books
        assertEquals(BookCategory.Books, classifier.classify("novel.fb2.zip"))
        assertEquals(BookCategory.Books, classifier.classify("novel.epub.zip"))
    }

    @Test
    fun testBookExtension() {
        assertEquals("epub", "novel.epub".bookExtension())
        assertEquals("fb2", "book.fb2".bookExtension())
        assertEquals("fb2.zip", "book.fb2.zip".bookExtension())
        assertEquals("epub.zip", "novel.epub.zip".bookExtension())
        assertEquals("zip", "unsupported.zip".bookExtension()) // not in ZipWrappedBookExtensions
        assertEquals("txt", "folder/subfolder/file.txt".bookExtension())
        assertEquals("epub", "C:\\path\\file.epub".bookExtension())
    }

    @Test
    fun testBookTitleWithoutExtension() {
        assertEquals("novel", "novel.epub".bookTitleWithoutExtension())
        assertEquals("book", "book.fb2".bookTitleWithoutExtension())
        assertEquals("book", "book.fb2.zip".bookTitleWithoutExtension())
        assertEquals("novel", "novel.fb2.epub".bookTitleWithoutExtension())
        assertEquals("complex.name", "complex.name.epub".bookTitleWithoutExtension())
        assertEquals("file_without_extension", "file_without_extension".bookTitleWithoutExtension())
    }

    @Test
    fun testContentExtension() {
        assertEquals("epub", "novel.epub".contentExtension())
        assertEquals("fb2", "book.fb2.zip".contentExtension())
        assertEquals("epub", "novel.epub.zip".contentExtension())
    }

    @Test
    fun testIsZipWrappedBook() {
        assertTrue("book.fb2.zip".isZipWrappedBook())
        assertTrue("novel.epub.zip".isZipWrappedBook())
        assertFalse("archive.zip".isZipWrappedBook())
        assertFalse("novel.epub".isZipWrappedBook())
    }

    @Test
    fun testHasFb2EpubExtension() {
        assertTrue("book.fb2.epub".hasFb2EpubExtension())
        assertTrue("BOOK.FB2.EPUB".hasFb2EpubExtension())
        assertFalse("book.fb2".hasFb2EpubExtension())
        assertFalse("book.epub".hasFb2EpubExtension())
    }
}

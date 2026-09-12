package com.cybercat.ebooksender.data.manga

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ComxArchiveUrlResolverTest {
    @Test
    fun requestsArchiveFromReaderIdsWhenDownloadLinkIsMissing() = runBlocking {
        val url = chapter().resolveComxArchiveUrl { newsId, chapterId ->
            assertEquals(700L, newsId)
            assertEquals(11L, chapterId)
            "/download/700-11?token=test"
        }

        assertEquals("https://com-x.life/download/700-11?token=test", url)
    }

    @Test
    fun refreshesExistingDownloadLinkBeforeDownloading() = runBlocking {
        val url = chapter(downloadUrl = "https://com-x.life/download/700-11?token=old")
            .resolveComxArchiveUrl { _, _ -> "/download/700-11?token=new" }

        assertEquals("https://com-x.life/download/700-11?token=new", url)
    }

    @Test
    fun preservesDirectLinkWhenChapterHasNoApiIds() = runBlocking {
        val url = chapter(downloadUrl = "https://cdn.example.test/chapter.cbz")
            .copy(chapterId = "unknown")
            .resolveComxArchiveUrl { _, _ -> error("No API request expected") }

        assertEquals("https://cdn.example.test/chapter.cbz", url)
    }

    @Test
    fun missingApiLinkAndDirectLinkAllowsPageFallback() = runBlocking {
        assertNull(chapter().resolveComxArchiveUrl { _, _ -> "" })
    }

    @Test
    fun authorizationFailureAndCancellationAreNotHiddenByAnOldLink() = runBlocking {
        listOf(IOException("Archive unavailable"), CancellationException("Canceled"))
            .forEach { expected ->
                val error = runCatching {
                    chapter(downloadUrl = "https://com-x.life/download/700-11?token=old")
                        .resolveComxArchiveUrl { _, _ -> throw expected }
                }.exceptionOrNull()

                assertSame(expected, error)
            }
    }

    private fun chapter(downloadUrl: String? = null) = MangaChapter(
        sourceId = "comx",
        seriesId = "https://com-x.life/700-series.html",
        chapterId = "https://com-x.life/reader/700/11",
        stableKey = "700/11",
        title = "Chapter 1",
        numberForSort = 1.0,
        publishedAtMillis = null,
        downloadUrl = downloadUrl
    )
}

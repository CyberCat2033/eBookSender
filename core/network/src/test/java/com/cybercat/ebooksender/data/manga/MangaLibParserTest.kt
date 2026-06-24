package com.cybercat.ebooksender.data.manga

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaLibParserTest {
    private val parser = MangaLibParser()

    @Test
    fun searchResultsParsePublicApiItems() {
        val results = parser.parseSearchResults(
            JSONObject(
                """
                {
                  "data": [
                    {
                      "slug_url": "195--naruto",
                      "rus_name": "Наруто",
                      "name": "Naruto",
                      "cover": {
                        "default": "https://cover.cdnlibs.org/uploads/cover/naruto/cover/main.jpg"
                      },
                      "type": { "label": "Манга" },
                      "status": { "label": "Завершён" },
                      "releaseDateString": "1999 г."
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(1, results.size)
        assertEquals("mangalib", results[0].sourceId)
        assertEquals("195--naruto", results[0].seriesId)
        assertEquals("Наруто", results[0].title)
        assertEquals(
            "https://cover.cdnlibs.org/uploads/cover/naruto/cover/main.jpg",
            results[0].coverUrl
        )
        assertEquals("Манга • Завершён • 1999 г.", results[0].subtitle)
    }

    @Test
    fun seriesDetailsParseOptionalFieldsDefensively() {
        val details = parser.parseSeriesDetails(
            seriesId = "195--naruto",
            json = JSONObject(
                """
                {
                  "data": {
                    "slug_url": "195--naruto",
                    "name": "Naruto",
                    "description": "A ninja story",
                    "cover": {
                      "thumbnail": "https://cover.cdnlibs.org/uploads/cover/naruto/thumb.jpg"
                    }
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals("195--naruto", details.seriesId)
        assertEquals("Naruto", details.title)
        assertEquals("A ninja story", details.description)
        assertEquals(
            "https://cover.cdnlibs.org/uploads/cover/naruto/thumb.jpg",
            details.coverUrl
        )
    }

    @Test
    fun chaptersParseVolumeNumberAndBranchIntoStableIds() {
        val chapters = parser.parseChapters(
            seriesId = "195--naruto",
            json = JSONObject(
                """
                {
                  "data": [
                    {
                      "id": 54641,
                      "volume": "1",
                      "number": "0",
                      "name": "",
                      "branches": [
                        { "branch_id": null, "created_at": "2016-08-20T04:41:33.000000Z", "expired_type": 0 }
                      ]
                    },
                    {
                      "id": 54642,
                      "volume": "1",
                      "number": "1",
                      "name": "Удзумаки Наруто!",
                      "branches": [
                        { "branch_id": 77, "created_at": "2016-08-20T04:47:09.000000Z", "expired_type": 0 }
                      ]
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        assertEquals(2, chapters.size)
        assertEquals("195--naruto|1|0|", chapters[0].chapterId)
        assertEquals("Vol. 1 Ch. 0", chapters[0].title)
        assertEquals("195--naruto|1|1|77", chapters[1].chapterId)
        assertEquals("Vol. 1 Ch. 1 - Удзумаки Наруто!", chapters[1].title)
    }

    @Test
    fun chapterPagesResolveToDownloadServerAndRejectUnsafeUrls() {
        val pages = parser.parseChapterPages(
            chapterId = "195--naruto|1|0|",
            json = JSONObject(
                """
                {
                  "data": {
                    "pages": [
                      { "url": "//manga/naruto/chapters/1-0/01.jpg" },
                      { "url": "javascript:alert(1)" },
                      { "url": "https://img3.cdnlibs.org/manga/naruto/chapters/1-0/02.webp" }
                    ]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(2, pages.size)
        assertEquals("https://img3.cdnlibs.org/manga/naruto/chapters/1-0/01.jpg", pages[0].imageUrl)
        assertEquals("jpg", pages[0].fileExtension)
        assertEquals("https://mangalib.me/manga/195--naruto/v1/c0", pages[0].refererUrl)
        assertEquals("webp", pages[1].fileExtension)
    }

    @Test
    fun urlSafetyAcceptsOnlyExpectedHttpsHosts() {
        assertTrue(isSafeMangaLibUrl("https://api.cdnlibs.org/api/manga"))
        assertTrue(isSafeMangaLibUrl("https://img3.cdnlibs.org/manga/a.jpg"))
        assertFalse(isSafeMangaLibUrl("http://img3.cdnlibs.org/manga/a.jpg"))
        assertFalse(isSafeMangaLibUrl("https://example.org/manga/a.jpg"))
        assertFalse(isSafeMangaLibUrl("file:///tmp/a.jpg"))
    }
}

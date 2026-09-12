package com.cybercat.ebooksender.data.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ComxParserTest {
    private val parser = ComxHtmlParser(
        ComxSearchParser(),
        ComxSeriesPageParser(),
        ComxReaderPageParser()
    )

    @Test
    fun guardTokenPageRequiresBrowserSessionRefresh() {
        // Minimal, sanitized form of the challenge Com-X serves with HTTP 404.
        val html = """
            <html><body><script>
                var targetUrl = decodeURIComponent("https%3A%2F%2Fcom-x.life%2Fsearch%2Ftest");
                var p = { token: "test-token", mode: "modern" };
                var x = new XMLHttpRequest();
                x.open("POST", "/_v", true);
            </script></body></html>
        """.trimIndent()

        assertThrows(MangaBrowserSessionRefreshRequiredException::class.java) {
            parser.ensureReadableHtml(html)
        }
    }

    @Test
    fun javascriptGateRequiresBrowserSessionRefresh() {
        val html = """
            <html><body>
                <noscript>Для доступа к сайту необходимо включить JavaScript</noscript>
                <script src="/_v"></script>
            </body></html>
        """.trimIndent()

        assertThrows(MangaBrowserSessionRefreshRequiredException::class.java) {
            parser.ensureReadableHtml(html)
        }
    }

    @Test
    fun ordinaryNotFoundPageIsNotBrowserChallenge() {
        val html = "<html><body><h1>HTTP 404 — страница не найдена</h1></body></html>"

        assertFalse(parser.isGuardChallenge(html))
        parser.ensureReadableHtml(html)
    }

    @Test
    fun seriesPageParsesWindowDataFromBracketAssignment() {
        val page = ComxSeriesPageParser().parseSeriesPage(
            url = "https://com-x.life/700-test-series.html",
            html = """
                <html>
                    <head>
                        <script>
                            window["__DATA__"] = {
                                "title": "Test {Series}",
                                "poster": "/uploads/poster.webp",
                                "description": "Description with } inside text",
                                "news_id": 700,
                                "chapters": [
                                    {
                                        "id": 11,
                                        "title": "Test {Series} - Chapter 1",
                                        "posi": 1,
                                        "download_link": "/download/11.zip"
                                    },
                                    {
                                        "id": 12,
                                        "name": "Chapter 2",
                                        "number": 2
                                    }
                                ]
                            };
                        </script>
                    </head>
                    <body></body>
                </html>
            """.trimIndent()
        )

        assertNotNull(page)
        requireNotNull(page)
        assertEquals("Test {Series}", page.details.title)
        assertEquals("https://com-x.life/uploads/poster.webp", page.details.coverUrl)
        assertEquals("Description with } inside text", page.details.description)
        assertEquals(2, page.chapters.size)
        assertEquals("Chapter 1", page.chapters[0].title)
        assertEquals("https://com-x.life/reader/700/11", page.chapters[0].chapterId)
        assertEquals("https://com-x.life/download/11.zip", page.chapters[0].downloadUrl)
        assertEquals("Chapter 2", page.chapters[1].title)
    }

    @Test
    fun readerPageParsesNestedJsonImageDataFromScript() {
        val pages = ComxReaderPageParser().parseChapterPages(
            url = "https://com-x.life/reader/700/11",
            html = """
                <html>
                    <head>
                        <script>
                            const state = {
                                "reader": {
                                    "host": "https://cdn.com-x.life",
                                    "images": [
                                        "series/chapter/001.webp",
                                        "/comix/series/chapter/002.jpg"
                                    ]
                                }
                            };
                        </script>
                    </head>
                    <body>
                        <main id="reader"></main>
                    </body>
                </html>
            """.trimIndent()
        )

        assertEquals(2, pages.size)
        assertEquals(0, pages[0].index)
        assertEquals("https://cdn.com-x.life/comix/series/chapter/001.webp", pages[0].imageUrl)
        assertEquals("webp", pages[0].fileExtension)
        assertEquals(1, pages[1].index)
        assertEquals("https://cdn.com-x.life/comix/series/chapter/002.jpg", pages[1].imageUrl)
        assertEquals("jpg", pages[1].fileExtension)
    }
}

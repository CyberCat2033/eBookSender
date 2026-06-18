package com.cybercat.ebooksender.data.manga

import com.cybercat.ebooksender.data.database.dao.MangaChapterHistoryDao
import com.cybercat.ebooksender.data.database.dao.MangaSeriesBookmarkDao
import com.cybercat.ebooksender.data.database.entity.MangaChapterHistoryEntity
import com.cybercat.ebooksender.data.database.entity.MangaSeriesBookmarkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CheckMangaSubscriptionsUseCaseTest {
    @Test
    fun filtersOutAlreadyDownloadedChaptersByStableKey() = runBlocking {
        val bookmarkDao = FakeBookmarkDao(
            subscribedSeries = listOf(bookmark(seriesId = "series-1"))
        )
        val historyDao = FakeHistoryDao(downloadedStableKeys = listOf("stable-1"))
        val useCase = CheckMangaSubscriptionsUseCase(
            historyDao = historyDao,
            bookmarkDao = bookmarkDao,
            seriesPageLoader = FakeMangaSeriesPageLoader(
                responses = mapOf(
                    "source-1|series-1" to LoaderResponse.Success(
                        page(
                            seriesId = "series-1",
                            chapters = listOf(
                                chapter(stableKey = "stable-1", title = "Chapter 1"),
                                chapter(stableKey = "stable-2", title = "Chapter 2")
                            )
                        )
                    )
                )
            )
        )

        val results = useCase()

        assertEquals(1, results.size)
        assertEquals(listOf("stable-2"), results.single().newChapters.map { it.stableKey })
        assertEquals(listOf("source-1|series-1"), bookmarkDao.markCheckedCalls.map { it.key })
    }

    @Test
    fun marksSeriesAsCheckedOnlyAfterSuccessfulOpen() = runBlocking {
        val bookmarkDao = FakeBookmarkDao(
            subscribedSeries = listOf(bookmark(seriesId = "series-1"))
        )
        val useCase = CheckMangaSubscriptionsUseCase(
            historyDao = FakeHistoryDao(),
            bookmarkDao = bookmarkDao,
            seriesPageLoader = FakeMangaSeriesPageLoader(
                responses = mapOf(
                    "source-1|series-1" to LoaderResponse.Failure(IllegalStateException("boom"))
                )
            )
        )

        val results = useCase()

        assertTrue(results.isEmpty())
        assertTrue(bookmarkDao.markCheckedCalls.isEmpty())
    }

    @Test
    fun rethrowsAuthenticationExpiredError() = runBlocking {
        val useCase = CheckMangaSubscriptionsUseCase(
            historyDao = FakeHistoryDao(),
            bookmarkDao = FakeBookmarkDao(
                subscribedSeries = listOf(bookmark(seriesId = "series-1"))
            ),
            seriesPageLoader = FakeMangaSeriesPageLoader(
                responses = mapOf(
                    "source-1|series-1" to LoaderResponse.Failure(
                        MangaAuthenticationExpiredException()
                    )
                )
            )
        )

        try {
            useCase()
            fail("Expected MangaAuthenticationExpiredException")
        } catch (_: MangaAuthenticationExpiredException) {
            Unit
        }
    }

    @Test
    fun continuesCheckingOtherSeriesAfterNonAuthFailure() = runBlocking {
        val bookmarkDao = FakeBookmarkDao(
            subscribedSeries = listOf(
                bookmark(seriesId = "series-fail"),
                bookmark(seriesId = "series-ok")
            )
        )
        val useCase = CheckMangaSubscriptionsUseCase(
            historyDao = FakeHistoryDao(),
            bookmarkDao = bookmarkDao,
            seriesPageLoader = FakeMangaSeriesPageLoader(
                responses = mapOf(
                    "source-1|series-fail" to LoaderResponse.Failure(
                        IllegalArgumentException("temporary")
                    ),
                    "source-1|series-ok" to LoaderResponse.Success(
                        page(
                            seriesId = "series-ok",
                            chapters = listOf(
                                chapter(
                                    seriesId = "series-ok",
                                    stableKey = "stable-2",
                                    title = "Chapter 2"
                                )
                            )
                        )
                    )
                )
            )
        )

        val results = useCase()

        assertEquals(1, results.size)
        assertEquals("series-ok", results.single().page.details.seriesId)
        assertEquals(listOf("source-1|series-ok"), bookmarkDao.markCheckedCalls.map { it.key })
    }

    @Test
    fun reusesSingleCheckedTimestampForWholePass() = runBlocking {
        val bookmarkDao = FakeBookmarkDao(
            subscribedSeries = listOf(
                bookmark(seriesId = "series-1"),
                bookmark(seriesId = "series-2")
            )
        )
        val useCase = CheckMangaSubscriptionsUseCase(
            historyDao = FakeHistoryDao(),
            bookmarkDao = bookmarkDao,
            seriesPageLoader = FakeMangaSeriesPageLoader(
                responses = mapOf(
                    "source-1|series-1" to LoaderResponse.Success(page(seriesId = "series-1")),
                    "source-1|series-2" to LoaderResponse.Success(page(seriesId = "series-2"))
                )
            )
        )

        useCase()

        assertEquals(2, bookmarkDao.markCheckedCalls.size)
        assertEquals(1, bookmarkDao.markCheckedCalls.map { it.checkedAtMillis }.distinct().size)
    }

    private class FakeHistoryDao(private val downloadedStableKeys: List<String> = emptyList()) :
        MangaChapterHistoryDao {
        override fun observeHistory(): Flow<List<MangaChapterHistoryEntity>> = flowOf(emptyList())

        override fun observeDownloadedStableKeys(): Flow<List<String>> =
            flowOf(downloadedStableKeys)

        override suspend fun downloadedStableKeys(): List<String> = downloadedStableKeys

        override suspend fun upsertAll(items: List<MangaChapterHistoryEntity>) = Unit
    }

    private class FakeBookmarkDao(
        private val subscribedSeries: List<MangaSeriesBookmarkEntity> = emptyList()
    ) : MangaSeriesBookmarkDao {
        val markCheckedCalls = mutableListOf<MarkCheckedCall>()

        override fun observeSavedSeries(): Flow<List<MangaSeriesBookmarkEntity>> =
            flowOf(emptyList())

        override suspend fun subscribedSeries(): List<MangaSeriesBookmarkEntity> = subscribedSeries

        override suspend fun findSeries(
            sourceId: String,
            seriesId: String
        ): MangaSeriesBookmarkEntity? = error("Unused in test")

        override suspend fun upsert(series: MangaSeriesBookmarkEntity) = Unit

        override suspend fun deleteSeries(sourceId: String, seriesId: String) = Unit

        override suspend fun normalizeMutualExclusion() = Unit

        override suspend fun clearSavedSeries(): Int = 0

        override suspend fun savedSeriesCount(): Int = subscribedSeries.size

        override suspend fun upsertSnapshot(
            sourceId: String,
            seriesId: String,
            title: String,
            coverUrl: String?,
            description: String?,
            openedAtMillis: Long
        ) = Unit

        override suspend fun setFavorite(
            sourceId: String,
            seriesId: String,
            title: String,
            coverUrl: String?,
            description: String?,
            favorite: Boolean,
            updatedAtMillis: Long
        ): Int = 0

        override suspend fun setSubscribed(
            sourceId: String,
            seriesId: String,
            title: String,
            coverUrl: String?,
            description: String?,
            subscribed: Boolean,
            updatedAtMillis: Long
        ): Int = 0

        override suspend fun markChecked(
            sourceId: String,
            seriesId: String,
            checkedAtMillis: Long
        ) {
            markCheckedCalls += MarkCheckedCall(
                key = "$sourceId|$seriesId",
                checkedAtMillis = checkedAtMillis
            )
        }
    }

    private class FakeMangaSeriesPageLoader(private val responses: Map<String, LoaderResponse>) :
        MangaSeriesPageLoader {
        override suspend fun openSeries(sourceId: String, seriesId: String): MangaSeriesPage =
            when (val response = responses.getValue("$sourceId|$seriesId")) {
                is LoaderResponse.Success -> response.page
                is LoaderResponse.Failure -> throw response.error
            }
    }

    private sealed interface LoaderResponse {
        data class Success(val page: MangaSeriesPage) : LoaderResponse
        data class Failure(val error: Throwable) : LoaderResponse
    }

    private data class MarkCheckedCall(val key: String, val checkedAtMillis: Long)

    private fun bookmark(seriesId: String) = MangaSeriesBookmarkEntity(
        sourceId = "source-1",
        seriesId = seriesId,
        title = "Series $seriesId",
        coverUrl = null,
        description = null,
        favorite = false,
        subscribed = true,
        addedAtMillis = 1L,
        lastOpenedAtMillis = 1L,
        lastCheckedAtMillis = null
    )

    private fun page(seriesId: String, chapters: List<MangaChapter> = emptyList()) =
        MangaSeriesPage(
            details = MangaSeriesDetails(
                sourceId = "source-1",
                seriesId = seriesId,
                title = "Series $seriesId",
                coverUrl = null,
                description = null
            ),
            chapters = chapters
        )

    private fun chapter(stableKey: String, title: String, seriesId: String = "series-1") =
        MangaChapter(
            sourceId = "source-1",
            seriesId = seriesId,
            chapterId = "chapter-$stableKey",
            stableKey = stableKey,
            title = title,
            numberForSort = null,
            publishedAtMillis = null
        )
}

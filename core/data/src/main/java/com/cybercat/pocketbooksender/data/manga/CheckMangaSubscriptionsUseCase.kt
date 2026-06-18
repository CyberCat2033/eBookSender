package com.cybercat.pocketbooksender.data.manga

import com.cybercat.pocketbooksender.data.database.dao.MangaChapterHistoryDao
import com.cybercat.pocketbooksender.data.database.dao.MangaSeriesBookmarkDao
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CheckMangaSubscriptionsUseCase @Inject constructor(
    private val historyDao: MangaChapterHistoryDao,
    private val bookmarkDao: MangaSeriesBookmarkDao,
    private val seriesPageLoader: MangaSeriesPageLoader
) {
    suspend operator fun invoke(): List<MangaSubscriptionCheckResult> =
        withContext(Dispatchers.IO) {
            val downloadedStableKeys = historyDao.downloadedStableKeys().toSet()
            val checkedAtMillis = System.currentTimeMillis()
            bookmarkDao.subscribedSeries().mapNotNull { saved ->
                runCatching {
                    val page = seriesPageLoader.openSeries(saved.sourceId, saved.seriesId)
                    bookmarkDao.markChecked(
                        sourceId = page.details.sourceId,
                        seriesId = page.details.seriesId,
                        checkedAtMillis = checkedAtMillis
                    )
                    MangaSubscriptionCheckResult(
                        page = page,
                        newChapters = page.chapters.filter { chapter ->
                            chapter.stableKey !in downloadedStableKeys
                        }
                    )
                }.getOrElse { error ->
                    if (error is MangaAuthenticationExpiredException) {
                        throw error
                    }
                    null
                }
            }
        }
}

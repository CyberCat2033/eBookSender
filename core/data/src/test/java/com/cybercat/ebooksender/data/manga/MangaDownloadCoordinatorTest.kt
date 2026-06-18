package com.cybercat.ebooksender.data.manga

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaDownloadCoordinatorTest {
    @Test
    fun emittedEventsAreRetainedUntilTheCollectorStarts() = runBlocking {
        val coordinator = MangaDownloadCoordinator()
        val expected = MangaDownloadEvent.Completed(
            requestId = "request-1",
            kind = MangaDownloadRequestKind.SelectedChapters,
            downloadedChapterIds = setOf("chapter-1"),
            downloadedSubscriptionKeys = emptySet(),
            addedToQueueCount = 1,
            failedMessages = emptyList()
        )

        coordinator.emit(expected)

        assertEquals(expected, coordinator.events.first())
    }

    @Test
    fun emitsProgressBurstAndTerminalEventWithoutDropping() = runBlocking {
        val coordinator = MangaDownloadCoordinator()
        val progressEvents = List(100) { index ->
            MangaDownloadEvent.Progress(
                requestId = "request-1",
                progress = sampleProgress(index)
            )
        }
        val completedEvent = MangaDownloadEvent.Completed(
            requestId = "request-1",
            kind = MangaDownloadRequestKind.SubscriptionUpdates,
            downloadedChapterIds = setOf("chapter-100"),
            downloadedSubscriptionKeys = setOf("series-100"),
            addedToQueueCount = 100,
            failedMessages = emptyList()
        )
        val expectedEvents = progressEvents + completedEvent

        expectedEvents.forEach(coordinator::emit)

        assertEquals(
            expectedEvents,
            coordinator.events.take(expectedEvents.size).toList()
        )
    }

    private fun sampleProgress(index: Int) = MangaDownloadProgress(
        chapterTitle = "Chapter $index",
        totalChapters = 100,
        completedChapters = index,
        totalPages = 24,
        completedPages = (index % 24) + 1,
        detail = "Downloading archive",
        archiveBytesRead = index * 256L * 1024L,
        archiveTotalBytes = 100L * 256L * 1024L
    )
}

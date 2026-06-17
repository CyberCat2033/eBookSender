package com.cybercat.pocketbooksender.feature.manga

import com.cybercat.pocketbooksender.data.manga.MangaChapterDownloadTarget
import com.cybercat.pocketbooksender.data.manga.MangaSubscriptionCheckResult

internal object MangaSubscriptionUpdateReducer {
    fun allChapterKeys(updates: List<MangaSubscriptionCheckResult>): Set<String> =
        updates.flatMap { update ->
            update.newChapters.map { chapter -> chapter.subscriptionUpdateSelectionKey() }
        }.toSet()

    fun selectedTargets(
        updates: List<MangaSubscriptionCheckResult>,
        selectedKeys: Set<String>
    ): List<MangaChapterDownloadTarget> = updates.flatMap { update ->
        update.newChapters
            .filter { chapter -> chapter.subscriptionUpdateSelectionKey() in selectedKeys }
            .map { chapter -> MangaChapterDownloadTarget(update.page.details, chapter) }
    }

    fun remainingAfterDownload(
        updates: List<MangaSubscriptionCheckResult>,
        selectedKeys: Set<String>,
        downloadedKeys: Set<String>
    ): List<MangaSubscriptionCheckResult> = updates.mapNotNull { update ->
        val remainingChapters = update.newChapters.filter { chapter ->
            val key = chapter.subscriptionUpdateSelectionKey()
            key in selectedKeys && key !in downloadedKeys
        }
        update.copy(newChapters = remainingChapters).takeIf { remainingChapters.isNotEmpty() }
    }
}

package com.cybercat.ebooksender.feature.manga

import com.cybercat.ebooksender.data.manga.MangaDownloadProgress
import com.cybercat.ebooksender.localization.AppStrings
import com.cybercat.ebooksender.util.formatBytes

internal object MangaDownloadProgressFormatter {
    fun initialProgress(strings: AppStrings, selectedChapterCount: Int): MangaDownloadUiProgress =
        MangaDownloadUiProgress(
            title = strings.mangaStatusDownloadPreparing,
            detail = when (selectedChapterCount) {
                1 -> strings.mangaStatusDownloadOneChapterSelected
                else -> strings.get("manga_status_download_chapters_selected", selectedChapterCount)
            },
            currentChapterTitle = null,
            progress = null
        )

    fun format(
        progress: MangaDownloadProgress,
        strings: AppStrings,
        previousProgress: Float?
    ): MangaDownloadUiProgress {
        val safeTotalChapters = progress.totalChapters.coerceAtLeast(1)
        val completedChapterCount = progress.completedChapters.coerceIn(0, safeTotalChapters)
        val allChaptersComplete = completedChapterCount >= safeTotalChapters
        val archiveTotal = progress.archiveTotalBytes?.takeIf { it > 0L }
        val archiveFraction = archiveTotal?.let { total ->
            progress.archiveBytesRead.toFloat() / total.toFloat()
        }
        val pageFraction = when {
            allChaptersComplete -> 0f

            archiveFraction != null -> archiveFraction

            progress.totalPages > 0 -> progress.completedPages.toFloat() /
                progress.totalPages.toFloat()

            else -> 0f
        }.coerceIn(0f, 1f)
        val progressCap = if (allChaptersComplete) 1f else 0.99f
        val previousSafeProgress = previousProgress?.coerceAtMost(progressCap) ?: 0f
        val progressValue = ((completedChapterCount + pageFraction) / safeTotalChapters)
            .coerceIn(0f, 1f)
            .coerceAtMost(progressCap)
            .coerceAtLeast(previousSafeProgress)

        val step = progress.localizedStep(strings)
        val chapterProgressText = if (allChaptersComplete) {
            strings.mangaProgressAllChaptersSaved
        } else {
            strings.get("manga_progress_chapters_done", completedChapterCount, safeTotalChapters)
        }
        val detailText = when {
            allChaptersComplete ->
                chapterProgressText

            archiveTotal != null && progress.detail.equals(
                ARCHIVE_DOWNLOAD_DETAIL,
                ignoreCase = true
            ) ->
                strings.get(
                    "manga_progress_detail_archive",
                    chapterProgressText,
                    progress.archiveBytesRead.formatBytes(),
                    archiveTotal.formatBytes()
                )

            progress.totalPages > 0 && progress.completedPages >= progress.totalPages ->
                strings.get("manga_progress_detail_finalizing", chapterProgressText)

            progress.totalPages > 0 && progress.detail.isNullOrBlank() ->
                strings.get(
                    "manga_progress_detail_page",
                    chapterProgressText,
                    progress.completedPages,
                    progress.totalPages
                )

            else ->
                strings.get("manga_progress_detail_generic", chapterProgressText, step ?: "")
        }

        return MangaDownloadUiProgress(
            title = strings.get("manga_download_progress_title", (progressValue * 100).toInt()),
            detail = detailText,
            currentChapterTitle = progress.chapterTitle,
            progress = progressValue
        )
    }

    private fun MangaDownloadProgress.localizedStep(strings: AppStrings): String? = when {
        detail.equals("Preparing", ignoreCase = true) -> strings.mangaProgressStepPreparing

        detail.equals(
            ARCHIVE_DOWNLOAD_DETAIL,
            ignoreCase = true
        ) -> strings.mangaProgressStepDownloadingArchive

        detail.equals(
            "Archive downloaded",
            ignoreCase = true
        ) -> strings.mangaProgressStepArchiveSaved

        detail.equals("Archive unavailable, downloading pages", ignoreCase = true) ->
            strings.mangaProgressStepSwitchingToPages

        !detail.isNullOrBlank() -> detail

        totalPages > 0 -> strings.mangaProgressStepDownloadingPages

        else -> strings.mangaProgressStepDownloadingChapter
    }

    private const val ARCHIVE_DOWNLOAD_DETAIL = "Downloading archive"
}

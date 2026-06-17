package com.cybercat.pocketbooksender.data.manga

import java.io.File
import kotlinx.coroutines.CancellationException

data class MangaDownloadResult(
    val downloaded: List<MangaDownloadedChapter>,
    val failedMessages: List<String>
)

class MangaDownloadCancelledException(val result: MangaDownloadResult) :
    CancellationException("Manga download canceled")

data class MangaDownloadedChapter(
    val series: MangaSeriesDetails,
    val chapter: MangaChapter,
    val file: File
)

data class MangaChapterDownloadTarget(val series: MangaSeriesDetails, val chapter: MangaChapter)

data class MangaDownloadProgress(
    val chapterTitle: String,
    val totalChapters: Int,
    val completedChapters: Int,
    val totalPages: Int,
    val completedPages: Int,
    val detail: String? = null,
    val archiveBytesRead: Long = 0L,
    val archiveTotalBytes: Long? = null
)

package com.cybercat.ebooksender.data.manga

internal suspend fun MangaChapter.resolveComxArchiveUrl(
    authorize: suspend (newsId: Long, chapterId: Long) -> String
): String? {
    val fallbackUrl = downloadUrl?.takeIf { it.isNotBlank() }
    val readerIds = READER_IDS.find(chapterId)?.groupValues
    val downloadIds = fallbackUrl?.let { DOWNLOAD_IDS.find(it)?.groupValues }
    val newsId = readerIds?.get(1)?.toLongOrNull()
        ?: seriesId.extractNewsId()
        ?: downloadIds?.get(1)?.toLongOrNull()
        ?: return fallbackUrl
    val chapterNumber = readerIds?.get(2)?.toLongOrNull()
        ?: downloadIds?.get(2)?.toLongOrNull()
        ?: return fallbackUrl

    return authorize(newsId, chapterNumber)
        .takeIf { it.isNotBlank() }
        ?.resolveAgainst(fallbackUrl ?: seriesId)
        ?: fallbackUrl
}

private val READER_IDS = Regex("""/reader/(\d+)/(\d+)""")
private val DOWNLOAD_IDS = Regex("""/download/(\d+)-(\d+)""")

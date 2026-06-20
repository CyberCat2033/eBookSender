package com.cybercat.ebooksender.data.manga

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaSourceRegistry @Inject constructor(
    adapterSet: Set<@JvmSuppressWildcards HtmlMangaSourceAdapter>
) {
    private val adapters: List<HtmlMangaSourceAdapter> = adapterSet.sortedBy { adapter ->
        adapter.title
    }

    val sources: List<MangaSourceSummary> =
        adapters.map { adapter ->
            MangaSourceSummary(
                id = adapter.id,
                title = adapter.title,
                homeUrl = adapter.homeUrl,
                browserUserAgent = adapter.browserUserAgent,
                loginUrl = adapter.loginUrl,
                nativeLoginConfig = adapter.nativeLoginConfig
            )
        }

    fun adapter(sourceId: String): HtmlMangaSourceAdapter =
        adapters.firstOrNull { adapter -> adapter.id == sourceId }
            ?: throw IllegalArgumentException("Unknown manga source: $sourceId")

    fun homeUrl(sourceId: String): String =
        adapters.firstOrNull { adapter -> adapter.id == sourceId }?.homeUrl
            ?: throw IllegalArgumentException("Unknown manga source: $sourceId")
}

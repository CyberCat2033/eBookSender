package com.cybercat.pocketbooksender.feature.opds

import com.cybercat.pocketbooksender.data.opds.OpdsLink
import com.cybercat.pocketbooksender.data.opds.OpdsSource

internal fun OpdsLink.normalizedRelName(): String? =
    rel
        ?.substringAfterLast('/')
        ?.lowercase()
        ?.takeIf(String::isNotBlank)

internal fun OpdsLink.isStartLink(): Boolean =
    normalizedRelName() == "start"

internal fun OpdsLink.isNextLink(): Boolean =
    normalizedRelName() == "next"

internal fun OpdsLink.isPreviousLink(): Boolean =
    normalizedRelName() in setOf("previous", "prev")

internal fun OpdsLink.isPageNavigationLink(): Boolean =
    isNextLink() || isPreviousLink()

internal fun OpdsLink.isBrowsableFeedLink(): Boolean {
    val relValue = normalizedRelName().orEmpty()
    val typeValue = type.orEmpty()
    return relValue in setOf("next", "previous", "prev", "start") ||
        (relValue !in setOf("self", "up") && typeValue.contains("profile=opds-catalog"))
}

internal fun OpdsLink.isRedundantStartLink(
    currentUrl: String?,
    sources: List<OpdsSource>,
): Boolean {
    if (!isStartLink() || currentUrl.isNullOrBlank()) return false

    val currentKey = currentUrl.navigationUrlKey()
    val targetKey = href.navigationUrlKey()
    if (currentKey == targetKey) return true

    return sources.any { source ->
        val sourceKey = source.url.navigationUrlKey()
        currentKey == sourceKey && targetKey == sourceKey
    }
}

private fun String.navigationUrlKey(): String =
    trim()
        .trimEnd('/')
        .lowercase()

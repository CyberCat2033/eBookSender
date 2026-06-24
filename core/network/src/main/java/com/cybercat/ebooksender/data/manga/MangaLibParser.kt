package com.cybercat.ebooksender.data.manga

import javax.inject.Inject
import org.json.JSONObject

class MangaLibParser @Inject constructor() {
    fun parseSearchResults(json: JSONObject): List<MangaSeriesSearchResult> {
        val items = json.mangaLibDataArray() ?: return emptyList()
        return (0 until items.length())
            .asSequence()
            .mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val slugUrl = item.optString("slug_url").takeIf { it.isNotBlank() }
                    ?: item.optString("slug").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val title =
                    item.mangaLibTitle().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val subtitle = listOfNotNull(
                    item.optJSONObject("type")?.optString("label")?.takeIf { it.isNotBlank() },
                    item.optJSONObject("status")?.optString("label")?.takeIf { it.isNotBlank() },
                    item.optString("releaseDateString").takeIf { it.isNotBlank() }
                ).joinToString(" • ").takeIf { it.isNotBlank() }

                MangaSeriesSearchResult(
                    sourceId = MangaLibMangaAdapter.SOURCE_ID,
                    seriesId = slugUrl,
                    title = title,
                    coverUrl = item.mangaLibCoverUrl(),
                    subtitle = subtitle
                )
            }
            .distinctBy { result -> result.seriesId }
            .toList()
    }

    fun parseSeriesDetails(seriesId: String, json: JSONObject): MangaSeriesDetails {
        val data = json.mangaLibDataObject() ?: throw MangaNotFoundException(404, "Manga not found")
        val slugUrl = data.optString("slug_url").takeIf { it.isNotBlank() } ?: seriesId
        return MangaSeriesDetails(
            sourceId = MangaLibMangaAdapter.SOURCE_ID,
            seriesId = slugUrl,
            title = data.mangaLibTitle().ifBlank { seriesId },
            coverUrl = data.mangaLibCoverUrl(),
            description = firstNonBlank(
                data.optString("summary"),
                data.optString("description"),
                data.optString("annotation")
            ).ifBlank { null }
        )
    }

    fun parseChapters(seriesId: String, json: JSONObject): List<MangaChapter> {
        val chapters = json.mangaLibDataArray() ?: return emptyList()
        return (0 until chapters.length())
            .asSequence()
            .mapNotNull { index ->
                val item = chapters.optJSONObject(index) ?: return@mapNotNull null
                val volume = item.optString("volume").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val number = item.optString("number").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val branch = firstReadableBranch(item)
                val branchId = branch?.opt("branch_id")?.toString()?.takeIf {
                    it.isNotBlank() && it != "null"
                }
                val name = item.optString("name").cleanWhitespace()
                val chapterId = MangaLibChapterId(
                    seriesId = seriesId,
                    volume = volume,
                    number = number,
                    branchId = branchId
                ).encode()
                val title = buildString {
                    append("Vol. ")
                    append(volume)
                    append(" Ch. ")
                    append(number)
                    if (name.isNotBlank()) {
                        append(" - ")
                        append(name)
                    }
                }
                val sortNumber = item.optFiniteDouble("number")
                    ?: item.optFiniteDouble("item_number")
                    ?: number.replace(',', '.').toDoubleOrNull()
                    ?: (index + 1).toDouble()

                MangaChapter(
                    sourceId = MangaLibMangaAdapter.SOURCE_ID,
                    seriesId = seriesId,
                    chapterId = chapterId,
                    stableKey = chapterId,
                    title = title,
                    numberForSort = sortNumber,
                    publishedAtMillis = branch?.optString("created_at")
                        ?.takeIf { it.isNotBlank() }
                        ?.mangaLibInstantMillisOrNull()
                )
            }
            .distinctBy { chapter -> chapter.stableKey }
            .sortedWith(
                compareBy<MangaChapter> { it.numberForSort ?: Double.MAX_VALUE }
                    .thenBy { it.title }
            )
            .toList()
    }

    fun parseChapterPages(chapterId: String, json: JSONObject): List<MangaPage> {
        val parsed = MangaLibChapterId.decode(chapterId)
        val data = json.mangaLibDataObject() ?: return emptyList()
        val pages = data.optJSONArray("pages") ?: return emptyList()
        val refererUrl = MangaLibMangaAdapter.chapterWebUrl(parsed)
        return (0 until pages.length())
            .asSequence()
            .mapNotNull { index ->
                val item = pages.optJSONObject(index) ?: return@mapNotNull null
                val imageUrl = resolvePageImageUrl(item.optString("url"))
                    ?: resolvePageImageUrl(item.optString("image"))
                    ?: return@mapNotNull null
                MangaPage(
                    index = index,
                    imageUrl = imageUrl,
                    refererUrl = refererUrl,
                    fileExtension = mangaLibImageExtensionFromUrl(imageUrl)
                )
            }
            .toList()
    }

    fun imageExtensionFromUrl(url: String): String? = mangaLibImageExtensionFromUrl(url)

    private fun firstReadableBranch(item: JSONObject): JSONObject? {
        val branches = item.optJSONArray("branches") ?: return null
        return (0 until branches.length())
            .asSequence()
            .mapNotNull { index -> branches.optJSONObject(index) }
            .firstOrNull { branch -> branch.optInt("expired_type", 0) == 0 }
            ?: branches.optJSONObject(0)
    }

    private fun resolvePageImageUrl(rawUrl: String): String? {
        val raw = rawUrl.replace("\\/", "/").trim()
        if (raw.isBlank()) return null
        val resolved = when {
            raw.startsWith("https://") -> raw

            raw.startsWith(
                "//manga/"
            ) -> "${MangaLibMangaAdapter.IMAGE_BASE_URL}/${raw.removePrefix("//")}"

            raw.startsWith("/manga/") -> "${MangaLibMangaAdapter.IMAGE_BASE_URL}$raw"

            raw.startsWith("manga/") -> "${MangaLibMangaAdapter.IMAGE_BASE_URL}/$raw"

            else -> return null
        }
        return resolved.takeIf(::isSafeMangaLibUrl)
    }
}

data class MangaLibChapterId(
    val seriesId: String,
    val volume: String,
    val number: String,
    val branchId: String?
) {
    fun encode(): String = listOf(
        seriesId,
        volume,
        number,
        branchId.orEmpty()
    ).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = "|"

        fun decode(value: String): MangaLibChapterId {
            val parts = value.split(SEPARATOR)
            require(parts.size >= 3) { "Invalid MangaLib chapter id" }
            return MangaLibChapterId(
                seriesId = parts[0],
                volume = parts[1],
                number = parts[2],
                branchId = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
            )
        }
    }
}

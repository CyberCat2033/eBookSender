package com.cybercat.ebooksender.metadata

import android.net.Uri
import com.cybercat.ebooksender.domain.NaturalSort
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream
import javax.inject.Inject
import org.xmlpull.v1.XmlPullParser

class EpubMetadataParser @Inject constructor() {
    fun extract(uri: Uri, fallbackTitle: String, openStream: (Uri) -> InputStream): BookMetadata {
        val scan = openStream(uri).use(::scanArchive)
        val selectedOpf = resolveOpfEntry(scan) ?: return BookMetadata(title = fallbackTitle)

        val parsed = parseOpf(selectedOpf.bytes, fallbackTitle)
        val coverTargets = buildCoverTargets(selectedOpf.entryName, parsed)
        val coverScan = openStream(uri).use { scanCoverAssets(it, coverTargets) }
        val coverBytes = selectCoverBytes(uri, openStream, coverTargets, coverScan)

        return BookMetadata(
            title = parsed.title,
            authors = parsed.authors,
            preview = coverBytes?.let(MetadataPreviewDecoder::decodeBitmap),
            series = parsed.series,
            seriesIndex = parsed.seriesIndex,
            publisher = parsed.publisher,
            year = parsed.year
        )
    }

    private fun scanArchive(input: InputStream): EpubScan = ZipInputStream(input).use { zip ->
        var containerBytes: ByteArray? = null
        var firstOpfEntryName: String? = null
        val opfEntries = linkedMapOf<String, ByteArray>()

        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) {
                zip.closeEntry()
                continue
            }

            when {
                entry.name.equals(CONTAINER_ENTRY_NAME, ignoreCase = true) -> {
                    containerBytes = zip.readCurrentEntry(MAX_TEXT_BYTES)
                }

                entry.name.lowercase().endsWith(".opf") -> {
                    if (firstOpfEntryName == null) {
                        firstOpfEntryName = entry.name
                    }
                    opfEntries[entry.name] = zip.readCurrentEntry(MAX_TEXT_BYTES)
                }

                else -> zip.closeEntry()
            }
        }

        EpubScan(
            containerBytes = containerBytes,
            firstOpfEntryName = firstOpfEntryName,
            opfEntries = opfEntries
        )
    }

    private fun resolveOpfEntry(scan: EpubScan): SelectedOpf? {
        val preferredPath = scan.containerBytes?.let(::parseContainerRootfilePath)
        if (preferredPath != null) {
            scan.opfEntries.entries
                .firstOrNull { (entryName, _) ->
                    entryName.equals(preferredPath, ignoreCase = true)
                }
                ?.let { (entryName, bytes) ->
                    return SelectedOpf(entryName = entryName, bytes = bytes)
                }
        }

        val fallbackEntryName = scan.firstOpfEntryName ?: return null
        val fallbackBytes = scan.opfEntries[fallbackEntryName] ?: return null
        return SelectedOpf(entryName = fallbackEntryName, bytes = fallbackBytes)
    }

    private fun parseContainerRootfilePath(bytes: ByteArray): String? {
        val parser = newMetadataXmlParser(ByteArrayInputStream(bytes))
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name.substringAfter(':') != "rootfile") continue

            val fullPath = parser.getAttributeValue(null, "full-path")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: continue
            return resolveZipPath(baseDir = "", href = fullPath)
        }
        return null
    }

    private fun parseOpf(bytes: ByteArray, fallbackTitle: String): OpfMetadata {
        val parser = newMetadataXmlParser(ByteArrayInputStream(bytes))
        var title: String? = null
        val authors = mutableListOf<String>()
        var coverId: String? = null
        val manifest = mutableListOf<ManifestItem>()
        var propertiesCoverHref: String? = null
        var guideCoverHref: String? = null

        var series: String? = null
        var seriesIndex: String? = null
        var publisher: String? = null
        var year: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            val localName = parser.name.substringAfter(':')
            when (localName) {
                "title" -> if (title == null) title = parser.nextTextSafe()

                "creator" -> authors += parser.nextTextSafe()

                "publisher" -> if (publisher == null) publisher = parser.nextTextSafe().trim()

                "date" -> {
                    val dateText = parser.nextTextSafe()
                    val regex = Regex("""\b\d{4}\b""")
                    val maybeYear = regex.find(dateText)?.value
                    if (maybeYear != null && year == null) {
                        year = maybeYear
                    }
                }

                "meta" -> {
                    val nameAttr = parser.getAttributeValue(null, "name")
                    val contentAttr = parser.getAttributeValue(null, "content")
                    val propertyAttr = parser.getAttributeValue(null, "property")

                    if (nameAttr == "cover") {
                        coverId = contentAttr
                    }

                    // Calibre EPUB 2 series tags
                    if (nameAttr == "calibre:series" && !contentAttr.isNullOrBlank()) {
                        series = contentAttr.trim()
                    }
                    if (nameAttr == "calibre:series_index" && !contentAttr.isNullOrBlank()) {
                        seriesIndex = contentAttr.trim()
                    }

                    // EPUB 3 series tags
                    if (propertyAttr == "belongs-to-collection") {
                        val collectionName = parser.nextTextSafe().trim()
                        if (collectionName.isNotBlank()) {
                            series = collectionName
                        }
                    }
                    if (propertyAttr == "group-position") {
                        val position = parser.nextTextSafe().trim()
                        if (position.isNotBlank()) {
                            seriesIndex = position
                        }
                    }
                }

                "item" -> {
                    val id = parser.getAttributeValue(null, "id")
                    val href = parser.getAttributeValue(null, "href")
                    val mediaType = parser.getAttributeValue(null, "media-type").orEmpty()
                    val properties = parser.getAttributeValue(null, "properties").orEmpty()
                    val parsedProperties = parseProperties(properties)
                    if (id != null && href != null) {
                        manifest += ManifestItem(
                            id = id,
                            href = href,
                            mediaType = mediaType,
                            properties = parsedProperties
                        )
                    }
                    if (href != null && parsedProperties.contains(COVER_IMAGE_PROPERTY)) {
                        propertiesCoverHref = href
                    }
                }

                "reference" -> {
                    val type = parser.getAttributeValue(null, "type")
                    val href = parser.getAttributeValue(null, "href")
                    if (type.equals(COVER_REFERENCE_TYPE, ignoreCase = true) &&
                        !href.isNullOrBlank()
                    ) {
                        guideCoverHref = href
                    }
                }
            }
        }

        val coverHrefFromId = coverId?.let { id ->
            manifest.firstOrNull { it.id == id }?.href
        }

        return OpfMetadata(
            title = title?.ifBlank { fallbackTitle } ?: fallbackTitle,
            authors = authors.filter { it.isNotBlank() }.distinct(),
            primaryCoverHref = propertiesCoverHref ?: coverHrefFromId,
            guideCoverHref = guideCoverHref,
            manifestItems = manifest,
            series = series,
            seriesIndex = seriesIndex,
            publisher = publisher,
            year = year
        )
    }

    private fun buildCoverTargets(opfEntryName: String, metadata: OpfMetadata): CoverTargets {
        val opfDir = opfEntryName.substringBeforeLast('/', missingDelimiterValue = "")
        val manifestItems = metadata.manifestItems.map { item ->
            ResolvedManifestItem(
                id = item.id,
                href = item.href,
                resolvedPath = resolveZipPath(opfDir, item.href),
                mediaType = item.mediaType,
                properties = item.properties
            )
        }

        val manifestByPath = manifestItems.associateBy { it.resolvedPath.lowercase() }
        val explicitPaths = buildList {
            metadata.primaryCoverHref?.let { add(resolveZipPath(opfDir, it)) }
            metadata.guideCoverHref?.let { add(resolveZipPath(opfDir, it)) }
        }.distinct()

        val explicitImagePaths = mutableListOf<String>()
        val explicitPagePaths = mutableListOf<String>()
        explicitPaths.forEach { path ->
            val manifestItem = manifestByPath[path.lowercase()]
            when {
                manifestItem?.isCoverPage == true || path.isHtmlLikeName() ->
                    explicitPagePaths +=
                        path

                else -> explicitImagePaths += path
            }
        }

        val preferredFallbackImagePaths = manifestItems
            .filter { it.isImage && it.looksLikeCover() }
            .map { it.resolvedPath }
            .distinct()

        val fallbackImagePaths = (
            preferredFallbackImagePaths + manifestItems
                .filter { it.isImage }
                .map { it.resolvedPath }
            )
            .distinct()

        return CoverTargets(
            explicitImagePaths = explicitImagePaths,
            explicitPagePaths = explicitPagePaths,
            preferredFallbackImagePaths = preferredFallbackImagePaths,
            fallbackImagePaths = fallbackImagePaths
        )
    }

    private fun scanCoverAssets(input: InputStream, targets: CoverTargets): CoverScan =
        ZipInputStream(input).use { zip ->
            val explicitImagePaths = targets.explicitImagePaths.mapTo(mutableSetOf()) {
                it.lowercase()
            }
            val explicitPagePaths = targets.explicitPagePaths.mapTo(mutableSetOf()) {
                it.lowercase()
            }
            val preferredFallbackImagePaths =
                targets.preferredFallbackImagePaths.mapTo(mutableSetOf()) { it.lowercase() }
            val fallbackImagePaths = targets.fallbackImagePaths.mapTo(mutableSetOf()) {
                it.lowercase()
            }

            val explicitImages = linkedMapOf<String, ByteArray>()
            val explicitPages = linkedMapOf<String, ByteArray>()
            var fallbackImage: NamedBytes? = null

            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }

                val entryName = entry.name
                val lowerName = entryName.lowercase()
                when {
                    lowerName in explicitImagePaths -> {
                        explicitImages.putIfAbsent(
                            entryName,
                            zip.readCurrentEntry(METADATA_MAX_IMAGE_BYTES)
                        )
                    }

                    lowerName in explicitPagePaths -> {
                        explicitPages.putIfAbsent(entryName, zip.readCurrentEntry(MAX_TEXT_BYTES))
                    }

                    lowerName in preferredFallbackImagePaths &&
                        shouldReplaceFallbackImage(fallbackImage?.name, entryName) -> {
                        fallbackImage = NamedBytes(
                            name = entryName,
                            bytes = zip.readCurrentEntry(METADATA_MAX_IMAGE_BYTES)
                        )
                    }

                    (lowerName in fallbackImagePaths || lowerName.isImageName()) &&
                        shouldReplaceFallbackImage(fallbackImage?.name, entryName) -> {
                        fallbackImage = NamedBytes(
                            name = entryName,
                            bytes = zip.readCurrentEntry(METADATA_MAX_IMAGE_BYTES)
                        )
                    }

                    else -> zip.closeEntry()
                }
            }

            CoverScan(
                explicitImages = explicitImages,
                explicitPages = explicitPages,
                fallbackImage = fallbackImage
            )
        }

    private fun selectCoverBytes(
        uri: Uri,
        openStream: (Uri) -> InputStream,
        targets: CoverTargets,
        coverScan: CoverScan
    ): ByteArray? {
        targets.explicitImagePaths.forEach { path ->
            coverScan.explicitImages.findIgnoreCase(path)?.let { return it }
        }

        targets.explicitPagePaths.forEach { pagePath ->
            val pageBytes = coverScan.explicitPages.findIgnoreCase(pagePath) ?: return@forEach
            val imageHref = extractCoverImageHrefFromPage(pageBytes) ?: return@forEach
            val coverImagePath = resolveZipPath(
                baseDir = pagePath.substringBeforeLast('/', missingDelimiterValue = ""),
                href = imageHref
            )

            coverScan.explicitImages.findIgnoreCase(coverImagePath)?.let { return it }
            openStream(uri).use { input ->
                extractEntryBytes(input, coverImagePath, METADATA_MAX_IMAGE_BYTES)
            }?.let { return it }
        }

        return coverScan.fallbackImage?.bytes
    }

    private fun extractCoverImageHrefFromPage(bytes: ByteArray): String? {
        val parser = newMetadataXmlParser(ByteArrayInputStream(bytes))
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name.substringAfter(':')) {
                "img" -> parser.attributeValueByLocalName("src")?.let { return it }

                "image" -> parser.attributeValueByLocalName("href", XLINK_NAMESPACE)?.let {
                    return it
                }
            }
        }
        return null
    }

    private fun extractEntryBytes(input: InputStream, targetPath: String, limit: Int): ByteArray? =
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.equals(targetPath, ignoreCase = true)) {
                    return@use zip.readCurrentEntry(limit)
                }
                zip.closeEntry()
            }
            null
        }

    private fun parseProperties(properties: String): Set<String> = properties
        .splitToSequence(WHITESPACE_REGEX)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

    private fun resolveZipPath(baseDir: String, href: String): String {
        val decoded = runCatching {
            URLDecoder.decode(href.substringBefore('#'), Charsets.UTF_8.name())
        }.getOrElse {
            href.substringBefore('#')
        }
        val raw = if (baseDir.isBlank()) decoded else "$baseDir/$decoded"
        val parts = mutableListOf<String>()
        raw.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += part
            }
        }
        return parts.joinToString("/")
    }

    private data class OpfMetadata(
        val title: String,
        val authors: List<String>,
        val primaryCoverHref: String?,
        val guideCoverHref: String?,
        val manifestItems: List<ManifestItem>,
        val series: String? = null,
        val seriesIndex: String? = null,
        val publisher: String? = null,
        val year: String? = null
    )

    private data class EpubScan(
        val containerBytes: ByteArray?,
        val firstOpfEntryName: String?,
        val opfEntries: Map<String, ByteArray>
    )

    private data class SelectedOpf(val entryName: String, val bytes: ByteArray)

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: Set<String>
    )

    private data class ResolvedManifestItem(
        val id: String,
        val href: String,
        val resolvedPath: String,
        val mediaType: String,
        val properties: Set<String>
    ) {
        val isImage: Boolean
            get() = mediaType.startsWith("image/", ignoreCase = true)

        val isCoverPage: Boolean
            get() {
                val lowerPath = resolvedPath.lowercase()
                return mediaType.equals(XHTML_MEDIA_TYPE, ignoreCase = true) ||
                    lowerPath.endsWith(".xhtml") ||
                    lowerPath.endsWith(".html") ||
                    lowerPath.endsWith(".htm")
            }

        fun looksLikeCover(): Boolean {
            if (properties.contains(COVER_IMAGE_PROPERTY)) return true

            val idName = id.lowercase()
            val hrefName = href.lowercase().substringAfterLast('/')
            return idName.contains("cover") ||
                hrefName.contains("cover") ||
                hrefName.contains("front") ||
                hrefName.contains("folder") ||
                hrefName.contains("title")
        }
    }

    private data class CoverTargets(
        val explicitImagePaths: List<String>,
        val explicitPagePaths: List<String>,
        val preferredFallbackImagePaths: List<String>,
        val fallbackImagePaths: List<String>
    )

    private data class CoverScan(
        val explicitImages: Map<String, ByteArray>,
        val explicitPages: Map<String, ByteArray>,
        val fallbackImage: NamedBytes?
    )

    private data class NamedBytes(val name: String, val bytes: ByteArray)

    private fun Map<String, ByteArray>.findIgnoreCase(path: String): ByteArray? =
        entries.firstOrNull { (entryName, _) -> entryName.equals(path, ignoreCase = true) }?.value

    private fun MutableMap<String, ByteArray>.putIfAbsent(name: String, bytes: ByteArray) {
        if (keys.none { it.equals(name, ignoreCase = true) }) {
            put(name, bytes)
        }
    }

    private fun shouldReplaceFallbackImage(currentName: String?, candidateName: String): Boolean {
        if (currentName == null) return true

        val candidateIsPreferred = candidateName.hasPreferredFallbackImageName()
        val currentIsPreferred = currentName.hasPreferredFallbackImageName()
        return when {
            candidateIsPreferred != currentIsPreferred -> candidateIsPreferred
            else -> NaturalSort.compare(candidateName, currentName) < 0
        }
    }

    private fun String.hasPreferredFallbackImageName(): Boolean {
        val name = lowercase().substringAfterLast('/')
        return name.contains("cover") ||
            name.contains("front") ||
            name.contains("folder") ||
            name.contains("title")
    }

    private fun String.isHtmlLikeName(): Boolean {
        val lower = lowercase()
        return lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
    }

    private companion object {
        const val CONTAINER_ENTRY_NAME = "META-INF/container.xml"
        const val COVER_IMAGE_PROPERTY = "cover-image"
        const val COVER_REFERENCE_TYPE = "cover"
        const val XHTML_MEDIA_TYPE = "application/xhtml+xml"
        const val XLINK_NAMESPACE = "http://www.w3.org/1999/xlink"
        const val MAX_TEXT_BYTES = 32 * 1024 * 1024
        val WHITESPACE_REGEX = Regex("""\s+""")
    }
}

package com.cybercat.pocketbooksender.data.opds

import android.content.Context
import com.cybercat.pocketbooksender.data.database.dao.OpdsSourceDao
import com.cybercat.pocketbooksender.data.database.entity.OpdsSourceEntity
import com.cybercat.pocketbooksender.domain.bookExtension
import com.cybercat.pocketbooksender.util.ExpiringLruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class OpdsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: OpdsParser,
    private val httpClient: OpdsHttpClient,
    private val sourceDao: OpdsSourceDao
) {
    private val catalogCache = ExpiringLruCache<String, OpdsCatalog>(
        ttlMillis = CATALOG_CACHE_TTL_MILLIS,
        maxSize = CATALOG_CACHE_MAX_ENTRIES
    )

    fun clearCache() {
        catalogCache.clear()
    }

    suspend fun hasSavedCredentials(): Boolean = withContext(Dispatchers.IO) {
        sourceDao.getAllSources().any { entity ->
            entity.username != null || entity.password != null
        }
    }

    suspend fun logoutAll(): Boolean = withContext(Dispatchers.IO) {
        val allSources = sourceDao.getAllSources()
        var clearedAny = false
        allSources.forEach { entity ->
            if (entity.username != null || entity.password != null) {
                sourceDao.upsert(entity.copy(username = null, password = null))
                clearedAny = true
            }
        }
        if (clearedAny) {
            clearCache()
        }
        clearedAny
    }

    val sources: Flow<List<OpdsSource>> =
        sourceDao.observeSources().map { entities ->
            entities
                .filter(OpdsSourceEntity::enabled)
                .map { entity ->
                    OpdsSource(
                        id = entity.id,
                        title = entity.title,
                        url = entity.url,
                        username = entity.username,
                        password = entity.password
                    )
                }
                .distinctBy { source -> source.url.trimEnd('/').lowercase() }
        }

    suspend fun seedDefaultsIfNeeded() {
        sourceDao.deleteById(LEGACY_PROJECT_GUTENBERG_SOURCE_ID)
        if (sourceDao.count() > 0) return

        DEFAULT_SOURCES.forEach { source ->
            sourceDao.upsert(
                OpdsSourceEntity(
                    id = source.id,
                    title = source.title,
                    url = source.url,
                    enabled = true,
                    lastSyncedAt = null
                )
            )
        }
    }

    suspend fun addSource(
        title: String,
        url: String,
        username: String? = null,
        password: String? = null
    ) {
        val normalizedUrl = normalizeUrl(url)
        val sourceTitle = title.ifBlank {
            runCatching { URL(normalizedUrl).host.removePrefix("www.") }.getOrDefault("OPDS")
        }

        sourceDao.upsert(
            OpdsSourceEntity(
                id = UUID.nameUUIDFromBytes(normalizedUrl.toByteArray()).toString(),
                title = sourceTitle,
                url = normalizedUrl,
                enabled = true,
                lastSyncedAt = null,
                username = username,
                password = password
            )
        )
    }

    suspend fun removeSource(id: String) {
        sourceDao.deleteById(id)
    }

    suspend fun loadCatalog(url: String): OpdsCatalog = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(url)
        catalogCache.get(normalizedUrl)?.let { cachedCatalog ->
            return@withContext cachedCatalog
        }

        val connection = httpClient.openConnection(
            url = normalizedUrl,
            accept = OPDS_CATALOG_ACCEPT
        )
        try {
            val catalog = connection.inputStream.use { input ->
                parser.parse(input).resolvedAgainst(normalizedUrl)
            }
            catalogCache.put(normalizedUrl, catalog)
            catalog
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadPublication(
        baseUrl: String,
        entry: OpdsEntry,
        acquisition: OpdsAcquisition,
        onProgress: (OpdsDownloadProgress) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val resolvedUrl = OpdsUrlResolver.resolveUrl(baseUrl, acquisition.href)
        val connection = httpClient.openConnection(
            url = resolvedUrl,
            accept = acquisition.type ?: "*/*"
        )
        try {
            val fileName = chooseFileName(
                connection = connection,
                url = resolvedUrl,
                entry = entry,
                acquisition = acquisition
            )
            val outputDir = File(context.cacheDir, "opds").apply { mkdirs() }
            val outputFile = uniqueFile(outputDir, fileName)
            val tempFile = uniqueFile(outputDir, "${outputFile.name}.download")

            try {
                connection.runDisconnectingOnCancellation { ensureActive ->
                    val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
                    val progressReporter = OpdsDownloadProgressReporter(
                        totalBytes = totalBytes,
                        currentItemTitle = entry.title.ifBlank { acquisition.title.orEmpty() },
                        currentItemAuthors = entry.authors,
                        onProgress = onProgress
                    )
                    var bytesRead = 0L
                    progressReporter.report(bytesRead, force = true)

                    connection.inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_DOWNLOAD_BUFFER_SIZE)
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                ensureActive()
                                output.write(buffer, 0, read)
                                bytesRead += read
                                progressReporter.report(bytesRead)
                            }
                        }
                    }

                    progressReporter.report(bytesRead, force = true)
                    if (!tempFile.renameTo(outputFile)) {
                        tempFile.copyTo(outputFile, overwrite = false)
                        tempFile.delete()
                    }
                    outputFile
                }
            } catch (error: Throwable) {
                tempFile.delete()
                outputFile.delete()
                if (error !is CancellationException) {
                    try {
                        currentCoroutineContext().ensureActive()
                    } catch (cancellation: CancellationException) {
                        throw CancellationException("OPDS download canceled").also { canceled ->
                            canceled.initCause(error)
                        }
                    }
                }
                throw error
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun OpdsCatalog.resolvedAgainst(baseUrl: String): OpdsCatalog = copy(
        links = links.map { it.resolvedAgainst(baseUrl) },
        entries = entries.map { entry ->
            entry.copy(
                coverHref = entry.coverHref?.let { OpdsUrlResolver.resolveUrl(baseUrl, it) },
                navigation = entry.navigation.map { it.resolvedAgainst(baseUrl) },
                acquisitions = entry.acquisitions.map { acquisition ->
                    acquisition.copy(
                        href = OpdsUrlResolver.resolveUrl(baseUrl, acquisition.href)
                    )
                }
            )
        }
    )

    private fun OpdsLink.resolvedAgainst(baseUrl: String): OpdsLink =
        copy(href = OpdsUrlResolver.resolveUrl(baseUrl, href))

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) throw IllegalArgumentException("OPDS URL is empty")
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun chooseFileName(
        connection: HttpURLConnection,
        url: String,
        entry: OpdsEntry,
        acquisition: OpdsAcquisition
    ): String {
        val dispositionName = connection.getHeaderField("Content-Disposition")
            ?.let(::fileNameFromDisposition)
        val urlName = runCatching {
            URLDecoder.decode(URL(url).path.substringAfterLast('/'), Charsets.UTF_8.name())
        }.getOrNull()?.takeIf { it.isNotBlank() }

        val baseName = dispositionName
            ?: urlName
            ?: entry.title.ifBlank { acquisition.title.orEmpty() }
            ?: "book"

        val sanitized = baseName.sanitizeFileName().ifBlank { "book" }
        return if (sanitized.bookExtension().isNotBlank()) {
            sanitized
        } else {
            val extension = acquisition.extensionFromType()
            if (extension.isBlank()) sanitized else "$sanitized.$extension"
        }
    }

    private fun fileNameFromDisposition(disposition: String): String? {
        val utfName = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
            .find(disposition)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }

        if (!utfName.isNullOrBlank()) return utfName

        return Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
            .find(disposition)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun uniqueFile(directory: File, desiredName: String): File {
        val name = desiredName.sanitizeFileName().ifBlank { "book" }
        var candidate = File(directory, name)
        if (!candidate.exists()) return candidate

        val extension = name.bookExtension()
        val stem = if (extension.isBlank()) name else name.dropLast(extension.length + 1)
        var index = 2
        while (candidate.exists()) {
            val nextName = if (extension.isBlank()) {
                "$stem-$index"
            } else {
                "$stem-$index.$extension"
            }
            candidate = File(directory, nextName)
            index += 1
        }
        return candidate
    }

    private fun String.sanitizeFileName(): String = replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), "_")
        .trim('_', '.', ' ')

    private fun OpdsAcquisition.extensionFromType(): String {
        val mime = type.orEmpty().lowercase()
        return when {
            mime.contains("epub") -> "epub"
            mime.contains("fb2") -> "fb2"
            mime.contains("mobipocket") || mime.contains("mobi") -> "mobi"
            else -> ""
        }
    }

    private class OpdsDownloadProgressReporter(
        private val totalBytes: Long?,
        private val currentItemTitle: String?,
        private val currentItemAuthors: List<String>,
        private val onProgress: (OpdsDownloadProgress) -> Unit
    ) {
        private var lastReportedBytes = -1L
        private var lastReportedPercent = -1
        private var lastReportedAtMillis = 0L

        fun report(bytesRead: Long, force: Boolean = false) {
            val safeBytesRead = bytesRead.coerceAtLeast(0L)
            val percent = totalBytes?.let { total ->
                ((safeBytesRead.coerceAtMost(total) * 100L) / total).toInt()
            } ?: -1
            val now = System.currentTimeMillis()
            val shouldReport = force ||
                lastReportedBytes < 0L ||
                safeBytesRead - lastReportedBytes >= PROGRESS_REPORT_BYTES_INTERVAL ||
                percent != lastReportedPercent ||
                now - lastReportedAtMillis >= PROGRESS_REPORT_MIN_INTERVAL_MILLIS

            if (!shouldReport) return

            lastReportedBytes = safeBytesRead
            lastReportedPercent = percent
            lastReportedAtMillis = now
            onProgress(
                OpdsDownloadProgress(
                    bytesRead = safeBytesRead,
                    totalBytes = totalBytes,
                    currentItemTitle = currentItemTitle,
                    currentItemAuthors = currentItemAuthors
                )
            )
        }
    }

    private companion object {
        const val CATALOG_CACHE_TTL_MILLIS = 5 * 60 * 1000L
        const val CATALOG_CACHE_MAX_ENTRIES = 32
        const val DEFAULT_DOWNLOAD_BUFFER_SIZE = 64 * 1024
        const val PROGRESS_REPORT_BYTES_INTERVAL = 256 * 1024L
        const val PROGRESS_REPORT_MIN_INTERVAL_MILLIS = 250L
        const val LEGACY_PROJECT_GUTENBERG_SOURCE_ID = "project-gutenberg"
        const val OPDS_CATALOG_ACCEPT =
            "application/atom+xml;profile=opds-catalog, application/atom+xml, application/xml, text/xml, */*"

        val DEFAULT_SOURCES = listOf(
            OpdsSource(
                id = "flibusta-test",
                title = "Flibusta",
                url = "https://flub.flibusta.is/opds"
            )
        )
    }
}

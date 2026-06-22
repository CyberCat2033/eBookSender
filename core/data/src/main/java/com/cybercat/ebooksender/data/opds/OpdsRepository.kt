package com.cybercat.ebooksender.data.opds

import android.content.Context
import com.cybercat.ebooksender.data.database.dao.OpdsSourceDao
import com.cybercat.ebooksender.data.database.entity.OpdsSourceEntity
import com.cybercat.ebooksender.domain.bookExtension
import com.cybercat.ebooksender.util.ExpiringLruCache
import com.cybercat.ebooksender.util.UrlHostMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
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
open class OpdsRepository(
    @ApplicationContext private val _context: Context?,
    private val _parser: OpdsParser?,
    private val _httpClient: OpdsHttpClient?,
    private val _sourceDao: OpdsSourceDao?,
    private val _credentialsStore: OpdsSecureCredentialsStore?,
    @Suppress("UNUSED_PARAMETER") dummy: Boolean
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        parser: OpdsParser,
        httpClient: OpdsHttpClient,
        sourceDao: OpdsSourceDao,
        credentialsStore: OpdsSecureCredentialsStore
    ) : this(context, parser, httpClient, sourceDao, credentialsStore, true)

    private val context: Context get() = _context!!
    private val parser: OpdsParser get() = _parser!!
    private val httpClient: OpdsHttpClient get() = _httpClient!!
    private val sourceDao: OpdsSourceDao get() = _sourceDao!!
    private val credentialsStore: OpdsSecureCredentialsStore get() = _credentialsStore!!

    private val catalogCache = ExpiringLruCache<String, OpdsCatalog>(
        ttlMillis = CATALOG_CACHE_TTL_MILLIS,
        maxSize = CATALOG_CACHE_MAX_ENTRIES
    )

    fun clearCache() {
        catalogCache.clear()
    }

    suspend fun hasSavedCredentials(): Boolean = withContext(Dispatchers.IO) {
        credentialsStore.hasAny()
    }

    suspend fun logoutAll(): Boolean = withContext(Dispatchers.IO) {
        val clearedAny = credentialsStore.clearAll()
        if (clearedAny) {
            clearCache()
        }
        clearedAny
    }

    open val sources: Flow<List<OpdsSource>> =
        if (_sourceDao != null && _credentialsStore != null) {
            sourceDao.observeSources().map { entities ->
                entities
                    .filter(OpdsSourceEntity::enabled)
                    .map { entity ->
                        val credentials = credentialsStore.read(entity.id)
                        OpdsSource(
                            id = entity.id,
                            title = entity.title,
                            url = entity.url,
                            username = credentials?.username,
                            password = credentials?.password
                        )
                    }
                    .distinctBy { source -> source.url.trimEnd('/').lowercase() }
            }
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
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
        val normalizedUrl = normalizeOpdsUrl(url)
        val sourceTitle = title.ifBlank {
            UrlHostMatcher.displayHostWithoutWww(normalizedUrl) ?: "OPDS"
        }
        val sourceId = UUID.nameUUIDFromBytes(normalizedUrl.toByteArray()).toString()

        sourceDao.upsert(
            OpdsSourceEntity(
                id = sourceId,
                title = sourceTitle,
                url = normalizedUrl,
                enabled = true,
                lastSyncedAt = null
            )
        )
        credentialsStore.save(
            sourceId = sourceId,
            username = username,
            password = password
        )
    }

    suspend fun removeSource(id: String) {
        if (id in DEFAULT_SOURCE_IDS) {
            sourceDao.setEnabled(id, false)
        } else {
            sourceDao.deleteById(id)
        }
        credentialsStore.remove(id)
        clearCache()
    }

    suspend fun loadCatalog(url: String): OpdsCatalog = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeOpdsUrl(url)
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
            val fileName = chooseOpdsDownloadFileName(
                connection = connection,
                url = resolvedUrl,
                entry = entry,
                acquisition = acquisition
            )
            val outputDir = File(context.cacheDir, "opds").apply { mkdirs() }
            val outputFile = uniqueOpdsDownloadFile(outputDir, fileName)
            val tempFile = uniqueOpdsDownloadFile(outputDir, "${outputFile.name}.download")

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
        val DEFAULT_SOURCE_IDS = DEFAULT_SOURCES.mapTo(mutableSetOf()) { it.id }
    }
}

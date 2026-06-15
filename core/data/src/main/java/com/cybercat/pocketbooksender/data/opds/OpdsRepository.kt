package com.cybercat.pocketbooksender.data.opds

import android.content.Context
import com.cybercat.pocketbooksender.data.database.dao.OpdsSourceDao
import com.cybercat.pocketbooksender.data.database.entity.OpdsSourceEntity
import com.cybercat.pocketbooksender.domain.bookExtension
import com.cybercat.pocketbooksender.util.AppConstants
import com.cybercat.pocketbooksender.util.TimedCacheEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class OpdsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: OpdsParser,
    private val sourceDao: OpdsSourceDao,
) {
    private val catalogCache = ConcurrentHashMap<String, TimedCacheEntry<OpdsCatalog>>()

    fun clearCache() {
        catalogCache.clear()
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
                        password = entity.password,
                    )
                }
                .distinctBy { source -> source.url.trimEnd('/').lowercase() }
        }

    suspend fun seedDefaultsIfNeeded() {
        sourceDao.deleteById(LegacyProjectGutenbergSourceId)
        if (sourceDao.count() > 0) return

        DefaultSources.forEach { source ->
            sourceDao.upsert(
                OpdsSourceEntity(
                    id = source.id,
                    title = source.title,
                    url = source.url,
                    enabled = true,
                    lastSyncedAt = null,
                ),
            )
        }
    }

    suspend fun addSource(title: String, url: String, username: String? = null, password: String? = null) {
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
                password = password,
            ),
        )
    }

    suspend fun removeSource(id: String) {
        sourceDao.deleteById(id)
    }

    suspend fun loadCatalog(url: String): OpdsCatalog = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(url)
        catalogCache[normalizedUrl]?.takeIf { entry -> entry.isFresh(CatalogCacheTtlMillis) }?.let { entry ->
            return@withContext entry.value
        }

        val connection = openConnection(
            url = normalizedUrl,
            accept = "application/atom+xml;profile=opds-catalog, application/atom+xml, application/xml, text/xml, */*",
        )
        try {
            val catalog = connection.inputStream.use { input ->
                parser.parse(input).resolvedAgainst(normalizedUrl)
            }
            catalogCache[normalizedUrl] = TimedCacheEntry(catalog)
            catalog
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadPublication(
        baseUrl: String,
        entry: OpdsEntry,
        acquisition: OpdsAcquisition,
    ): File = withContext(Dispatchers.IO) {
        val resolvedUrl = resolveUrl(baseUrl, acquisition.href)
        val connection = openConnection(
            url = resolvedUrl,
            accept = acquisition.type ?: "*/*",
        )
        try {
            val fileName = chooseFileName(
                connection = connection,
                url = resolvedUrl,
                entry = entry,
                acquisition = acquisition,
            )
            val outputDir = File(context.cacheDir, "opds").apply { mkdirs() }
            val outputFile = uniqueFile(outputDir, fileName)

            connection.inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            outputFile
        } finally {
            connection.disconnect()
        }
    }

    suspend fun buildSearchUrl(
        baseUrl: String,
        searchLink: OpdsLink,
        query: String,
    ): String = withContext(Dispatchers.IO) {
        val resolvedLink = resolveTemplateUrl(baseUrl, searchLink.href)
        val template = when {
            searchLink.href.contains(SearchTermsPlaceholder) -> resolvedLink
            searchLink.type.orEmpty().contains("opensearchdescription", ignoreCase = true) -> {
                loadOpenSearchTemplate(resolvedLink)
            }
            searchLink.type.orEmpty().contains("application/atom+xml", ignoreCase = true) -> {
                resolvedLink
            }
            else -> {
                resolvedLink
            }
        }

        expandSearchTemplate(template, query)
    }

    suspend fun buildSearchUrls(
        baseUrl: String,
        searchLink: OpdsLink,
        query: String,
    ): List<String> = withContext(Dispatchers.IO) {
        val bookSearchUrl = buildSearchUrl(baseUrl, searchLink, query)
        (listOf(bookSearchUrl) + bookSearchUrl.flibustaAuthorSearchUrls(query)).distinct()
    }

    fun resolveUrl(baseUrl: String, href: String): String {
        return runCatching {
            URI(baseUrl).resolve(href).toString()
        }.getOrElse {
            href
        }
    }

    private fun OpdsCatalog.resolvedAgainst(baseUrl: String): OpdsCatalog =
        copy(
            links = links.map { it.resolvedAgainst(baseUrl) },
            entries = entries.map { entry ->
                entry.copy(
                    coverHref = entry.coverHref?.let { resolveUrl(baseUrl, it) },
                    navigation = entry.navigation.map { it.resolvedAgainst(baseUrl) },
                    acquisitions = entry.acquisitions.map { acquisition ->
                        acquisition.copy(href = resolveUrl(baseUrl, acquisition.href))
                    },
                )
            },
        )

    private fun OpdsLink.resolvedAgainst(baseUrl: String): OpdsLink =
        copy(href = resolveUrl(baseUrl, href))

    private suspend fun getCredentialsForUrl(urlStr: String): Pair<String, String>? {
        val requestHost = runCatching { URL(urlStr).host.lowercase() }.getOrNull() ?: return null
        val sourcesList = sourceDao.getAllSources()
        for (source in sourcesList) {
            val sourceHost = runCatching { URL(source.url).host.lowercase() }.getOrNull()
            if (sourceHost != null && sourceHost == requestHost) {
                val username = source.username
                val password = source.password
                if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                    return Pair(username, password)
                }
            }
        }
        return null
    }

    private fun getCredentialsFromUrl(urlStr: String): Pair<String, String>? {
        return runCatching {
            val uri = URI(urlStr)
            val userInfo = uri.userInfo
            if (!userInfo.isNullOrBlank()) {
                val parts = userInfo.split(':', limit = 2)
                if (parts.size == 2) {
                    Pair(parts[0], parts[1])
                } else null
            } else null
        }.getOrNull()
    }

    private suspend fun openConnection(
        url: String,
        accept: String,
        redirectsLeft: Int = MaxRedirects,
    ): HttpURLConnection {
        val credentials = getCredentialsFromUrl(url) ?: getCredentialsForUrl(url)
        val cleanedUrl = if (url.contains("@")) {
            runCatching {
                val uri = URI(url)
                val cleanUri = URI(uri.scheme, null, uri.host, uri.port, uri.path, uri.query, uri.fragment)
                cleanUri.toString()
            }.getOrDefault(url)
        } else {
            url
        }

        val connection = (URL(cleanedUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = ConnectTimeoutMillis
            readTimeout = ReadTimeoutMillis
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", AppConstants.UserAgent)
            if (credentials != null) {
                val authString = "${credentials.first}:${credentials.second}"
                val authHeaderValue = "Basic " + android.util.Base64.encodeToString(
                    authString.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )
                setRequestProperty("Authorization", authHeaderValue)
            }
        }

        val code = connection.responseCode
        if (code in 300..399 && redirectsLeft > 0) {
            val location = connection.getHeaderField("Location")
            connection.disconnect()

            if (location.isNullOrBlank()) {
                throw IOException("HTTP redirect without Location")
            }

            return openConnection(
                url = resolveUrl(url, location),
                accept = accept,
                redirectsLeft = redirectsLeft - 1,
            )
        }

        if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
            connection.disconnect()
            throw OpdsAuthenticationRequiredException(url)
        }
        if (code !in 200..299) {
            val message = connection.responseMessage?.takeIf { it.isNotBlank() }
            connection.disconnect()
            throw IOException("HTTP $code${message?.let { ": $it" }.orEmpty()}")
        }

        return connection
    }

    private suspend fun loadOpenSearchTemplate(url: String): String {
        val connection = openConnection(
            url = url,
            accept = "application/opensearchdescription+xml, application/xml, text/xml, */*",
        )
        try {
            val template = connection.inputStream.use { input ->
                parser.parseOpenSearch(input).bestTemplate
            }

            if (template.isNullOrBlank()) {
                throw IOException("OpenSearch template was not found")
            }

            return resolveTemplateUrl(url, template)
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveTemplateUrl(baseUrl: String, href: String): String {
        val escapedHref = href
            .replace("{", "%7B")
            .replace("}", "%7D")
        return resolveUrl(baseUrl, escapedHref)
            .replace("%7B", "{")
            .replace("%7D", "}")
    }

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
        acquisition: OpdsAcquisition,
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

    private fun String.sanitizeFileName(): String =
        replace(Regex("[\\\\/:*?\"<>|]"), "_")
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

    private companion object {
        const val ConnectTimeoutMillis = 15_000
        const val ReadTimeoutMillis = 45_000
        const val CatalogCacheTtlMillis = 5 * 60 * 1000L
        const val MaxRedirects = 5
        const val SearchTermsPlaceholder = "{searchTerms"
        const val LegacyProjectGutenbergSourceId = "project-gutenberg"

        val DefaultSources = listOf(
            OpdsSource(
                id = "flibusta-test",
                title = "Flibusta",
                url = "https://flub.flibusta.is/opds",
            ),
        )
    }
}

private fun expandSearchTemplate(
    template: String,
    query: String,
): String {
    val encodedQuery = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
    return template
        .replace(Regex("\\{searchTerms\\??\\}"), encodedQuery)
        .replace(Regex("[?&][^?&=]+=\\{[^}]+\\}"), "")
        .replace(Regex("\\{[^}]+\\}"), "")
        .replace("?&", "?")
        .trimEnd('?', '&')
}

private fun String.flibustaAuthorSearchUrls(query: String): List<String> {
    val uri = runCatching { URI(this) }.getOrNull() ?: return emptyList()
    val host = uri.host.orEmpty().lowercase()
    if ("flibusta" !in host && "flub" !in host) return emptyList()

    val normalizedQuery = query.cleanAuthorSearchQuery()
    if (normalizedQuery.isBlank()) return emptyList()

    val authorPrefix = normalizedQuery
        .split(' ')
        .lastOrNull { token -> token.length >= 2 }
        ?: return emptyList()

    val baseUrl = "${uri.scheme}://${uri.rawAuthority}"
    return listOf("$baseUrl/opds/authorsindex/${authorPrefix.urlPathEncode()}")
}

private fun String.cleanAuthorSearchQuery(): String =
    trim()
        .replace(Regex("[^\\p{L}\\p{N}\\s-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()

private fun String.urlPathEncode(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name())
        .replace("+", "%20")

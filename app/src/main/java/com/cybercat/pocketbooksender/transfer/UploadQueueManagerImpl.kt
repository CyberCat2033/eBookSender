package com.cybercat.pocketbooksender.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.cybercat.pocketbooksender.domain.FileClassifier
import com.cybercat.pocketbooksender.domain.PathPlanner
import com.cybercat.pocketbooksender.domain.bookExtension
import com.cybercat.pocketbooksender.domain.bookTitleWithoutExtension
import com.cybercat.pocketbooksender.metadata.MetadataExtractor
import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.UploadItem
import com.cybercat.pocketbooksender.model.UploadStatus
import com.cybercat.pocketbooksender.data.settings.SettingsRepository
import com.cybercat.pocketbooksender.data.transfer.UploadQueueManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class UploadQueueManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataExtractor: MetadataExtractor,
    private val classifier: FileClassifier,
    private val pathPlanner: PathPlanner,
    private val settingsRepository: SettingsRepository,
) : UploadQueueManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _queue = MutableStateFlow<List<UploadItem>>(emptyList())
    override val queue: StateFlow<List<UploadItem>> = _queue.asStateFlow()

    private var activeSettings = AppSettings()
    private var queueRestored = false

    init {
        scope.launch {
            val restoredQueue = withContext(Dispatchers.IO) {
                restorePersistedQueue()
            }
            _queue.update { current ->
                (restoredQueue + current).deduplicateQueue()
            }
            queueRestored = true
            withContext(Dispatchers.IO) {
                persistQueue(_queue.value)
            }
            _queue.value.forEach { item ->
                if (item.preview == null && item.status != UploadStatus.Uploaded) {
                    launch {
                        loadMetadata(item)
                    }
                }
            }
        }

        _queue
            .drop(1)
            .onEach { items ->
                if (queueRestored) {
                    withContext(Dispatchers.IO) {
                        persistQueue(items)
                        cleanupCoverCacheFiles(items)
                    }
                }
            }
            .launchIn(scope)

        // Reactively replan queue items when settings change
        settingsRepository.settings
            .onEach { settings ->
                val prev = activeSettings
                activeSettings = settings
                if (prev != settings) {
                    _queue.update { current ->
                        current.map { replan(it, settings) }.deduplicateQueue()
                    }
                }
            }
            .launchIn(scope)
    }

    override fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return

        val settings = activeSettings
        val existing = _queue.value.queueIdentityKeys()
        val skippedFiles = mutableListOf<String>()

        val newItems = uris
            .distinctBy { it.toString() }
            .mapNotNull { uri ->
                val uriString = uri.toString()
                if (uriString in existing) {
                    null
                } else {
                    val displayName = resolveDisplayName(uri)
                        ?: uri.lastPathSegment
                        ?: "Book-${UUID.randomUUID()}"
                    val extension = displayName.bookExtension().lowercase().trim()

                    val isSupported = extension in com.cybercat.pocketbooksender.domain.AllSupportedExtensions ||
                            (extension.endsWith(".zip") && extension.removeSuffix(".zip") in com.cybercat.pocketbooksender.domain.AllSupportedExtensions)
                    val fileSize = resolveFileSize(uri)
                    val isTooBig = fileSize > 500L * 1024L * 1024L // 500 MB limit

                    if (!isSupported || isTooBig) {
                        val reason = when {
                            !isSupported -> "unsupported format"
                            else -> "too large (>500MB)"
                        }
                        skippedFiles.add("$displayName ($reason)")
                        null
                    } else {
                        persistReadPermission(uri)
                        createUploadItem(uri, displayName, settings)
                    }
                }
            }

        if (skippedFiles.isNotEmpty()) {
            val message = if (skippedFiles.size == 1) {
                "Skipped: ${skippedFiles.first()}"
            } else {
                "Skipped ${skippedFiles.size} files (unsupported format or >500MB)"
            }
            scope.launch(Dispatchers.Main) {
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
        }

        if (newItems.isEmpty()) return

        _queue.update { current ->
            (current + newItems).deduplicateQueue()
        }

        newItems.forEach { item ->
            scope.launch {
                loadMetadata(item)
            }
        }
    }

    override fun addPreparedItems(items: List<UploadItem>) {
        if (items.isEmpty()) return

        val existing = _queue.value.queueIdentityKeys()
        val newItems = items
            .filterNot { item -> item.sourceUri in existing }
            .map { item ->
                val prepared = if (item.status == UploadStatus.Preparing) {
                    item.copy(progress = 0f)
                } else {
                    item.copy(status = UploadStatus.Preparing, progress = 0f)
                }
                replan(prepared, activeSettings)
            }

        if (newItems.isEmpty()) return

        _queue.update { current ->
            (current + newItems).deduplicateQueue()
        }

        newItems.forEach { item ->
            scope.launch {
                loadMetadata(item)
            }
        }
    }

    override fun removeItem(id: String) {
        _queue.update { current ->
            current.filterNot { it.id == id }
        }
    }

    override fun clearQueue() {
        _queue.value = emptyList()
    }

    override fun updateCategory(id: String, category: BookCategory) {
        _queue.update { current ->
            val settings = activeSettings
            val updatedList = current.map { item ->
                if (item.id != id) {
                    item
                } else if (item.extension == "cbr" || item.extension == "cbz") {
                    replan(item.copy(category = BookCategory.Manga), settings)
                } else {
                    val updated = when (category) {
                        BookCategory.Documents -> item.copy(
                            category = BookCategory.Documents,
                            documentsTag = item.documentsTag ?: settings.defaultDocumentsTag,
                        )
                        BookCategory.Books -> item.copy(category = BookCategory.Books)
                        BookCategory.Manga -> item.copy(
                            category = BookCategory.Manga,
                            mangaSeries = item.mangaSeries ?: settings.defaultMangaSeries,
                            mangaVolume = item.mangaVolume ?: item.title,
                        )
                    }
                    replan(updated, settings)
                }
            }
            updatedList.deduplicateQueue()
        }
    }

    override fun updateDocumentsTag(id: String, tag: String) {
        _queue.update { current ->
            val trimmedTag = tag.trim()
            val updated = current.map { item ->
                if (item.id == id) {
                    replan(item.copy(documentsTag = trimmedTag), activeSettings)
                } else {
                    item
                }
            }
            updated.deduplicateQueue()
        }
    }

    override fun updateMangaSeries(id: String, series: String) {
        _queue.update { current ->
            val trimmedSeries = series.trim()
            val updated = current.map { item ->
                if (item.id == id) {
                    replan(item.copy(mangaSeries = trimmedSeries), activeSettings)
                } else {
                    item
                }
            }
            updated.deduplicateQueue()
        }
    }

    override fun updateQueuedMangaSeries(oldSeries: String?, series: String) {
        val trimmedSeries = series.trim()
        if (trimmedSeries.isBlank()) return
        _queue.update { current ->
            val updated = current.map { item ->
                if (item.category == BookCategory.Manga &&
                    item.status != UploadStatus.Uploaded &&
                    (oldSeries == null || item.mangaSeries?.equals(oldSeries, ignoreCase = true) == true)
                ) {
                    replan(item.copy(mangaSeries = trimmedSeries), activeSettings)
                } else {
                    item
                }
            }
            updated.deduplicateQueue()
        }
    }

    override fun updateQueue(updateBlock: (List<UploadItem>) -> List<UploadItem>) {
        _queue.update { current ->
            updateBlock(current).deduplicateQueue()
        }
    }

    private fun restorePersistedQueue(): List<UploadItem> {
        val file = queueStoreFile()
        if (!file.isFile) return emptyList()

        return try {
            val payload = JSONArray(file.readText())
            buildList {
                for (index in 0 until payload.length()) {
                    val item = payload.optJSONObject(index)?.toUploadItemOrNull() ?: continue
                    val canReadSource = canReadSource(Uri.parse(item.sourceUri))
                    val restoredStatus = item.status.restoredAfterProcessStart(canReadSource)
                    if (restoredStatus == UploadStatus.Uploaded) continue
                    
                    val previewBitmap = if (restoredStatus != UploadStatus.Uploaded) {
                        val cachedCoverFile = getCoverCacheFile(item.id)
                        if (cachedCoverFile.isFile) {
                            runCatching {
                                BitmapFactory.decodeFile(cachedCoverFile.absolutePath)
                            }.getOrNull()
                        } else null
                    } else null

                    add(
                        replan(
                            item.copy(
                                preview = previewBitmap,
                                status = restoredStatus,
                                progress = if (restoredStatus == UploadStatus.Uploaded) 1f else 0f,
                            ),
                            activeSettings,
                        ),
                    )
                }
            }.deduplicateQueue()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistQueue(items: List<UploadItem>) {
        val file = queueStoreFile()
        val payload = JSONArray()
        items.filter { it.status != UploadStatus.Uploaded }.forEach { item ->
            payload.put(item.toJson())
        }

        try {
            file.parentFile?.mkdirs()
            val tempFile = File(file.parentFile, "${file.name}.tmp")
            tempFile.writeText(payload.toString())
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        } catch (_: IOException) {
            // Queue persistence is best effort; in-memory queue remains authoritative.
        }
    }

    private fun createUploadItem(uri: Uri, displayName: String, settings: AppSettings): UploadItem {
        val extension = displayName.bookExtension().ifBlank { "bin" }
        val title = displayName.bookTitleWithoutExtension()
        val category = classifier.classify(displayName)
        
        var mangaSeries: String? = null
        var mangaVolume: String? = null
        
        if (category == BookCategory.Manga) {
            val parsed = parseMangaFilename(displayName)
            mangaSeries = parsed.first ?: settings.defaultMangaSeries
            mangaVolume = parsed.second ?: title
        }

        val preliminary = UploadItem(
            id = UUID.randomUUID().toString(),
            sourceUri = uri.toString(),
            originalName = displayName,
            extension = extension,
            category = category,
            title = title,
            author = if (category == BookCategory.Books) "Unknown Author" else null,
            documentsTag = if (category == BookCategory.Documents) settings.defaultDocumentsTag else null,
            mangaSeries = mangaSeries,
            mangaVolume = mangaVolume,
            plannedPath = "",
            status = UploadStatus.Preparing,
        )

        return replan(preliminary, settings)
    }

    private fun parseMangaFilename(displayName: String): Pair<String?, String?> {
        val title = displayName.bookTitleWithoutExtension().trim()
        val patterns = listOf(
            Regex("""^(.*?)\s+-\s+(.+)$"""), // "Naruto - 01", "Naruto - Chapter 1"
            Regex("""^(.*?)\s*_\s*(\d+.*)$"""), // "Naruto_01", "Naruto_ch1"
            Regex("""^(.*?)\s+([vV]?\d+.*)$"""), // "Naruto 01", "Naruto v01", "Naruto ch01"
        )
        for (pattern in patterns) {
            val match = pattern.matchEntire(title)
            if (match != null) {
                val series = match.groupValues[1].trim().replace('_', ' ')
                val volume = match.groupValues[2].trim()
                if (series.isNotBlank() && volume.isNotBlank()) {
                    return Pair(series, volume)
                }
            }
        }
        return Pair(null, null)
    }

    private suspend fun loadMetadata(item: UploadItem) {
        val metadata = metadataExtractor.extract(item.sourceUri, item.originalName)
        val preview = metadata.preview

        if (preview != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = getCoverCacheFile(item.id)
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { out ->
                        preview.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    }
                }
            }
        }

        _queue.update { current ->
            val updatedList = current.map { currentItem ->
                if (currentItem.id != item.id) {
                    currentItem
                } else if (currentItem.status == UploadStatus.Preparing) {
                    val title = metadata.title.ifBlank { currentItem.title }
                    val author = metadata.authors
                        .joinToString(", ")
                        .ifBlank { currentItem.author.orEmpty() }
                        .ifBlank { null }
                    val updated = currentItem.copy(
                        title = title,
                        author = if (currentItem.category == BookCategory.Books) author else currentItem.author,
                        mangaSeries = if (currentItem.category == BookCategory.Manga) {
                            metadata.series ?: currentItem.mangaSeries
                        } else {
                            currentItem.mangaSeries
                        },
                        year = metadata.year,
                        language = metadata.language,
                        series = metadata.series,
                        seriesIndex = metadata.seriesIndex,
                        publisher = metadata.publisher,
                        coverUri = metadata.coverUri,
                        preview = preview,
                        status = UploadStatus.Pending,
                    )
                    replan(updated, activeSettings)
                } else {
                    currentItem.copy(
                        preview = preview ?: currentItem.preview,
                        coverUri = metadata.coverUri ?: currentItem.coverUri
                    )
                }
            }
            updatedList.deduplicateQueue()
        }
    }

    private fun replan(item: UploadItem, settings: AppSettings): UploadItem =
        item.copy(plannedPath = pathPlanner.plan(item, settings))

    private fun List<UploadItem>.deduplicateQueue(): List<UploadItem> {
        val seenIds = mutableSetOf<String>()
        val seenSources = mutableSetOf<String>()
        val seenPaths = mutableSetOf<String>()

        return filter { item ->
            val idIsUnique = item.id.isBlank() || seenIds.add(item.id)
            val sourceIsUnique = item.sourceUri.isBlank() || seenSources.add(item.sourceUri)
            val pathIsUnique = item.plannedPath.isBlank() || seenPaths.add(item.plannedPath)
            idIsUnique && sourceIsUnique && pathIsUnique
        }
    }

    private fun List<UploadItem>.queueIdentityKeys(): Set<String> =
        flatMap { item ->
            listOfNotNull(
                item.sourceUri.takeIf { it.isNotBlank() },
                item.plannedPath.takeIf { it.isNotBlank() }
            )
        }.toSet()

    private fun resolveDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }

    private fun resolveFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun canReadSource(uri: Uri): Boolean {
        return when (uri.scheme?.lowercase()) {
            null, "file" -> {
                val path = uri.path.orEmpty()
                path.isNotBlank() && File(path).let { file -> file.isFile && file.canRead() }
            }
            "content" -> runCatching {
                context.contentResolver.openInputStream(uri)?.use { } ?: error("Cannot open source")
            }.isSuccess
            else -> runCatching {
                context.contentResolver.openInputStream(uri)?.use { } ?: error("Cannot open source")
            }.isSuccess
        }
    }

    private fun queueStoreFile(): File =
        File(context.filesDir, QueueStoreFileName)

    private fun UploadStatus.restoredAfterProcessStart(canReadSource: Boolean): UploadStatus =
        when {
            !canReadSource -> UploadStatus.Failed
            this == UploadStatus.Uploaded -> UploadStatus.Uploaded
            this == UploadStatus.Failed -> UploadStatus.Failed
            this == UploadStatus.Skipped -> UploadStatus.Skipped
            else -> UploadStatus.Pending
        }

    private fun UploadItem.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("sourceUri", sourceUri)
            .put("originalName", originalName)
            .put("extension", extension)
            .put("category", category.name)
            .put("title", title)
            .putNullable("author", author)
            .putNullable("documentsTag", documentsTag)
            .putNullable("mangaSeries", mangaSeries)
            .putNullable("mangaVolume", mangaVolume)
            .putNullable("year", year)
            .putNullable("language", language)
            .putNullable("series", series)
            .putNullable("seriesIndex", seriesIndex)
            .putNullable("publisher", publisher)
            .putNullable("coverUri", coverUri)
            .put("plannedPath", plannedPath)
            .put("status", status.name)

    private fun JSONObject.toUploadItemOrNull(): UploadItem? {
        val sourceUri = nullableString("sourceUri") ?: return null
        val originalName = nullableString("originalName") ?: return null
        val extension = nullableString("extension") ?: originalName.bookExtension().ifBlank { "bin" }
        val category = enumValueOrNull<BookCategory>(nullableString("category"))
            ?: classifier.classify(originalName)
        val title = nullableString("title") ?: originalName.bookTitleWithoutExtension()
        val status = enumValueOrNull<UploadStatus>(nullableString("status")) ?: UploadStatus.Pending

        return UploadItem(
            id = nullableString("id") ?: UUID.randomUUID().toString(),
            sourceUri = sourceUri,
            originalName = originalName,
            extension = extension,
            category = category,
            title = title,
            author = nullableString("author"),
            documentsTag = nullableString("documentsTag"),
            mangaSeries = nullableString("mangaSeries"),
            mangaVolume = nullableString("mangaVolume"),
            year = nullableString("year"),
            language = nullableString("language"),
            series = nullableString("series"),
            seriesIndex = nullableString("seriesIndex"),
            publisher = nullableString("publisher"),
            coverUri = nullableString("coverUri"),
            plannedPath = nullableString("plannedPath").orEmpty(),
            status = status,
            progress = optDouble("progress", 0.0).toFloat(),
        )
    }

    private fun JSONObject.putNullable(name: String, value: String?): JSONObject =
        put(name, value ?: JSONObject.NULL)

    private fun JSONObject.nullableString(name: String): String? =
        if (has(name) && !isNull(name)) getString(name) else null

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    private fun getCoverCacheDir(): File {
        return File(context.filesDir, "covers").apply { mkdirs() }
    }

    private fun getCoverCacheFile(itemId: String): File {
        return File(getCoverCacheDir(), "$itemId.jpg")
    }

    private fun cleanupCoverCacheFiles(items: List<UploadItem>) {
        val dir = getCoverCacheDir()
        if (!dir.isDirectory) return

        val activeIds = items.map { it.id }.toSet()

        runCatching {
            dir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val id = file.nameWithoutExtension
                    if (id !in activeIds) {
                        file.delete()
                    }
                }
            }
        }
    }

    private companion object {
        const val QueueStoreFileName = "upload_queue.json"
    }
}

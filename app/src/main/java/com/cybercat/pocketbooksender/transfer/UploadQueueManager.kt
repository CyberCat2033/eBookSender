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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class UploadQueueManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataExtractor: MetadataExtractor,
    private val classifier: FileClassifier,
    private val pathPlanner: PathPlanner,
    private val settingsRepository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _queue = MutableStateFlow<List<UploadItem>>(emptyList())
    val queue: StateFlow<List<UploadItem>> = _queue.asStateFlow()

    private var activeSettings = AppSettings()

    init {
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

    fun addUris(uris: List<Uri>) {
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

    fun addPreparedItems(items: List<UploadItem>) {
        if (items.isEmpty()) return

        val existing = _queue.value.queueIdentityKeys()
        val newItems = items
            .filterNot { item -> item.sourceUri in existing }
            .map { item ->
                if (item.status == UploadStatus.Preparing) {
                    item
                } else {
                    item.copy(status = UploadStatus.Preparing)
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

    fun removeItem(id: String) {
        _queue.update { current ->
            current.filterNot { it.id == id }
        }
    }

    fun clearQueue() {
        _queue.value = emptyList()
    }

    fun updateCategory(id: String, category: BookCategory) {
        _queue.update { current ->
            val settings = activeSettings
            val updatedList = current.map { item ->
                if (item.id != id) {
                    item
                } else if (item.extension == "cbr" || item.extension == "cbz") {
                    replan(item.copy(category = BookCategory.Manga), settings)
                } else {
                    val updated = when (category) {
                        BookCategory.Programming -> item.copy(
                            category = BookCategory.Programming,
                            programmingTag = item.programmingTag ?: settings.defaultProgrammingTag,
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

    fun updateProgrammingTag(id: String, tag: String) {
        _queue.update { current ->
            val trimmedTag = tag.trim()
            val updated = current.map { item ->
                if (item.id == id) {
                    replan(item.copy(programmingTag = trimmedTag), activeSettings)
                } else {
                    item
                }
            }
            updated.deduplicateQueue()
        }
    }

    fun updateMangaSeries(id: String, series: String) {
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

    fun updateQueuedMangaSeries(series: String) {
        val trimmedSeries = series.trim()
        if (trimmedSeries.isBlank()) return
        _queue.update { current ->
            val updated = current.map { item ->
                if (item.category == BookCategory.Manga && item.status != UploadStatus.Uploaded) {
                    replan(item.copy(mangaSeries = trimmedSeries), activeSettings)
                } else {
                    item
                }
            }
            updated.deduplicateQueue()
        }
    }

    fun updateQueue(updateBlock: (List<UploadItem>) -> List<UploadItem>) {
        _queue.update { current ->
            updateBlock(current).deduplicateQueue()
        }
    }

    private fun createUploadItem(uri: Uri, displayName: String, settings: AppSettings): UploadItem {
        val extension = displayName.bookExtension().ifBlank { "bin" }
        val title = displayName.bookTitleWithoutExtension()
        val category = classifier.classify(displayName)
        val preliminary = UploadItem(
            id = UUID.randomUUID().toString(),
            sourceUri = uri.toString(),
            originalName = displayName,
            extension = extension,
            category = category,
            title = title,
            author = if (category == BookCategory.Books) "Unknown Author" else null,
            programmingTag = if (category == BookCategory.Programming) settings.defaultProgrammingTag else null,
            mangaSeries = if (category == BookCategory.Manga) settings.defaultMangaSeries else null,
            mangaVolume = if (category == BookCategory.Manga) title else null,
            plannedPath = "",
            status = UploadStatus.Preparing,
        )

        return replan(preliminary, settings)
    }

    private suspend fun loadMetadata(item: UploadItem) {
        val metadata = metadataExtractor.extract(item.sourceUri, item.originalName)

        _queue.update { current ->
            val updatedList = current.map { currentItem ->
                if (currentItem.id != item.id || currentItem.status != UploadStatus.Preparing) {
                    currentItem
                } else {
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
                        coverUri = metadata.coverUri,
                        preview = metadata.preview,
                        status = UploadStatus.Pending,
                    )
                    replan(updated, activeSettings)
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
}

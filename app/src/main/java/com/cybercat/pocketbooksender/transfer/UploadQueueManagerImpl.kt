package com.cybercat.pocketbooksender.transfer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cybercat.pocketbooksender.data.settings.SettingsRepository
import com.cybercat.pocketbooksender.data.transfer.UploadQueueManager
import com.cybercat.pocketbooksender.di.ApplicationScope
import com.cybercat.pocketbooksender.domain.FileClassifier
import com.cybercat.pocketbooksender.domain.MangaTitleParser
import com.cybercat.pocketbooksender.domain.PathPlanner
import com.cybercat.pocketbooksender.domain.bookExtension
import com.cybercat.pocketbooksender.domain.bookTitleWithoutExtension
import com.cybercat.pocketbooksender.metadata.MetadataExtractor
import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.UploadItem
import com.cybercat.pocketbooksender.model.UploadStatus
import com.cybercat.pocketbooksender.model.toDomain
import com.cybercat.pocketbooksender.util.UploadPreviewCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class UploadQueueManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val metadataExtractor: MetadataExtractor,
    private val classifier: FileClassifier,
    private val pathPlanner: PathPlanner,
    private val settingsRepository: SettingsRepository,
    private val downloadCacheManager: DownloadCacheManager,
    private val localFileResolver: LocalFileResolver,
    private val mangaTitleParser: MangaTitleParser,
    private val queueStorageRepository: QueueStorageRepository
) : UploadQueueManager {
    private val _queue = MutableStateFlow<List<UploadItem>>(emptyList())
    override val queue: StateFlow<List<UploadItem>> = _queue.asStateFlow()

    private val metadataExtractionSemaphore = Semaphore(METADATA_EXTRACTION_PARALLELISM)

    private var activeSettings = AppSettings()
    private var queueRestored = false
    private var lastCleanedQueueItemIds = emptySet<String>()

    init {
        scope.launch {
            restorePersistedQueueState()
        }

        _queue
            .drop(1)
            .onEach { items ->
                if (queueRestored) {
                    persistQueueSnapshot(items)
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
                    val displayName = localFileResolver.resolveDisplayName(uri)
                        ?: uri.lastPathSegment
                        ?: "Book-${UUID.randomUUID()}"
                    val extension = displayName.bookExtension().lowercase().trim()

                    val isSupported =
                        extension in com.cybercat.pocketbooksender.domain.AllSupportedExtensions ||
                            (
                                extension.endsWith(".zip") &&
                                    extension.removeSuffix(".zip") in
                                    com.cybercat.pocketbooksender.domain.AllSupportedExtensions
                                )
                    val fileSize = localFileResolver.resolveFileSize(uri)
                    val isTooBig = fileSize > 500L * 1024L * 1024L // 500 MB limit

                    if (!isSupported || isTooBig) {
                        val reason = when {
                            !isSupported -> "unsupported format"
                            else -> "too large (>500MB)"
                        }
                        skippedFiles.add("$displayName ($reason)")
                        null
                    } else {
                        localFileResolver.persistReadPermission(uri)
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
                android.widget.Toast.makeText(
                    context,
                    message,
                    android.widget.Toast.LENGTH_LONG
                ).show()
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
        val removedItems = mutableListOf<UploadItem>()
        val retainedSourceUris = mutableSetOf<String>()

        _queue.update { current ->
            val remaining = current.filterNot { item -> item.id == id }
            removedItems.clear()
            removedItems.addAll(current.filter { item -> item.id == id })
            retainedSourceUris.clear()
            retainedSourceUris.addAll(remaining.map { item -> item.sourceUri })
            remaining
        }

        deleteDownloadCacheSources(
            removedItems = removedItems,
            retainedSourceUris = retainedSourceUris
        )
    }

    override fun clearQueue() {
        val removedItems = _queue.value
        if (removedItems.isEmpty()) return

        _queue.value = emptyList()
        deleteDownloadCacheSources(
            removedItems = removedItems,
            retainedSourceUris = emptySet()
        )
    }

    override fun removeDownloadCacheItems(): Int {
        val removedItems = mutableListOf<UploadItem>()
        val retainedSourceUris = mutableSetOf<String>()

        _queue.update { current ->
            val (removed, remaining) = current.partition { item ->
                downloadCacheManager.isDownloadCacheSource(item.sourceUri)
            }
            removedItems.clear()
            removedItems.addAll(removed)
            retainedSourceUris.clear()
            retainedSourceUris.addAll(remaining.map { item -> item.sourceUri })
            remaining
        }

        deleteDownloadCacheSources(
            removedItems = removedItems,
            retainedSourceUris = retainedSourceUris
        )

        return removedItems.size
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
                            documentsTag = item.documentsTag ?: settings.defaultDocumentsTag
                        )

                        BookCategory.Books -> item.copy(category = BookCategory.Books)

                        BookCategory.Manga -> item.copy(
                            category = BookCategory.Manga,
                            mangaSeries = item.mangaSeries ?: settings.defaultMangaSeries,
                            mangaVolume = item.mangaVolume ?: item.title
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
                    (
                        oldSeries == null ||
                            item.mangaSeries?.equals(oldSeries, ignoreCase = true) == true
                        )
                ) {
                    replan(item.copy(mangaSeries = trimmedSeries), activeSettings)
                } else {
                    item
                }
            }
            updated.deduplicateQueue()
        }
    }

    override fun updateQueue(
        deduplicate: Boolean,
        updateBlock: (List<UploadItem>) -> List<UploadItem>
    ) {
        _queue.update { current ->
            val updated = updateBlock(current)
            when {
                updated === current -> current
                deduplicate -> updated.deduplicateQueue()
                else -> updated
            }
        }
    }

    private fun restorePersistedQueue(): RestoredQueueState {
        val metadataReloadItemIds = mutableSetOf<String>()
        val items = queueStorageRepository.restore()
            .mapNotNull { entity ->
                val item = entity.toDomain(
                    fallbackId = { UUID.randomUUID().toString() },
                    fallbackCategory = classifier::classify,
                    fallbackExtension = { name -> name.bookExtension().ifBlank { "bin" } },
                    fallbackTitle = { name -> name.bookTitleWithoutExtension() }
                ) ?: return@mapNotNull null
                if (item.status == UploadStatus.Uploaded) return@mapNotNull null
                val canReadSource = localFileResolver.canRead(Uri.parse(item.sourceUri))
                if (item.status == UploadStatus.Preparing && canReadSource) {
                    metadataReloadItemIds += item.id
                }
                val restoredStatus = item.status.restoredAfterProcessStart(canReadSource)

                replan(
                    item.copy(
                        status = restoredStatus,
                        progress = if (restoredStatus == UploadStatus.Uploaded) 1f else 0f
                    ),
                    activeSettings
                )
            }
            .deduplicateQueue()

        return RestoredQueueState(
            items = items,
            metadataReloadItemIds = metadataReloadItemIds.intersect(items.queueItemIds())
        )
    }

    private suspend fun restorePersistedQueueState() {
        val restoredQueueState = try {
            withTimeoutOrNull(QUEUE_RESTORE_TIMEOUT_MILLIS) {
                withContext(Dispatchers.IO) {
                    restorePersistedQueue()
                }
            } ?: run {
                Log.w(
                    TAG,
                    "Timed out restoring upload queue after ${QUEUE_RESTORE_TIMEOUT_MILLIS}ms"
                )
                RestoredQueueState.empty()
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Log.e(TAG, "Failed to restore persisted upload queue", error)
            RestoredQueueState.empty()
        }

        _queue.update { current ->
            (restoredQueueState.items + current).deduplicateQueue()
        }
        queueRestored = true
        persistQueueSnapshot(_queue.value)

        val itemIdsNeedingMetadataReload = restoredQueueState.metadataReloadItemIds
        _queue.value
            .filter { item -> item.id in itemIdsNeedingMetadataReload }
            .forEach { item ->
                scope.launch {
                    loadMetadata(item)
                }
            }
    }

    private suspend fun persistQueueSnapshot(items: List<UploadItem>) {
        val queueItemIds = items.queueItemIds()
        try {
            withContext(Dispatchers.IO) {
                queueStorageRepository.persist(items)
                if (queueItemIds != lastCleanedQueueItemIds) {
                    cleanupCoverCacheFiles(items)
                }
            }
            lastCleanedQueueItemIds = queueItemIds
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Log.e(TAG, "Failed to persist upload queue snapshot", error)
        }
    }

    private fun createUploadItem(uri: Uri, displayName: String, settings: AppSettings): UploadItem {
        val extension = displayName.bookExtension().ifBlank { "bin" }
        val title = displayName.bookTitleWithoutExtension()
        val category = classifier.classify(displayName)

        var mangaSeries: String? = null
        var mangaVolume: String? = null

        if (category == BookCategory.Manga) {
            val parsed = mangaTitleParser.parse(displayName)
            mangaSeries = parsed.series ?: settings.defaultMangaSeries
            mangaVolume = parsed.volume ?: title
        }

        val preliminary = UploadItem(
            id = UUID.randomUUID().toString(),
            sourceUri = uri.toString(),
            originalName = displayName,
            extension = extension,
            category = category,
            title = title,
            author = if (category == BookCategory.Books) "Unknown Author" else null,
            documentsTag = if (category ==
                BookCategory.Documents
            ) {
                settings.defaultDocumentsTag
            } else {
                null
            },
            mangaSeries = mangaSeries,
            mangaVolume = mangaVolume,
            plannedPath = "",
            status = UploadStatus.Preparing
        )

        return replan(preliminary, settings)
    }

    private suspend fun loadMetadata(item: UploadItem) {
        val metadata = metadataExtractionSemaphore.withPermit {
            if (!shouldLoadMetadata(item.id)) return@withPermit null
            metadataExtractor.extract(item.sourceUri, item.originalName)
        } ?: return
        val preview = metadata.preview

        if (preview != null && _queue.value.any { currentItem -> currentItem.id == item.id }) {
            withContext(Dispatchers.IO) {
                UploadPreviewCache.save(
                    context = context,
                    itemId = item.id,
                    preview = preview
                )
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
                        author = if (currentItem.category ==
                            BookCategory.Books
                        ) {
                            author
                        } else {
                            currentItem.author
                        },
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
                        status = UploadStatus.Pending
                    )
                    replan(updated, activeSettings)
                } else {
                    currentItem.copy(
                        coverUri = metadata.coverUri ?: currentItem.coverUri
                    )
                }
            }
            updatedList.deduplicateQueue()
        }
    }

    private fun shouldLoadMetadata(itemId: String): Boolean = _queue.value.any { item ->
        item.id == itemId && item.status != UploadStatus.Uploaded
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

    private fun List<UploadItem>.queueIdentityKeys(): Set<String> = flatMap { item ->
        listOfNotNull(
            item.sourceUri.takeIf { it.isNotBlank() },
            item.plannedPath.takeIf { it.isNotBlank() }
        )
    }.toSet()

    private fun List<UploadItem>.queueItemIds(): Set<String> = mapTo(mutableSetOf()) { it.id }

    private fun UploadStatus.restoredAfterProcessStart(canReadSource: Boolean): UploadStatus =
        when {
            !canReadSource -> UploadStatus.Failed
            this == UploadStatus.Uploaded -> UploadStatus.Uploaded
            this == UploadStatus.Failed -> UploadStatus.Failed
            this == UploadStatus.Skipped -> UploadStatus.Skipped
            else -> UploadStatus.Pending
        }

    private fun cleanupCoverCacheFiles(items: List<UploadItem>) {
        UploadPreviewCache.cleanup(
            context = context,
            activeItemIds = items.map { it.id }.toSet()
        )
    }

    private fun deleteDownloadCacheSources(
        removedItems: Collection<UploadItem>,
        retainedSourceUris: Set<String>
    ) {
        if (removedItems.isEmpty()) return

        scope.launch {
            try {
                downloadCacheManager.deleteDownloadSources(
                    sourceUris = removedItems.map { item -> item.sourceUri },
                    retainedSourceUris = retainedSourceUris
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Log.e(TAG, "Failed to delete cached download sources", error)
            }
        }
    }

    private companion object {
        const val METADATA_EXTRACTION_PARALLELISM = 1
        const val QUEUE_RESTORE_TIMEOUT_MILLIS = 5_000L
        const val TAG = "UploadQueueManager"
    }
}

private data class RestoredQueueState(
    val items: List<UploadItem>,
    val metadataReloadItemIds: Set<String>
) {
    companion object {
        fun empty(): RestoredQueueState = RestoredQueueState(
            items = emptyList(),
            metadataReloadItemIds = emptySet()
        )
    }
}

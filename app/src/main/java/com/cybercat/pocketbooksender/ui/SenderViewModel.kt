package com.cybercat.pocketbooksender.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.cybercat.pocketbooksender.data.ftp.FtpEntry
import com.cybercat.pocketbooksender.data.ftp.FtpGateway
import com.cybercat.pocketbooksender.data.manga.MangaChapter
import com.cybercat.pocketbooksender.data.manga.MangaDownloadedChapter
import com.cybercat.pocketbooksender.data.manga.MangaDownloadProgress
import com.cybercat.pocketbooksender.data.manga.MangaRepository
import com.cybercat.pocketbooksender.data.manga.MangaSeriesDetails
import com.cybercat.pocketbooksender.data.opds.OpdsAcquisition
import com.cybercat.pocketbooksender.data.opds.OpdsEntry
import com.cybercat.pocketbooksender.data.opds.OpdsLink
import com.cybercat.pocketbooksender.data.opds.OpdsRepository
import com.cybercat.pocketbooksender.data.settings.SettingsRepository
import com.cybercat.pocketbooksender.domain.FileClassifier
import com.cybercat.pocketbooksender.domain.FtpUrlParser
import com.cybercat.pocketbooksender.domain.NaturalSort
import com.cybercat.pocketbooksender.domain.PathPlanner
import com.cybercat.pocketbooksender.domain.bookExtension
import com.cybercat.pocketbooksender.domain.bookTitleWithoutExtension
import com.cybercat.pocketbooksender.domain.contentExtension
import com.cybercat.pocketbooksender.metadata.MetadataExtractor
import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.CatalogFile
import com.cybercat.pocketbooksender.model.CatalogGroup
import com.cybercat.pocketbooksender.model.DeviceCatalog
import com.cybercat.pocketbooksender.model.MangaSeriesGroup
import com.cybercat.pocketbooksender.model.PocketBookDevice
import com.cybercat.pocketbooksender.model.UploadItem
import com.cybercat.pocketbooksender.model.UploadStatus
import com.cybercat.pocketbooksender.transfer.TransferCoordinator
import com.cybercat.pocketbooksender.transfer.TransferEvent
import com.cybercat.pocketbooksender.transfer.TransferForegroundService
import com.cybercat.pocketbooksender.transfer.TransferUploadItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SenderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ftpGateway: FtpGateway,
    private val metadataExtractor: MetadataExtractor,
    private val settingsRepository: SettingsRepository,
    private val opdsRepository: OpdsRepository,
    private val mangaRepository: MangaRepository,
) : ViewModel() {
    private val classifier = FileClassifier()
    private val pathPlanner = PathPlanner()

    private val _state = MutableStateFlow(SenderUiState())
    val state: StateFlow<SenderUiState> = _state.asStateFlow()

    init {
        _state.update { state ->
            state.copy(
                manga = state.manga.copy(
                    sources = mangaRepository.sources,
                    browserUrl = mangaRepository.homeUrl(state.manga.selectedSourceId),
                ),
            )
        }
        viewModelScope.launch {
            opdsRepository.seedDefaultsIfNeeded()
        }
        viewModelScope.launch {
            opdsRepository.sources.collect { sources ->
                _state.update { state ->
                    state.copy(
                        opds = state.opds.copy(
                            sources = sources,
                            urlInput = state.opds.urlInput.ifBlank {
                                sources.firstOrNull()?.url.orEmpty()
                            },
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { state ->
                    state.copy(
                        settings = settings,
                        connectedDevice = state.connectedDevice?.copy(
                            rootPath = settings.rootPath.ifBlank { "/mnt/ext1" },
                        ),
                        queue = state.queue.map { replan(it, settings) }.deduplicateQueue(),
                    )
                }
            }
        }
        viewModelScope.launch {
            TransferCoordinator.events.collect { event ->
                handleTransferEvent(event)
            }
        }
        viewModelScope.launch {
            mangaRepository.downloadedStableKeys.collect { keys ->
                _state.update { state ->
                    state.copy(manga = state.manga.copy(downloadedStableKeys = keys))
                }
            }
        }
    }

    fun onOpdsUrlChanged(value: String) {
        _state.update {
            it.copy(
                opds = it.opds.copy(
                    urlInput = value,
                    errorMessage = null,
                    statusMessage = null,
                ),
            )
        }
    }

    fun onOpdsTitleChanged(value: String) {
        _state.update {
            it.copy(
                opds = it.opds.copy(
                    titleInput = value,
                    errorMessage = null,
                    statusMessage = null,
                ),
            )
        }
    }

    fun onOpdsSearchChanged(value: String) {
        _state.update {
            it.copy(
                opds = it.opds.copy(
                    searchInput = value,
                    errorMessage = null,
                    statusMessage = null,
                ),
            )
        }
    }

    fun selectWebMode(mode: WebContentMode) {
        _state.update { state ->
            state.copy(opds = state.opds.copy(webMode = mode))
        }
    }

    fun saveOpdsSource(title: String, url: String) {
        viewModelScope.launch {
            runCatching {
                opdsRepository.addSource(
                    title = title.trim(),
                    url = url.trim(),
                )
            }.onSuccess {
                showOpdsStatus("Source saved")
            }.onFailure { error ->
                _state.update { state ->
                    state.copy(
                        opds = state.opds.copy(
                            errorMessage = error.message ?: "Cannot save OPDS source",
                            statusMessage = null,
                        ),
                    )
                }
            }
        }
    }

    fun removeOpdsSource(id: String) {
        viewModelScope.launch {
            opdsRepository.removeSource(id)
        }
    }

    fun openOpdsInput() {
        openOpdsUrl(_state.value.opds.urlInput)
    }

    fun openOpdsUrl(url: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            _state.update {
                it.copy(
                    opds = it.opds.copy(
                        errorMessage = "OPDS URL is empty",
                        statusMessage = null,
                    ),
                )
            }
            return
        }

        val previous = _state.value.opds
        loadOpdsCatalog(
            url = trimmedUrl,
            history = previous.currentUrl?.let { currentUrl ->
                previous.history + OpdsHistoryEntry(
                    title = previous.catalog?.title ?: "Catalog",
                    url = currentUrl,
                )
            } ?: previous.history,
        )
    }

    fun openOpdsLink(link: OpdsLink) {
        openOpdsUrl(link.href)
    }

    fun goBackOpds() {
        val snapshot = _state.value.opds
        val previous = snapshot.history.lastOrNull() ?: return
        loadOpdsCatalog(
            url = previous.url,
            history = snapshot.history.dropLast(1),
        )
    }

    fun searchOpds() {
        val snapshot = _state.value.opds
        val query = snapshot.searchInput.trim()
        val currentUrl = snapshot.currentUrl
        val catalog = snapshot.catalog

        if (query.isBlank()) {
            _state.update {
                it.copy(
                    opds = it.opds.copy(
                        errorMessage = "Search query is empty",
                        statusMessage = null,
                    ),
                )
            }
            return
        }

        if (currentUrl == null || catalog == null) {
            _state.update {
                it.copy(
                    opds = it.opds.copy(
                        errorMessage = "Open OPDS catalog first",
                        statusMessage = null,
                    ),
                )
            }
            return
        }

        val searchLink = catalog.links.firstOrNull { link ->
            link.rel.orEmpty().equals("search", ignoreCase = true)
        }

        if (searchLink == null) {
            _state.update {
                it.copy(
                    opds = it.opds.copy(
                        errorMessage = "This catalog page does not expose OPDS search",
                        statusMessage = null,
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    opds = it.opds.copy(
                        isLoading = true,
                        errorMessage = null,
                        statusMessage = null,
                    ),
                )
            }

            runCatching {
                opdsRepository.buildSearchUrl(currentUrl, searchLink, query)
            }.onSuccess { searchUrl ->
                loadOpdsCatalog(
                    url = searchUrl,
                    history = snapshot.history + OpdsHistoryEntry(
                        title = catalog.title,
                        url = currentUrl,
                    ),
                )
            }.onFailure { error ->
                _state.update { state ->
                    state.copy(
                        opds = state.opds.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Cannot build OPDS search URL",
                            statusMessage = null,
                        ),
                    )
                }
            }
        }
    }

    fun downloadOpdsAcquisition(
        entry: OpdsEntry,
        acquisition: OpdsAcquisition,
    ) {
        val baseUrl = _state.value.opds.currentUrl
        if (baseUrl == null) {
            _state.update {
                it.copy(
                    opds = it.opds.copy(
                        errorMessage = "Open OPDS catalog first",
                        statusMessage = null,
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    opds = it.opds.copy(
                        isDownloading = true,
                        errorMessage = null,
                        statusMessage = null,
                    ),
                )
            }

            runCatching {
                opdsRepository.downloadPublication(baseUrl, entry, acquisition)
            }.onSuccess { file ->
                addDownloadedOpdsFile(file)
                _state.update { state ->
                    state.copy(opds = state.opds.copy(isDownloading = false))
                }
                showOpdsStatus("Added to queue: ${file.name}")
            }.onFailure { error ->
                _state.update { state ->
                    state.copy(
                        opds = state.opds.copy(
                            isDownloading = false,
                            errorMessage = error.message ?: "Cannot download OPDS book",
                            statusMessage = null,
                        ),
                    )
                }
            }
        }
    }

    fun downloadOpdsEntries(entries: List<OpdsEntry>) {
        val baseUrl = _state.value.opds.currentUrl
        if (baseUrl == null) {
            _state.update {
                it.copy(
                    opds = it.opds.copy(
                        errorMessage = "Open OPDS catalog first",
                        statusMessage = null,
                    ),
                )
            }
            return
        }

        val downloadable = entries.mapNotNull { entry ->
            entry.bestAcquisition()?.let { acquisition -> entry to acquisition }
        }
        if (downloadable.isEmpty()) {
            _state.update {
                it.copy(
                    opds = it.opds.copy(
                        errorMessage = "No selected downloadable entries",
                        statusMessage = null,
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    opds = it.opds.copy(
                        isDownloading = true,
                        errorMessage = null,
                        statusMessage = null,
                    ),
                )
            }

            val downloadedFiles = mutableListOf<File>()
            var failedCount = 0
            downloadable.forEach { (entry, acquisition) ->
                runCatching {
                    opdsRepository.downloadPublication(baseUrl, entry, acquisition)
                }.onSuccess { file ->
                    downloadedFiles += file
                }.onFailure {
                    failedCount += 1
                }
            }

            if (downloadedFiles.isNotEmpty()) {
                addUris(downloadedFiles.map { file -> Uri.fromFile(file) })
            }

            _state.update { state ->
                state.copy(
                    opds = state.opds.copy(
                        isDownloading = false,
                        errorMessage = if (failedCount > 0) {
                            "Failed to download $failedCount entries"
                        } else {
                            null
                        },
                    ),
                )
            }

            if (downloadedFiles.isNotEmpty()) {
                showOpdsStatus("Added to queue: ${downloadedFiles.size} files")
            }
        }
    }

    fun onMangaSearchChanged(value: String) {
        _state.update {
            it.copy(
                manga = it.manga.copy(
                    searchInput = value,
                    errorMessage = null,
                    statusMessage = null,
                ),
            )
        }
    }

    fun selectMangaSource(sourceId: String) {
        val source = mangaRepository.sources.firstOrNull { source -> source.id == sourceId } ?: return
        _state.update { state ->
            state.copy(
                manga = state.manga.copy(
                    selectedSourceId = source.id,
                    browserUrl = source.homeUrl,
                    currentWebUrl = null,
                    searchResults = emptyList(),
                    selectedSeries = null,
                    chapters = emptyList(),
                    selectedChapterIds = emptySet(),
                    errorMessage = null,
                    statusMessage = null,
                ),
            )
        }
    }

    fun openMangaBrowser(url: String? = null) {
        _state.update { state ->
            val homeUrl = mangaRepository.homeUrl(state.manga.selectedSourceId)
            state.copy(
                manga = state.manga.copy(
                    browserVisible = true,
                    browserUrl = url ?: homeUrl,
                    currentWebUrl = state.manga.currentWebUrl ?: homeUrl,
                ),
            )
        }
    }

    fun closeMangaBrowser() {
        _state.update { state ->
            state.copy(manga = state.manga.copy(browserVisible = false))
        }
    }

    fun syncMangaWebPage(url: String, html: String) {
        _state.update { state ->
            state.copy(
                manga = state.manga.copy(
                    currentWebUrl = url,
                    errorMessage = null,
                ),
            )
        }
    }

    fun searchManga() {
        val snapshot = _state.value.manga
        val query = snapshot.searchInput.trim()
        if (query.isBlank()) {
            _state.update { state ->
                state.copy(manga = state.manga.copy(errorMessage = "Manga search query is empty"))
            }
            return
        }

        _state.update { state ->
            state.copy(
                manga = state.manga.copy(
                    isLoading = true,
                    browserVisible = false,
                    errorMessage = null,
                    statusMessage = null,
                ),
            )
        }

        viewModelScope.launch {
            runCatching {
                mangaRepository.searchSeries(snapshot.selectedSourceId, query)
            }.onSuccess { results ->
                _state.update { state ->
                    state.copy(
                        manga = state.manga.copy(
                            searchResults = results,
                            selectedSeries = null,
                            chapters = emptyList(),
                            selectedChapterIds = emptySet(),
                            isLoading = false,
                            browserVisible = false,
                            statusMessage = null,
                            errorMessage = null,
                        ),
                    )
                }
                if (results.isEmpty()) {
                    showMangaStatus("No manga found")
                }
            }.onFailure { error ->
                _state.update { state ->
                    state.copy(
                        manga = state.manga.copy(
                            isLoading = false,
                            browserVisible = false,
                            errorMessage = error.message ?: "Cannot search manga source",
                            statusMessage = null,
                        ),
                    )
                }
            }
        }
    }

    fun openMangaSeries(seriesId: String) {
        val sourceId = _state.value.manga.selectedSourceId
        _state.update { state ->
            state.copy(
                manga = state.manga.copy(
                    isLoading = true,
                    browserVisible = false,
                    errorMessage = null,
                    statusMessage = null,
                ),
            )
        }

        viewModelScope.launch {
            runCatching {
                mangaRepository.openSeries(sourceId, seriesId)
            }.onSuccess { seriesPage ->
                _state.update { state ->
                    state.copy(
                        manga = state.manga.copy(
                            selectedSeries = seriesPage.details,
                            chapters = seriesPage.chapters,
                            selectedChapterIds = emptySet(),
                            isLoading = false,
                            browserVisible = false,
                            errorMessage = null,
                        ),
                    )
                }
            }.onFailure { error ->
                _state.update { state ->
                    state.copy(
                        manga = state.manga.copy(
                            isLoading = false,
                            browserVisible = false,
                            errorMessage = error.message ?: "Cannot open manga series",
                            statusMessage = null,
                        ),
                    )
                }
            }
        }
    }

    fun toggleMangaChapter(chapterId: String, selected: Boolean) {
        _state.update { state ->
            val selectedIds = if (selected) {
                state.manga.selectedChapterIds + chapterId
            } else {
                state.manga.selectedChapterIds - chapterId
            }
            state.copy(manga = state.manga.copy(selectedChapterIds = selectedIds))
        }
    }

    fun selectNewMangaChapters() {
        _state.update { state ->
            val selectedIds = state.manga.chapters
                .filter { chapter -> chapter.stableKey !in state.manga.downloadedStableKeys }
                .mapTo(mutableSetOf()) { chapter -> chapter.chapterId }
            state.copy(manga = state.manga.copy(selectedChapterIds = selectedIds))
        }
    }

    fun selectAllMangaChapters() {
        _state.update { state ->
            state.copy(
                manga = state.manga.copy(
                    selectedChapterIds = state.manga.chapters.mapTo(mutableSetOf()) { chapter ->
                        chapter.chapterId
                    },
                ),
            )
        }
    }

    fun clearMangaChapterSelection() {
        _state.update { state ->
            state.copy(manga = state.manga.copy(selectedChapterIds = emptySet()))
        }
    }

    fun downloadSelectedMangaChapters() {
        val snapshot = _state.value.manga
        val series = snapshot.selectedSeries
        if (series == null) {
            _state.update { state ->
                state.copy(manga = state.manga.copy(errorMessage = "Open manga series first"))
            }
            return
        }

        val selectedChapters = snapshot.selectedChapters
        if (selectedChapters.isEmpty()) {
            _state.update { state ->
                state.copy(manga = state.manga.copy(errorMessage = "Select manga chapters first"))
            }
            return
        }

        _state.update { state ->
            state.copy(
                manga = state.manga.copy(
                    isDownloading = true,
                    downloadProgressText = "Preparing ${selectedChapters.size} chapters",
                    errorMessage = null,
                    statusMessage = null,
                ),
            )
        }

        viewModelScope.launch {
            runCatching {
                mangaRepository.downloadChapters(
                    sourceId = snapshot.selectedSourceId,
                    series = series,
                    chapters = selectedChapters,
                    onProgress = { progress ->
                        _state.update { state ->
                            state.copy(
                                manga = state.manga.copy(
                                    downloadProgressText = progress.toUiText(),
                                ),
                            )
                        }
                    },
                )
            }.onSuccess { result ->
                if (result.downloaded.isNotEmpty()) {
                    addDownloadedMangaFiles(series, result.downloaded)
                }

                _state.update { state ->
                    val downloadedIds = result.downloaded.mapTo(mutableSetOf()) { downloaded ->
                        downloaded.chapter.chapterId
                    }
                    state.copy(
                        manga = state.manga.copy(
                            isDownloading = false,
                            downloadProgressText = null,
                            selectedChapterIds = state.manga.selectedChapterIds - downloadedIds,
                            errorMessage = formatMangaFailures(result.failedMessages),
                        ),
                    )
                }

                if (result.downloaded.isNotEmpty()) {
                    showMangaStatus("Added to queue: ${result.downloaded.size} chapters")
                }
            }.onFailure { error ->
                _state.update { state ->
                    state.copy(
                        manga = state.manga.copy(
                            isDownloading = false,
                            downloadProgressText = null,
                            errorMessage = error.message ?: "Cannot download manga chapters",
                        ),
                    )
                }
            }
        }
    }

    private fun MangaDownloadProgress.toUiText(): String {
        val chapterPart = "Chapter ${completedChapters + 1}/$totalChapters"
        val pagePart = when {
            !detail.isNullOrBlank() -> ", $detail"
            totalPages > 0 -> ", page $completedPages/$totalPages"
            else -> ""
        }
        return "$chapterPart$pagePart: $chapterTitle"
    }

    private fun formatMangaFailures(failedMessages: List<String>): String? {
        if (failedMessages.isEmpty()) return null
        val visible = failedMessages.take(3).joinToString("\n")
        val hiddenCount = failedMessages.size - 3
        return if (hiddenCount > 0) {
            "$visible\n...and $hiddenCount more failed"
        } else {
            visible
        }
    }

    fun onFtpInputChanged(value: String) {
        _state.update { it.copy(ftpInput = value, errorMessage = null) }
    }

    fun connect() {
        connectTo(_state.value.ftpInput)
    }

    fun connectTo(rawLink: String) {
        val parsedDevice = FtpUrlParser.parse(rawLink)
            .getOrElse { error ->
                _state.update {
                    it.copy(
                        isConnecting = false,
                        errorMessage = error.message ?: "Invalid FTP link",
                    )
                }
                return
            }

        val device = parsedDevice.copy(
            rootPath = _state.value.settings.rootPath.ifBlank { "/mnt/ext1" },
        )

        _state.update {
            it.copy(
                isConnecting = true,
                connectedDevice = null,
                ftpInput = device.ftpUrl,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            ftpGateway.checkConnection(device)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            connectedDevice = device,
                            ftpInput = device.ftpUrl,
                            errorMessage = null,
                        )
                    }
                    refreshRemoteSuggestions(device)
                    refreshDeviceCatalog(device)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            connectedDevice = null,
                            errorMessage = error.toFtpConnectionMessage(device),
                        )
                    }
                }
        }
    }

    fun disconnect() {
        _state.update { it.copy(isConnecting = false, connectedDevice = null) }
    }

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return

        val settings = _state.value.settings
        val existing = _state.value.queue.queueIdentityKeys()
        val newItems = uris
            .distinctBy { uri -> uri.toString() }
            .mapNotNull { uri ->
                val uriString = uri.toString()
                if (uriString in existing) {
                    null
                } else {
                    persistReadPermission(uri)
                    val displayName = resolveDisplayName(uri)
                        ?: uri.lastPathSegment
                        ?: "Book-${UUID.randomUUID()}"
                    createUploadItem(uri, displayName, settings)
                }
            }

        if (newItems.isEmpty()) {
            _state.update { it.copy(errorMessage = "Selected files are already in queue") }
            return
        }

        _state.update { current ->
            current.copy(queue = (current.queue + newItems).deduplicateQueue(), errorMessage = null)
        }

        newItems.forEach { item ->
            viewModelScope.launch {
                loadMetadata(item)
            }
        }
    }

    fun removeItem(id: String) {
        _state.update { state ->
            state.copy(queue = state.queue.filterNot { it.id == id })
        }
    }

    fun clearQueue() {
        _state.update { it.copy(queue = emptyList()) }
    }

    fun updateCategory(id: String, category: BookCategory) {
        _state.update { state ->
            val queue = state.queue.map { item ->
                if (item.id != id) {
                    item
                } else if (item.extension == "cbr" || item.extension == "cbz") {
                    replan(item.copy(category = BookCategory.Manga), state.settings)
                } else {
                    val updated = when (category) {
                        BookCategory.Programming -> item.copy(
                            category = BookCategory.Programming,
                            programmingTag = item.programmingTag ?: state.settings.defaultProgrammingTag,
                        )
                        BookCategory.Books -> item.copy(category = BookCategory.Books)
                        BookCategory.Manga -> item.copy(
                            category = BookCategory.Manga,
                            mangaSeries = item.mangaSeries ?: state.settings.defaultMangaSeries,
                            mangaVolume = item.mangaVolume ?: item.title,
                        )
                    }
                    replan(updated, state.settings)
                }
            }
            state.copy(queue = queue.deduplicateQueue())
        }
    }

    fun updateProgrammingTag(id: String, tag: String) {
        _state.update { state ->
            val trimmedTag = tag.trim()
            val queue = state.queue.map { item ->
                if (item.id == id) {
                    replan(item.copy(programmingTag = trimmedTag), state.settings)
                } else {
                    item
                }
            }

            state.copy(queue = queue.deduplicateQueue())
        }
    }

    fun updateMangaSeries(id: String, series: String) {
        _state.update { state ->
            val trimmedSeries = series.trim()
            val queue = state.queue.map { item ->
                if (item.id == id) {
                    replan(item.copy(mangaSeries = trimmedSeries), state.settings)
                } else {
                    item
                }
            }

            state.copy(queue = queue.deduplicateQueue())
        }
    }

    fun updateQueuedMangaSeries(series: String) {
        _state.update { state ->
            val trimmedSeries = series.trim()
            if (trimmedSeries.isBlank()) return@update state

            val queue = state.queue.map { item ->
                if (item.category == BookCategory.Manga && item.status != UploadStatus.Uploaded) {
                    replan(item.copy(mangaSeries = trimmedSeries), state.settings)
                } else {
                    item
                }
            }

            state.copy(queue = queue.deduplicateQueue())
        }
    }

    fun updateRootPath(value: String) {
        _state.update { state ->
            val settings = state.settings.copy(rootPath = value)
            state.copy(
                settings = settings,
                connectedDevice = state.connectedDevice?.copy(
                    rootPath = settings.rootPath.ifBlank { "/mnt/ext1" },
                ),
                queue = state.queue.map { replan(it, settings) }.deduplicateQueue(),
            )
        }
        viewModelScope.launch {
            settingsRepository.setRootPath(value)
        }
    }

    fun updateDynamicColor(enabled: Boolean) {
        _state.update { state ->
            state.copy(settings = state.settings.copy(useDynamicColor = enabled))
        }
        viewModelScope.launch {
            settingsRepository.setUseDynamicColor(enabled)
        }
    }

    fun clearDownloadCache() {
        viewModelScope.launch {
            val deletedBytes = withContext(Dispatchers.IO) {
                listOf("opds", "manga")
                    .sumOf { name -> clearDirectory(File(context.cacheDir, name)) }
            }
            _state.update { state ->
                state.copy(settingsStatusMessage = "Cleared ${deletedBytes.formatBytes()} of downloaded cache")
            }
        }
    }

    fun uploadAll() {
        val snapshot = _state.value
        val device = snapshot.connectedDevice
        if (device == null) {
            _state.update { it.copy(errorMessage = "Connect PocketBook before upload") }
            return
        }
        if (snapshot.queue.isEmpty()) {
            _state.update { it.copy(errorMessage = "Queue is empty") }
            return
        }

        val normalizedQueue = snapshot.queue.deduplicateQueue()
        if (normalizedQueue.size != snapshot.queue.size) {
            _state.update { it.copy(queue = normalizedQueue) }
        }

        val uploadableItems = normalizedQueue.filter {
            it.status == UploadStatus.Pending ||
                it.status == UploadStatus.Failed ||
                it.status == UploadStatus.Skipped
        }
        if (uploadableItems.isEmpty()) {
            _state.update { it.copy(errorMessage = "No pending files to upload") }
            return
        }

        val requestId = TransferCoordinator.submit(
            device = device,
            items = uploadableItems.map { item ->
                TransferUploadItem(
                    id = item.id,
                    sourceUri = item.sourceUri,
                    originalName = item.originalName,
                    extension = item.extension,
                    plannedPath = item.plannedPath,
                )
            },
        )

        _state.update { state ->
            state.copy(
                queue = state.queue.map { item ->
                    if (uploadableItems.any { it.id == item.id }) {
                        item.copy(status = UploadStatus.Uploading, progress = 0.02f)
                    } else {
                        item
                    }
                }.deduplicateQueue(),
                errorMessage = null,
            )
        }

        ContextCompat.startForegroundService(
            context,
            TransferForegroundService.createIntent(context, requestId),
        )
    }

    fun refreshDeviceCatalog() {
        val device = _state.value.connectedDevice
        if (device == null) {
            _state.update {
                it.copy(deviceCatalog = it.deviceCatalog.copy(errorMessage = "Connect PocketBook first"))
            }
            return
        }
        refreshDeviceCatalog(device)
    }

    private fun handleTransferEvent(event: TransferEvent) {
        when (event) {
            is TransferEvent.ItemStarted -> {
                _state.update { state ->
                    state.copy(
                        queue = state.queue.map { item ->
                            if (item.id == event.itemId) {
                                item.copy(status = UploadStatus.Uploading, progress = 0.05f)
                            } else {
                                item
                            }
                        }.deduplicateQueue(),
                    )
                }
            }
            is TransferEvent.ItemUploaded -> {
                _state.update { state ->
                    state.copy(
                        queue = state.queue.map { item ->
                            if (item.id == event.itemId) {
                                item.copy(status = UploadStatus.Uploaded, progress = 1f)
                            } else {
                                item
                            }
                        }.deduplicateQueue(),
                    )
                }
            }
            is TransferEvent.ItemFailed -> {
                _state.update { state ->
                    state.copy(
                        queue = state.queue.map { item ->
                            if (item.id == event.itemId) {
                                item.copy(status = UploadStatus.Failed, progress = 0f)
                            } else {
                                item
                            }
                        }.deduplicateQueue(),
                        errorMessage = event.message,
                    )
                }
            }
            is TransferEvent.Completed -> {
                val device = _state.value.connectedDevice
                if (device != null) {
                    refreshRemoteSuggestions(device)
                    refreshDeviceCatalog(device)
                }
            }
        }
    }

    private fun showOpdsStatus(message: String) {
        _state.update { state ->
            state.copy(
                opds = state.opds.copy(
                    statusMessage = message,
                    errorMessage = null,
                ),
            )
        }

        viewModelScope.launch {
            delay(OpdsStatusMessageMillis)
            _state.update { state ->
                if (state.opds.statusMessage == message) {
                    state.copy(opds = state.opds.copy(statusMessage = null))
                } else {
                    state
                }
            }
        }
    }

    private fun showMangaStatus(message: String) {
        _state.update { state ->
            state.copy(
                manga = state.manga.copy(
                    statusMessage = message,
                    errorMessage = null,
                ),
            )
        }

        viewModelScope.launch {
            delay(StatusMessageMillis)
            _state.update { state ->
                if (state.manga.statusMessage == message) {
                    state.copy(manga = state.manga.copy(statusMessage = null))
                } else {
                    state
                }
            }
        }
    }

    private fun loadOpdsCatalog(
        url: String,
        history: List<OpdsHistoryEntry>,
    ) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    opds = it.opds.copy(
                        isLoading = true,
                        errorMessage = null,
                        statusMessage = null,
                    ),
                )
            }

            runCatching {
                opdsRepository.loadCatalog(url)
            }.onSuccess { catalog ->
                _state.update { state ->
                    state.copy(
                        opds = state.opds.copy(
                            currentUrl = url,
                            urlInput = url,
                            catalog = catalog,
                            history = history,
                            isLoading = false,
                            errorMessage = null,
                            statusMessage = null,
                        ),
                    )
                }
            }.onFailure { error ->
                _state.update { state ->
                    state.copy(
                        opds = state.opds.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Cannot open OPDS catalog",
                            statusMessage = null,
                        ),
                    )
                }
            }
        }
    }

    private fun addDownloadedOpdsFile(file: File) {
        addUris(listOf(Uri.fromFile(file)))
    }

    private fun addDownloadedMangaFiles(
        series: MangaSeriesDetails,
        downloaded: List<MangaDownloadedChapter>,
    ) {
        val settings = _state.value.settings
        val newItems = downloaded.map { item ->
            val uri = Uri.fromFile(item.file)
            val preliminary = createUploadItem(uri, item.file.name, settings)
            replan(
                preliminary.copy(
                    category = BookCategory.Manga,
                    title = item.chapter.title,
                    mangaSeries = series.title,
                    mangaVolume = item.chapter.title,
                    extension = item.file.extension.lowercase().ifBlank { "cbz" },
                ),
                settings,
            )
        }

        _state.update { current ->
            val existing = current.queue.queueIdentityKeys()
            val uniqueItems = newItems.filterNot { item -> item.sourceUri in existing }
            current.copy(queue = (current.queue + uniqueItems).deduplicateQueue(), errorMessage = null)
        }

        newItems.forEach { item ->
            viewModelScope.launch {
                loadMetadata(item)
            }
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

    private fun clearDirectory(directory: File): Long {
        if (!directory.exists()) return 0L

        var deletedBytes = 0L
        directory.listFiles().orEmpty().forEach { file ->
            deletedBytes += file.sizeRecursively()
            file.deleteRecursively()
        }
        directory.mkdirs()
        return deletedBytes
    }

    private fun File.sizeRecursively(): Long {
        if (!exists()) return 0L
        if (isFile) return length()
        return listFiles().orEmpty().sumOf { file -> file.sizeRecursively() }
    }

    private fun Long.formatBytes(): String {
        val units = listOf("B", "KB", "MB", "GB")
        var value = toDouble()
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex += 1
        }
        return if (unitIndex == 0) {
            "${value.toLong()} ${units[unitIndex]}"
        } else {
            "%.1f %s".format(value, units[unitIndex])
        }
    }

    private suspend fun loadMetadata(item: UploadItem) {
        val metadata = metadataExtractor.extract(item.sourceUri, item.originalName)

        _state.update { state ->
            val queue = state.queue.map { current ->
                if (current.id != item.id || current.status != UploadStatus.Preparing) {
                    current
                } else {
                    val title = metadata.title.ifBlank { current.title }
                    val author = metadata.authors
                        .joinToString(", ")
                        .ifBlank { current.author.orEmpty() }
                        .ifBlank { null }
                    val updated = current.copy(
                        title = title,
                        author = if (current.category == BookCategory.Books) author else current.author,
                        mangaSeries = if (current.category == BookCategory.Manga) {
                            metadata.series ?: current.mangaSeries
                        } else {
                            current.mangaSeries
                        },
                        coverUri = metadata.coverUri,
                        preview = metadata.preview,
                        status = UploadStatus.Pending,
                    )
                    replan(updated, state.settings)
                }
            }

            state.copy(queue = queue.deduplicateQueue())
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
            listOf(item.id, item.sourceUri, item.plannedPath)
        }
            .filter { key -> key.isNotBlank() }
            .toSet()

    private fun Throwable.toFtpConnectionMessage(device: PocketBookDevice): String {
        val reason = message
            ?.lineSequence()
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "FTP is unavailable"
        return "Cannot connect to ${device.host}:${device.port}: $reason"
    }

    private fun updateItem(id: String, item: UploadItem) {
        _state.update { state ->
            state.copy(queue = state.queue.map { if (it.id == id) item else it }.deduplicateQueue())
        }
    }

    private fun refreshRemoteSuggestions(device: PocketBookDevice) {
        viewModelScope.launch {
            val programmingTags = ftpGateway.listDirectories(device, "Programming").getOrDefault(emptyList())
            val mangaSeries = ftpGateway.listDirectories(device, "Manga").getOrDefault(emptyList())

            _state.update {
                it.copy(
                    programmingTags = programmingTags,
                    mangaSeriesSuggestions = mangaSeries,
                )
            }
        }
    }

    private fun refreshDeviceCatalog(device: PocketBookDevice) {
        viewModelScope.launch {
            _state.update {
                it.copy(deviceCatalog = it.deviceCatalog.copy(isLoading = true, errorMessage = null))
            }

            val result = runCatching {
                val books = loadGroupedFiles(device, "Books")
                val programming = loadGroupedFiles(device, "Programming")
                val manga = loadMangaSeries(device, "Manga")

                DeviceCatalog(
                    books = books,
                    programming = programming,
                    manga = manga,
                    scannedAtMillis = System.currentTimeMillis(),
                    isLoading = false,
                    errorMessage = null,
                )
            }

            _state.update { state ->
                result.fold(
                    onSuccess = { catalog ->
                        state.copy(
                            deviceCatalog = catalog,
                            programmingTags = catalog.programming.map(CatalogGroup::name),
                            mangaSeriesSuggestions = catalog.manga.map(MangaSeriesGroup::name),
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            deviceCatalog = state.deviceCatalog.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Cannot read PocketBook catalog",
                            ),
                        )
                    },
                )
            }
        }
    }

    private suspend fun loadGroupedFiles(
        device: PocketBookDevice,
        root: String,
    ): List<CatalogGroup> {
        return ftpGateway.listEntries(device, root)
            .getOrDefault(emptyList())
            .filter(FtpEntry::isDirectory)
            .mapNotNull { group ->
                val files = ftpGateway.listEntries(device, group.path)
                    .getOrDefault(emptyList())
                    .filterNot(FtpEntry::isDirectory)
                    .filter { it.isKnownBookFile() }
                    .map { it.toCatalogFile() }
                    .sortedWith(NaturalSort.by { it.name })

                if (files.isEmpty()) {
                    null
                } else {
                    CatalogGroup(
                        name = group.name,
                        path = group.path,
                        files = files,
                    )
                }
            }
            .sortedWith(NaturalSort.by { it.name })
    }

    private suspend fun loadMangaSeries(
        device: PocketBookDevice,
        root: String,
    ): List<MangaSeriesGroup> {
        return ftpGateway.listEntries(device, root)
            .getOrDefault(emptyList())
            .filter(FtpEntry::isDirectory)
            .mapNotNull { series ->
                val files = ftpGateway.listEntries(device, series.path)
                    .getOrDefault(emptyList())
                    .filterNot(FtpEntry::isDirectory)
                    .filter { it.name.contentExtension() in MangaExtensions }
                    .map { it.toCatalogFile() }
                    .sortedWith(NaturalSort.by { it.name })

                if (files.isEmpty()) {
                    null
                } else {
                    MangaSeriesGroup(
                        name = series.name,
                        path = series.path,
                        latestFile = files.maxWithOrNull(
                            compareBy<CatalogFile, String>(
                                NaturalSort.by { it },
                                CatalogFile::name,
                            ),
                        ) ?: files.maxByOrNull { it.modifiedAtMillis ?: 0L },
                        files = files,
                    )
                }
            }
            .sortedWith(NaturalSort.by { it.name })
    }

    private fun FtpEntry.toCatalogFile(): CatalogFile =
        CatalogFile(
            name = name,
            path = path,
            size = size,
            modifiedAtMillis = modifiedAtMillis,
        )

    private fun FtpEntry.isKnownBookFile(): Boolean =
        name.contentExtension() in BookExtensions

    private fun OpdsEntry.bestAcquisition(): OpdsAcquisition? =
        acquisitions.minByOrNull { acquisition ->
            val type = acquisition.type.orEmpty().lowercase()
            when {
                type.contains("comicbook+zip") || type.contains("comicbook-rar") -> 0
                acquisition.href.contentExtension() in MangaExtensions -> 0
                type.contains("fb2") || acquisition.href.bookExtension() == "fb2.zip" -> 1
                type.contains("epub") || acquisition.href.contentExtension() == "epub" -> 2
                type.contains("mobi") || acquisition.href.contentExtension() == "mobi" -> 3
                type.contains("pdf") || acquisition.href.contentExtension() == "pdf" -> 4
                else -> 10
            }
        }

    private companion object {
        val BookExtensions = setOf(
            "epub",
            "fb2",
            "mobi",
            "azw3",
            "pdf",
            "djvu",
            "txt",
            "rtf",
            "cbz",
            "cbr",
        )
        val MangaExtensions = setOf("cbz", "cbr")
        const val StatusMessageMillis = 5_000L
        const val OpdsStatusMessageMillis = 2_300L
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
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

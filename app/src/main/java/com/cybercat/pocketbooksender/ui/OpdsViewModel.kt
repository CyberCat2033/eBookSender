package com.cybercat.pocketbooksender.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybercat.pocketbooksender.data.opds.OpdsAcquisition
import com.cybercat.pocketbooksender.data.opds.OpdsCatalog
import com.cybercat.pocketbooksender.data.opds.OpdsEntry
import com.cybercat.pocketbooksender.data.opds.OpdsLink
import com.cybercat.pocketbooksender.data.opds.OpdsRepository
import com.cybercat.pocketbooksender.data.opds.supportedDownloadFormat
import com.cybercat.pocketbooksender.data.settings.SettingsRepository
import com.cybercat.pocketbooksender.transfer.UploadQueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OpdsViewModel @Inject constructor(
    private val opdsRepository: OpdsRepository,
    private val settingsRepository: SettingsRepository,
    private val queueManager: UploadQueueManager,
    private val localizationManager: com.cybercat.pocketbooksender.localization.LocalizationManager,
) : ViewModel() {

    private val _opdsState = MutableStateFlow(OpdsUiState())
    private var initialCatalogLoadRequested = false

    val uiState: StateFlow<OpdsUiState> = combine(
        opdsRepository.sources,
        _opdsState
    ) { sources, opdsState ->
        opdsState.copy(sources = sources)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OpdsUiState()
    )

    init {
        viewModelScope.launch {
            opdsRepository.seedDefaultsIfNeeded()
        }

        // Auto-fill OPDS inputs if sources change
        opdsRepository.sources
            .onEach { sources ->
                if (sources.isEmpty()) return@onEach

                val firstSource = sources.first()
                if (_opdsState.value.urlInput.isBlank()) {
                    _opdsState.update { state ->
                        state.copy(
                            urlInput = firstSource.url,
                            titleInput = firstSource.title,
                        )
                    }
                }

                val snapshot = _opdsState.value
                if (!initialCatalogLoadRequested && snapshot.currentUrl == null && snapshot.catalog == null) {
                    initialCatalogLoadRequested = true
                    loadOpdsCatalog(url = firstSource.url, history = emptyList())
                }
            }
            .launchIn(viewModelScope)
    }

    fun onUrlInputChanged(value: String) {
        _opdsState.update { it.copy(urlInput = value) }
    }

    fun onTitleInputChanged(value: String) {
        _opdsState.update { it.copy(titleInput = value) }
    }

    fun onSearchInputChanged(value: String) {
        _opdsState.update { it.copy(searchInput = value) }
    }

    fun setWebContentMode(mode: WebContentMode) {
        _opdsState.update { it.copy(webMode = mode) }
    }

    fun clearError() {
        _opdsState.update { it.copy(errorMessage = null) }
    }

    fun saveOpdsSource(title: String, url: String, username: String? = null, password: String? = null) {
        viewModelScope.launch {
            runCatching {
                opdsRepository.addSource(
                    title = title.trim(),
                    url = url.trim(),
                    username = username?.trim()?.ifBlank { null },
                    password = password?.trim()?.ifBlank { null },
                )
            }.onSuccess {
                showOpdsStatus(localizationManager.currentStrings.value.opdsStatusSourceSaved)
            }.onFailure { error ->
                _opdsState.update { state ->
                    state.copy(
                        errorMessage = error.message ?: localizationManager.currentStrings.value.opdsErrorCannotSaveSource,
                        statusMessage = null,
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
        openOpdsUrl(_opdsState.value.urlInput)
    }

    fun openOpdsUrl(url: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            _opdsState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value.opdsErrorUrlEmpty,
                    statusMessage = null,
                )
            }
            return
        }

        val previous = _opdsState.value
        loadOpdsCatalog(
            url = trimmedUrl,
            history = previous.currentUrl?.let { currentUrl ->
                previous.history + OpdsHistoryEntry(
                    title = previous.catalog?.title ?: localizationManager.currentStrings.value.opdsHistoryFallbackTitle,
                    url = currentUrl,
                )
            } ?: previous.history,
        )
    }

    fun openOpdsLink(link: OpdsLink) {
        openOpdsUrl(link.href)
    }

    fun goBackOpds() {
        val snapshot = _opdsState.value
        val previous = snapshot.history.lastOrNull() ?: return
        loadOpdsCatalog(
            url = previous.url,
            history = snapshot.history.dropLast(1),
        )
    }

    fun searchOpds() {
        val snapshot = _opdsState.value
        val query = snapshot.searchInput.trim()
        val currentUrl = snapshot.currentUrl
        val catalog = snapshot.catalog

        if (query.isBlank()) {
            _opdsState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value.opdsErrorSearchEmpty,
                    statusMessage = null,
                )
            }
            return
        }

        if (currentUrl == null || catalog == null) {
            _opdsState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value.opdsErrorOpenCatalogFirst,
                    statusMessage = null,
                )
            }
            return
        }

        val searchLink = catalog.links.firstOrNull { link ->
            link.rel.orEmpty().equals("search", ignoreCase = true)
        }

        if (searchLink == null) {
            _opdsState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value.opdsErrorNoSearchSupport,
                    statusMessage = null,
                )
            }
            return
        }

        viewModelScope.launch {
            _opdsState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = null,
                    catalog = null,
                )
            }

            runCatching {
                val searchUrls = opdsRepository.buildSearchUrls(currentUrl, searchLink, query)
                val catalogs = searchUrls.mapNotNull { searchUrl ->
                    runCatching { loadOpdsSearchCatalog(searchUrl, query) }.getOrNull()
                }
                if (catalogs.isEmpty()) {
                    throw IllegalStateException(localizationManager.currentStrings.value.opdsErrorCannotOpenSearch)
                }
                catalogs
            }.onSuccess { catalogs ->
                _opdsState.update { state ->
                    state.copy(
                        currentUrl = catalogs.first().first,
                        urlInput = catalogs.first().first,
                        catalog = mergeSearchCatalogs(query, catalogs.map { (_, catalog) -> catalog }),
                        history = snapshot.history + OpdsHistoryEntry(
                            title = catalog.title,
                            url = currentUrl,
                        ),
                        isLoading = false,
                        errorMessage = null,
                        statusMessage = null,
                    )
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                _opdsState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = error.message ?: localizationManager.currentStrings.value.opdsErrorCannotBuildSearchUrl,
                        statusMessage = null,
                    )
                }
            }
        }
    }

    fun downloadOpdsAcquisition(
        entry: OpdsEntry,
        acquisition: OpdsAcquisition,
    ) {
        val baseUrl = _opdsState.value.currentUrl
        if (baseUrl == null) {
            _opdsState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value.opdsErrorOpenCatalogFirst,
                    statusMessage = null,
                )
            }
            return
        }

        viewModelScope.launch {
            _opdsState.update {
                it.copy(
                    isDownloading = true,
                    errorMessage = null,
                    statusMessage = null,
                )
            }

            runCatching {
                opdsRepository.downloadPublication(baseUrl, entry, acquisition)
            }.onSuccess { file ->
                queueManager.addUris(listOf(Uri.fromFile(file)))
                _opdsState.update { state ->
                    state.copy(isDownloading = false)
                }
                showOpdsStatus(localizationManager.currentStrings.value.get("opds_status_added_to_queue", file.name))
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                _opdsState.update { state ->
                    state.copy(
                        isDownloading = false,
                        errorMessage = error.message ?: localizationManager.currentStrings.value.opdsErrorCannotDownload,
                        statusMessage = null,
                    )
                }
            }
        }
    }

    fun downloadOpdsEntries(entries: List<OpdsEntry>) {
        val baseUrl = _opdsState.value.currentUrl
        if (baseUrl == null) {
            _opdsState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value.opdsErrorOpenCatalogFirst,
                    statusMessage = null,
                )
            }
            return
        }

        val downloadable = entries.mapNotNull { entry ->
            entry.bestAcquisition()?.let { acquisition -> entry to acquisition }
        }
        if (downloadable.isEmpty()) {
            _opdsState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value.opdsErrorNoDownloadableEntries,
                    statusMessage = null,
                )
            }
            return
        }

        viewModelScope.launch {
            _opdsState.update {
                it.copy(
                    isDownloading = true,
                    errorMessage = null,
                    statusMessage = null,
                )
            }

            data class DownloadOutcome(val file: File?, val failed: Boolean)
            val outcomes = coroutineScope {
                downloadable.map { (entry, acquisition) ->
                    async {
                        runCatching {
                            opdsRepository.downloadPublication(baseUrl, entry, acquisition)
                        }.fold(
                            onSuccess = { file -> DownloadOutcome(file = file, failed = false) },
                            onFailure = { DownloadOutcome(file = null, failed = true) },
                        )
                    }
                }.awaitAll()
            }

            val downloadedFiles = outcomes.mapNotNull { it.file }
            val failedCount = outcomes.count { it.failed }

            if (downloadedFiles.isNotEmpty()) {
                queueManager.addUris(downloadedFiles.map { file -> Uri.fromFile(file) })
            }

            _opdsState.update { state ->
                state.copy(
                    isDownloading = false,
                    errorMessage = if (failedCount > 0) {
                        localizationManager.currentStrings.value.get("opds_error_failed_to_download_entries", failedCount)
                    } else {
                        null
                    },
                )
            }

            if (downloadedFiles.isNotEmpty()) {
                showOpdsStatus(localizationManager.currentStrings.value.get("opds_status_added_to_queue_multiple", downloadedFiles.size))
            }
        }
    }

    private fun loadOpdsCatalog(url: String, history: List<OpdsHistoryEntry>) {
        viewModelScope.launch {
            _opdsState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = null,
                    catalog = null,
                )
            }

            runCatching {
                opdsRepository.loadCatalog(url)
            }.onSuccess { catalog ->
                _opdsState.update { state ->
                    state.copy(
                        currentUrl = url,
                        urlInput = url,
                        catalog = catalog,
                        history = history,
                        isLoading = false,
                        errorMessage = null,
                        statusMessage = null,
                    )
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                _opdsState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = error.message ?: localizationManager.currentStrings.value.opdsErrorCannotOpenCatalog,
                        statusMessage = null,
                    )
                }
            }
        }
    }

    private suspend fun loadOpdsSearchCatalog(
        searchUrl: String,
        query: String,
    ): Pair<String, OpdsCatalog> {
        val catalog = opdsRepository.loadCatalog(searchUrl)
        if (!searchUrl.contains("/opds/authorsindex/", ignoreCase = true)) {
            return searchUrl to catalog
        }

        val authorLink = catalog.entries
            .singleOrNull()
            ?.navigation
            ?.firstOrNull { link -> link.href.contains("/opds/authors/", ignoreCase = true) }

        if (authorLink == null) {
            return searchUrl to catalog.filterAuthorEntries(query)
        }

        val expandedCatalog = opdsRepository.loadCatalog(authorLink.href).filterAuthorEntries(query)
        return authorLink.href to expandedCatalog
    }

    private fun mergeSearchCatalogs(
        query: String,
        catalogs: List<OpdsCatalog>,
    ): OpdsCatalog {
        if (catalogs.size == 1) return catalogs.first()

        return OpdsCatalog(
            title = "Search: $query",
            entries = catalogs.flatMap { catalog -> catalog.entries },
            links = catalogs
                .flatMap { catalog -> catalog.links }
                .distinctBy { link ->
                    listOf(link.href, link.rel.orEmpty(), link.type.orEmpty()).joinToString("|")
                },
        )
    }

    private fun OpdsCatalog.filterAuthorEntries(query: String): OpdsCatalog {
        val tokens = query.searchTokens()
        if (tokens.size < 2) return this

        val filteredEntries = entries.filter { entry ->
            val title = entry.title.searchComparableText()
            tokens.all { token -> token in title }
        }

        return if (filteredEntries.isEmpty()) {
            this
        } else {
            copy(entries = filteredEntries)
        }
    }

    private fun showOpdsStatus(message: String) {
        showTemporaryStatus(
            delayMillis = OpdsStatusMessageMillis,
            setMessage = { msg ->
                _opdsState.update { state ->
                    state.copy(statusMessage = msg, errorMessage = null)
                }
            },
            clearIfStillCurrent = { msg ->
                _opdsState.update { state ->
                    if (state.statusMessage == msg) {
                        state.copy(statusMessage = null)
                    } else {
                        state
                    }
                }
            },
            message = message,
        )
    }

    private fun showTemporaryStatus(
        delayMillis: Long,
        setMessage: (String) -> Unit,
        clearIfStillCurrent: (String) -> Unit,
        message: String,
    ) {
        setMessage(message)
        viewModelScope.launch {
            delay(delayMillis)
            clearIfStillCurrent(message)
        }
    }

    private fun String.searchTokens(): List<String> =
        trim()
            .replace(Regex("[^\\p{L}\\p{N}\\s-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .lowercase()
            .split(' ')
            .map { token -> token.trim('-', ' ') }
            .filter { token -> token.length >= 2 }

    private fun String.searchComparableText(): String =
        searchTokens().joinToString(" ")

    private fun OpdsEntry.bestAcquisition(): OpdsAcquisition? =
        acquisitions.mapNotNull { acquisition ->
            acquisition.supportedDownloadFormat()?.let { format -> acquisition to format.priority }
        }.minByOrNull { (_, priority) ->
            priority
        }?.first

    private companion object {
        const val OpdsStatusMessageMillis = 2300L
    }
}

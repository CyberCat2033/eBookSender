package com.cybercat.pocketbooksender.feature.opds

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybercat.pocketbooksender.data.opds.DownloadOpdsEntriesUseCase
import com.cybercat.pocketbooksender.data.opds.MatchOpdsAuthSourceUseCase
import com.cybercat.pocketbooksender.data.opds.OpdsAcquisition
import com.cybercat.pocketbooksender.data.opds.OpdsAuthenticationRequiredException
import com.cybercat.pocketbooksender.data.opds.OpdsCatalog
import com.cybercat.pocketbooksender.data.opds.OpdsEntry
import com.cybercat.pocketbooksender.data.opds.OpdsLink
import com.cybercat.pocketbooksender.data.opds.OpdsRepository
import com.cybercat.pocketbooksender.data.opds.OpdsSearchCatalogUnavailableException
import com.cybercat.pocketbooksender.data.opds.SearchOpdsCatalogUseCase
import com.cybercat.pocketbooksender.data.transfer.UploadQueueManager
import com.cybercat.pocketbooksender.util.launchTemporaryStatus
import com.cybercat.pocketbooksender.util.onFailureRethrowing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OpdsViewModel @Inject constructor(
    private val opdsRepository: OpdsRepository,
    private val downloadOpdsEntriesUseCase: DownloadOpdsEntriesUseCase,
    private val searchOpdsCatalogUseCase: SearchOpdsCatalogUseCase,
    private val matchOpdsAuthSource: MatchOpdsAuthSourceUseCase,
    private val queueManager: UploadQueueManager,
    private val localizationManager: com.cybercat.pocketbooksender.localization.LocalizationManager
) : ViewModel() {

    private val mutableOpdsState = MutableStateFlow(OpdsUiState())
    private var initialCatalogLoadRequested = false

    val uiState: StateFlow<OpdsUiState> = combine(
        opdsRepository.sources,
        mutableOpdsState
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
                if (mutableOpdsState.value.urlInput.isBlank()) {
                    mutableOpdsState.update { state ->
                        state.copy(
                            urlInput = firstSource.url,
                            titleInput = firstSource.title
                        )
                    }
                }

                val snapshot = mutableOpdsState.value
                if (!initialCatalogLoadRequested && snapshot.currentUrl == null &&
                    snapshot.catalog == null
                ) {
                    initialCatalogLoadRequested = true
                    loadOpdsCatalog(url = firstSource.url, history = emptyList())
                }
            }
            .launchIn(viewModelScope)
    }

    fun onUrlInputChanged(value: String) {
        mutableOpdsState.update { it.copy(urlInput = value) }
    }

    fun onTitleInputChanged(value: String) {
        mutableOpdsState.update { it.copy(titleInput = value) }
    }

    fun onSearchInputChanged(value: String) {
        mutableOpdsState.update { it.copy(searchInput = value) }
    }

    fun setWebContentMode(mode: WebContentMode) {
        mutableOpdsState.update { it.copy(webMode = mode) }
    }

    fun clearError() {
        mutableOpdsState.update { it.copy(errorMessage = null) }
    }

    fun saveOpdsSource(
        title: String,
        url: String,
        username: String? = null,
        password: String? = null
    ) {
        viewModelScope.launch {
            runCatching {
                opdsRepository.addSource(
                    title = title.trim(),
                    url = url.trim(),
                    username = username?.trim()?.ifBlank { null },
                    password = password?.trim()?.ifBlank { null }
                )
            }.onSuccess {
                showOpdsStatus(localizationManager.currentStrings.value.opdsStatusSourceSaved)
            }.onFailure { error ->
                mutableOpdsState.update { state ->
                    state.copy(
                        errorMessage =
                            error.message
                                ?: localizationManager.currentStrings.value
                                    .opdsErrorCannotSaveSource,
                        statusMessage = null
                    )
                }
            }
        }
    }

    fun openCredentialsDialog(
        source: com.cybercat.pocketbooksender.data.opds.OpdsSource,
        urlToRetry: String? = null
    ) {
        mutableOpdsState.update { state ->
            state.copy(
                showAuthDialog = true,
                authDialogSourceId = source.id,
                authDialogSourceTitle = source.title,
                authDialogUsername = source.username.orEmpty(),
                authDialogPassword = source.password.orEmpty(),
                authDialogUrlToRetry = urlToRetry
            )
        }
    }

    fun onAuthUsernameChanged(value: String) {
        mutableOpdsState.update { it.copy(authDialogUsername = value) }
    }

    fun onAuthPasswordChanged(value: String) {
        mutableOpdsState.update { it.copy(authDialogPassword = value) }
    }

    fun dismissCredentialsDialog() {
        mutableOpdsState.update { state ->
            state.copy(
                showAuthDialog = false,
                authDialogSourceId = null,
                authDialogUrlToRetry = null
            )
        }
    }

    fun saveCredentials() {
        val snapshot = mutableOpdsState.value
        val sourceId = snapshot.authDialogSourceId ?: return
        val username = snapshot.authDialogUsername
        val password = snapshot.authDialogPassword
        val urlToRetry = snapshot.authDialogUrlToRetry

        viewModelScope.launch {
            val source =
                opdsRepository.sources.first().firstOrNull { it.id == sourceId } ?: return@launch
            runCatching {
                opdsRepository.addSource(
                    title = source.title,
                    url = source.url,
                    username = username.trim().ifBlank { null },
                    password = password.trim().ifBlank { null }
                )
            }.onSuccess {
                mutableOpdsState.update {
                    it.copy(
                        showAuthDialog = false,
                        authDialogSourceId = null,
                        authDialogUrlToRetry = null
                    )
                }
                showOpdsStatus(
                    localizationManager.currentStrings.value.opdsStatusCredentialsUpdated
                )
                if (urlToRetry != null) {
                    loadOpdsCatalog(urlToRetry, snapshot.history)
                }
            }.onFailure { error ->
                mutableOpdsState.update { state ->
                    state.copy(
                        errorMessage =
                            error.message
                                ?: localizationManager.currentStrings.value
                                    .opdsErrorCannotSaveCredentials,
                        showAuthDialog = false,
                        authDialogSourceId = null,
                        authDialogUrlToRetry = null
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
        openOpdsUrl(mutableOpdsState.value.urlInput)
    }

    fun openOpdsSource(url: String) {
        val trimmedUrl = validateOpdsUrl(url) ?: return
        openOpdsUrl(
            url = trimmedUrl,
            history = emptyList(),
            paging = OpdsPagingState()
        )
    }

    fun openOpdsUrl(url: String) {
        val trimmedUrl = validateOpdsUrl(url) ?: return
        val previous = mutableOpdsState.value
        openOpdsUrl(
            url = trimmedUrl,
            history = previous.currentUrl?.let { currentUrl ->
                previous.history + OpdsHistoryEntry(
                    title =
                        previous.catalog?.title
                            ?: localizationManager.currentStrings.value.opdsHistoryFallbackTitle,
                    url = currentUrl,
                    paging = previous.paging.toSnapshot()
                )
            } ?: previous.history,
            paging = OpdsPagingState()
        )
    }

    private fun openOpdsUrl(url: String, history: List<OpdsHistoryEntry>, paging: OpdsPagingState) {
        loadOpdsCatalog(
            url = url,
            history = history,
            paging = paging
        )
    }

    fun openOpdsLink(link: OpdsLink) {
        when {
            link.isNextLink() -> goNextOpdsPage()

            link.isPreviousLink() -> goPreviousOpdsPage()

            link.isStartLink() -> {
                openOpdsUrl(
                    url = link.href,
                    history = emptyList(),
                    paging = OpdsPagingState()
                )
            }

            else -> openOpdsUrl(link.href)
        }
    }

    private fun validateOpdsUrl(url: String): String? {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isNotBlank()) return trimmedUrl

        mutableOpdsState.update {
            it.copy(
                errorMessage = localizationManager.currentStrings.value.opdsErrorUrlEmpty,
                statusMessage = null
            )
        }
        return null
    }

    fun goBackOpds() {
        val snapshot = mutableOpdsState.value
        val previous = snapshot.history.lastOrNull() ?: return
        loadOpdsCatalog(
            url = previous.url,
            history = snapshot.history.dropLast(1),
            paging = previous.paging.toPagingState()
        )
    }

    fun goPreviousOpdsPage() {
        val snapshot = mutableOpdsState.value
        val previousPage = snapshot.paging.previousPages.lastOrNull() ?: return
        loadOpdsCatalog(
            url = previousPage.url,
            history = snapshot.history,
            paging = snapshot.paging.copy(
                currentPage = (snapshot.paging.currentPage - 1).coerceAtLeast(1),
                previousPages = snapshot.paging.previousPages.dropLast(1),
                nextLink = null
            )
        )
    }

    fun goNextOpdsPage() {
        val snapshot = mutableOpdsState.value
        val nextLink = snapshot.paging.nextLink ?: return
        val currentPage = snapshot.currentPageHistoryEntry() ?: return
        loadOpdsCatalog(
            url = nextLink.href,
            history = snapshot.history,
            paging = snapshot.paging.copy(
                currentPage = snapshot.paging.currentPage + 1,
                previousPages = snapshot.paging.previousPages + currentPage,
                nextLink = null
            )
        )
    }

    fun searchOpds() {
        val snapshot = mutableOpdsState.value
        val query = snapshot.searchInput.trim()
        val currentUrl = snapshot.currentUrl
        val catalog = snapshot.catalog

        if (query.isBlank()) {
            mutableOpdsState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value.opdsErrorSearchEmpty,
                    statusMessage = null
                )
            }
            return
        }

        if (currentUrl == null || catalog == null) {
            mutableOpdsState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value
                        .opdsErrorOpenCatalogFirst,
                    statusMessage = null
                )
            }
            return
        }

        val searchLink = catalog.links.firstOrNull { link ->
            link.rel.orEmpty().equals("search", ignoreCase = true)
        }

        if (searchLink == null) {
            mutableOpdsState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value
                        .opdsErrorNoSearchSupport,
                    statusMessage = null
                )
            }
            return
        }

        viewModelScope.launch {
            mutableOpdsState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = null,
                    catalog = null
                )
            }

            val strings = localizationManager.currentStrings.value
            searchOpdsCatalogUseCase(
                baseUrl = currentUrl,
                searchLink = searchLink,
                query = query,
                mergedCatalogTitle = strings.get("opds_search_results_title", query)
            ).onSuccess { result ->
                mutableOpdsState.update { state ->
                    state.copy(
                        currentUrl = result.currentUrl,
                        urlInput = result.currentUrl,
                        catalog = result.catalog,
                        history = snapshot.history + OpdsHistoryEntry(
                            title = catalog.title,
                            url = currentUrl,
                            paging = snapshot.paging.toSnapshot()
                        ),
                        paging = OpdsPagingState().withCatalogLinks(result.catalog),
                        isLoading = false,
                        errorMessage = null,
                        statusMessage = null
                    )
                }
            }.onFailureRethrowing { error ->
                if (error is OpdsAuthenticationRequiredException) {
                    val matchingSource = matchOpdsAuthSource(error)
                    if (matchingSource != null) {
                        mutableOpdsState.update { state ->
                            state.copy(
                                isLoading = false,
                                catalog = snapshot.catalog,
                                currentUrl = snapshot.currentUrl,
                                history = snapshot.history,
                                paging = snapshot.paging
                            )
                        }
                        openCredentialsDialog(matchingSource, urlToRetry = error.url)
                        return@launch
                    }
                }
                mutableOpdsState.update { state -> state.copy(isLoading = false) }
                val message = when (error) {
                    is OpdsSearchCatalogUnavailableException ->
                        localizationManager.currentStrings.value.opdsErrorCannotOpenSearch

                    else ->
                        error.message
                            ?: localizationManager.currentStrings.value
                                .opdsErrorCannotBuildSearchUrl
                }
                showOpdsError(message)
            }
        }
    }

    fun downloadOpdsAcquisition(entry: OpdsEntry, acquisition: OpdsAcquisition) {
        val baseUrl = mutableOpdsState.value.currentUrl
        if (baseUrl == null) {
            mutableOpdsState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value
                        .opdsErrorOpenCatalogFirst,
                    statusMessage = null
                )
            }
            return
        }

        viewModelScope.launch {
            mutableOpdsState.update {
                it.copy(
                    isDownloading = true,
                    errorMessage = null,
                    statusMessage = null
                )
            }

            runCatching {
                opdsRepository.downloadPublication(baseUrl, entry, acquisition)
            }.onSuccess { file ->
                queueManager.addUris(listOf(Uri.fromFile(file)))
                mutableOpdsState.update { state ->
                    state.copy(isDownloading = false)
                }
                showOpdsStatus(
                    localizationManager.currentStrings.value.get(
                        "opds_status_added_to_queue",
                        file.name
                    )
                )
            }.onFailureRethrowing { error ->
                if (error is OpdsAuthenticationRequiredException) {
                    val matchingSource = matchOpdsAuthSource(error)
                    if (matchingSource != null) {
                        mutableOpdsState.update { state -> state.copy(isDownloading = false) }
                        openCredentialsDialog(matchingSource)
                        return@launch
                    }
                }
                mutableOpdsState.update { state -> state.copy(isDownloading = false) }
                showOpdsError(
                    error.message
                        ?: localizationManager.currentStrings.value.opdsErrorCannotDownload
                )
            }
        }
    }

    fun downloadOpdsEntries(entries: List<OpdsEntry>) {
        val baseUrl = mutableOpdsState.value.currentUrl
        if (baseUrl == null) {
            mutableOpdsState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value
                        .opdsErrorOpenCatalogFirst,
                    statusMessage = null
                )
            }
            return
        }

        if (!downloadOpdsEntriesUseCase.hasDownloadableEntries(entries)) {
            mutableOpdsState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value
                        .opdsErrorNoDownloadableEntries,
                    statusMessage = null
                )
            }
            return
        }

        viewModelScope.launch {
            mutableOpdsState.update {
                it.copy(
                    isDownloading = true,
                    errorMessage = null,
                    statusMessage = null
                )
            }

            downloadOpdsEntriesUseCase(baseUrl, entries)
                .onSuccess { result ->
                    if (result.downloadedFiles.isNotEmpty()) {
                        queueManager.addUris(
                            result.downloadedFiles.map { file ->
                                Uri.fromFile(file)
                            }
                        )
                    }

                    mutableOpdsState.update { state ->
                        state.copy(
                            isDownloading = false,
                            errorMessage = if (result.failedCount > 0) {
                                localizationManager.currentStrings.value.get(
                                    "opds_error_failed_to_download_entries",
                                    result.failedCount
                                )
                            } else {
                                null
                            }
                        )
                    }

                    if (result.downloadedFiles.isNotEmpty()) {
                        showOpdsStatus(
                            localizationManager.currentStrings.value.get(
                                "opds_status_added_to_queue_multiple",
                                result.downloadedFiles.size
                            )
                        )
                    }
                }.onFailureRethrowing { error ->
                    mutableOpdsState.update { state -> state.copy(isDownloading = false) }
                    showOpdsError(
                        error.message
                            ?: localizationManager.currentStrings.value.opdsErrorCannotDownload
                    )
                }
        }
    }

    private fun loadOpdsCatalog(
        url: String,
        history: List<OpdsHistoryEntry>,
        paging: OpdsPagingState = OpdsPagingState()
    ) {
        viewModelScope.launch {
            val snapshotBeforeLoad = mutableOpdsState.value
            mutableOpdsState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = null,
                    catalog = null
                )
            }

            runCatching {
                opdsRepository.loadCatalog(url)
            }.onSuccess { catalog ->
                mutableOpdsState.update { state ->
                    state.copy(
                        currentUrl = url,
                        urlInput = url,
                        catalog = catalog,
                        history = history,
                        paging = paging.withCatalogLinks(catalog),
                        isLoading = false,
                        errorMessage = null,
                        statusMessage = null
                    )
                }
            }.onFailureRethrowing { error ->
                if (error is OpdsAuthenticationRequiredException) {
                    val matchingSource = matchOpdsAuthSource(error)
                    if (matchingSource != null) {
                        // Restore previous catalog so back button and content are preserved
                        mutableOpdsState.update { state ->
                            state.copy(
                                isLoading = false,
                                catalog = snapshotBeforeLoad.catalog,
                                currentUrl = snapshotBeforeLoad.currentUrl,
                                history = snapshotBeforeLoad.history,
                                paging = snapshotBeforeLoad.paging
                            )
                        }
                        openCredentialsDialog(matchingSource, urlToRetry = url)
                        return@launch
                    }
                }
                mutableOpdsState.update { state -> state.copy(isLoading = false) }
                showOpdsError(
                    error.message
                        ?: localizationManager.currentStrings.value.opdsErrorCannotOpenCatalog
                )
            }
        }
    }

    private fun OpdsPagingState.withCatalogLinks(catalog: OpdsCatalog): OpdsPagingState {
        val nextLink = catalog.links.firstOrNull(OpdsLink::isNextLink)
        return copy(
            nextLink = nextLink,
            totalPages =
                totalPages ?: if (nextLink == null && currentPage > 1) currentPage else null
        )
    }

    private fun OpdsUiState.currentPageHistoryEntry(): OpdsPageHistoryEntry? {
        val currentUrl = currentUrl ?: return null
        return OpdsPageHistoryEntry(
            title =
                catalog?.title ?: localizationManager.currentStrings.value.opdsHistoryFallbackTitle,
            url = currentUrl
        )
    }

    private fun showOpdsStatus(message: String) {
        viewModelScope.launchTemporaryStatus(
            message = message,
            delayMillis = OPDS_STATUS_MESSAGE_MILLIS,
            setMessage = { msg ->
                mutableOpdsState.update { state ->
                    state.copy(statusMessage = msg, errorMessage = null)
                }
            },
            clearIfStillCurrent = { msg ->
                mutableOpdsState.update { state ->
                    if (state.statusMessage == msg) state.copy(statusMessage = null) else state
                }
            }
        )
    }

    private fun showOpdsError(message: String) {
        viewModelScope.launchTemporaryStatus(
            message = message,
            delayMillis = OPDS_ERROR_MESSAGE_MILLIS,
            setMessage = { msg ->
                mutableOpdsState.update { state ->
                    state.copy(errorMessage = msg, statusMessage = null)
                }
            },
            clearIfStillCurrent = { msg ->
                mutableOpdsState.update { state ->
                    if (state.errorMessage == msg) state.copy(errorMessage = null) else state
                }
            }
        )
    }

    private companion object {
        const val OPDS_STATUS_MESSAGE_MILLIS = 2300L
        const val OPDS_ERROR_MESSAGE_MILLIS = 5500L
    }
}

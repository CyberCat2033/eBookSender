package com.cybercat.pocketbooksender.feature.manga

import com.cybercat.pocketbooksender.data.catalog.DeviceCatalogRepository
import com.cybercat.pocketbooksender.data.manga.MangaRepository
import com.cybercat.pocketbooksender.localization.LocalizationManager
import com.cybercat.pocketbooksender.util.onFailureRethrowing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class MangaSearchController(
    private val mangaRepository: MangaRepository,
    private val catalogRepository: DeviceCatalogRepository,
    private val localizationManager: LocalizationManager,
    private val mangaState: MutableStateFlow<MangaUiState>,
    private val scope: CoroutineScope,
    private val showStatus: (String) -> Unit,
    private val refreshAuthState: () -> Unit
) {
    fun onSearchChanged(value: String) {
        mangaState.update {
            it.copy(
                searchInput = value,
                errorMessage = null,
                statusMessage = null
            )
        }
    }

    fun selectSource(sourceId: String) {
        val source = mangaRepository.sources.firstOrNull { it.id == sourceId } ?: return
        mangaState.update { state ->
            state.resetSelectedSeries().copy(
                selectedSourceId = source.id,
                browserUrl = source.homeUrl,
                currentWebUrl = null,
                searchResults = emptyList(),
                errorMessage = null,
                statusMessage = null
            )
        }
        refreshAuthState()
    }

    fun goBack() {
        mangaState.update { state ->
            state.resetSelectedSeries().copy(
                errorMessage = null,
                statusMessage = null
            )
        }
    }

    fun search() {
        val snapshot = mangaState.value
        val query = snapshot.searchInput.trim()
        if (query.isBlank()) {
            mangaState.update {
                it.copy(
                    errorMessage = localizationManager.currentStrings.value.mangaErrorSearchEmpty
                )
            }
            return
        }

        mangaState.update { state ->
            state.resetSelectedSeries().copy(
                isLoading = true,
                browserVisible = false,
                errorMessage = null,
                statusMessage = null,
                searchResults = emptyList()
            )
        }

        scope.launch {
            runCatching {
                mangaRepository.searchSeries(snapshot.selectedSourceId, query)
            }.onSuccess { results ->
                mangaState.update { state ->
                    state.resetSelectedSeries().copy(
                        searchResults = results,
                        isLoading = false,
                        browserVisible = false,
                        statusMessage = null,
                        errorMessage = null
                    )
                }
                if (results.isEmpty()) {
                    showStatus(
                        localizationManager.currentStrings.value.mangaStatusNoMangaFound
                    )
                }
            }.onFailureRethrowing { error ->
                val strings = localizationManager.currentStrings.value
                mangaState.update { state ->
                    state.copy(
                        isLoading = false,
                        browserVisible = false,
                        isAuthorized = state.isAuthorized &&
                            !MangaErrorMessageMapper.isAuthenticationExpired(error),
                        errorMessage = MangaErrorMessageMapper.errorMessage(
                            error = error,
                            fallback = strings.mangaErrorCannotSearch,
                            strings = strings
                        ),
                        statusMessage = null
                    )
                }
            }
        }
    }

    fun openSeries(seriesId: String) {
        val sourceId = mangaState.value.selectedSourceId
        mangaState.update { state ->
            state.resetSelectedSeries().copy(
                isLoading = true,
                browserVisible = false,
                errorMessage = null,
                statusMessage = null
            )
        }

        scope.launch {
            runCatching {
                mangaRepository.openSeries(sourceId, seriesId)
            }.onSuccess { seriesPage ->
                val lastRead = MangaCatalogProgressFormatter.lastReadChapterText(
                    series = seriesPage.details,
                    catalog = catalogRepository.catalog.value,
                    strings = localizationManager.currentStrings.value
                )
                mangaState.update { state ->
                    state.copy(
                        selectedSeries = seriesPage.details,
                        selectedSeriesScrollRequest = state.selectedSeriesScrollRequest + 1,
                        chapters = seriesPage.chapters,
                        selectedChapterIds = emptySet(),
                        lastReadChapterText = lastRead,
                        isLoading = false,
                        browserVisible = false,
                        errorMessage = null
                    )
                }
            }.onFailureRethrowing { error ->
                val strings = localizationManager.currentStrings.value
                mangaState.update { state ->
                    state.copy(
                        isLoading = false,
                        browserVisible = false,
                        isAuthorized = state.isAuthorized &&
                            !MangaErrorMessageMapper.isAuthenticationExpired(error),
                        errorMessage = MangaErrorMessageMapper.errorMessage(
                            error = error,
                            fallback = strings.mangaErrorCannotOpenSeries,
                            strings = strings
                        ),
                        statusMessage = null
                    )
                }
            }
        }
    }
}

private fun MangaUiState.resetSelectedSeries(): MangaUiState =
    copy(
        selectedSeries = null,
        chapters = emptyList(),
        selectedChapterIds = emptySet(),
        lastReadChapterText = null
    )

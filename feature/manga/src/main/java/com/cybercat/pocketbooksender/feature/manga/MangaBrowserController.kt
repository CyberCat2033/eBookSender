package com.cybercat.pocketbooksender.feature.manga

import com.cybercat.pocketbooksender.data.manga.MangaAuthState
import com.cybercat.pocketbooksender.data.manga.MangaRepository
import com.cybercat.pocketbooksender.localization.LocalizationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class MangaBrowserController(
    private val mangaRepository: MangaRepository,
    private val localizationManager: LocalizationManager,
    private val mangaState: MutableStateFlow<MangaUiState>,
    private val scope: CoroutineScope,
    private val showStatus: (String) -> Unit
) {
    fun openBrowser(url: String? = null) {
        mangaState.update { state ->
            val homeUrl = mangaRepository.homeUrl(state.selectedSourceId)
            state.copy(
                browserVisible = true,
                browserUrl = url ?: homeUrl,
                currentWebUrl = state.currentWebUrl ?: homeUrl
            )
        }
    }

    fun closeBrowser() {
        mangaState.update { state ->
            state.copy(browserVisible = false)
        }
        refreshAuthState()
    }

    fun performNativeLogin(
        targetUrl: String,
        username: String,
        password: String,
        doNotRemember: Boolean
    ) {
        val sourceId = mangaState.value.selectedSourceId
        val postBody = mangaRepository.buildLoginPostBody(
            sourceId = sourceId,
            username = username,
            password = password,
            doNotRemember = doNotRemember
        )
        if (postBody != null) {
            mangaState.update { state ->
                state.copy(
                    pendingLoginPost = MangaPendingLoginPost(targetUrl, postBody)
                )
            }
        }
    }

    fun clearPendingLoginPost() {
        mangaState.update { state ->
            state.copy(pendingLoginPost = null)
        }
    }

    fun syncWebPage(url: String) {
        mangaState.update { state ->
            state.copy(
                currentWebUrl = url,
                errorMessage = null
            )
        }
        refreshAuthState(closeBrowserOnAuthenticated = true)
    }

    fun refreshAuthState(closeBrowserOnAuthenticated: Boolean = false) {
        scope.launch {
            val sourceId = mangaState.value.selectedSourceId
            val authState = mangaRepository.authState(sourceId)
            val isAuth =
                authState is MangaAuthState.Authenticated || authState is MangaAuthState.NotRequired
            var shouldShowLoginSuccess = false
            mangaState.update { state ->
                val closeBrowser = closeBrowserOnAuthenticated &&
                    authState is MangaAuthState.Authenticated &&
                    state.browserVisible &&
                    !state.isAuthorized
                if (closeBrowser) {
                    shouldShowLoginSuccess = true
                }
                state.copy(
                    isAuthorized = isAuth,
                    browserVisible = if (closeBrowser) false else state.browserVisible
                )
            }
            if (shouldShowLoginSuccess) {
                showStatus(localizationManager.currentStrings.value.mangaStatusLoginSuccess)
            }
        }
    }
}

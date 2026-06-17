package com.cybercat.pocketbooksender.feature.opds

import com.cybercat.pocketbooksender.data.opds.OpdsRepository
import com.cybercat.pocketbooksender.data.opds.OpdsSource
import com.cybercat.pocketbooksender.localization.LocalizationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class OpdsAuthController(
    private val opdsRepository: OpdsRepository,
    private val localizationManager: LocalizationManager,
    private val opdsState: MutableStateFlow<OpdsUiState>,
    private val scope: CoroutineScope,
    private val showStatus: (String) -> Unit,
    private val loadCatalog: (String, List<OpdsHistoryEntry>) -> Unit
) {
    fun openCredentialsDialog(source: OpdsSource, urlToRetry: String? = null) {
        opdsState.update { state ->
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
        opdsState.update { it.copy(authDialogUsername = value) }
    }

    fun onAuthPasswordChanged(value: String) {
        opdsState.update { it.copy(authDialogPassword = value) }
    }

    fun dismissCredentialsDialog() {
        opdsState.update { state ->
            state.copy(
                showAuthDialog = false,
                authDialogSourceId = null,
                authDialogUrlToRetry = null
            )
        }
    }

    fun saveCredentials() {
        val snapshot = opdsState.value
        val sourceId = snapshot.authDialogSourceId ?: return
        val username = snapshot.authDialogUsername
        val password = snapshot.authDialogPassword
        val urlToRetry = snapshot.authDialogUrlToRetry

        scope.launch {
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
                opdsState.update {
                    it.copy(
                        showAuthDialog = false,
                        authDialogSourceId = null,
                        authDialogUrlToRetry = null
                    )
                }
                showStatus(
                    localizationManager.currentStrings.value.opdsStatusCredentialsUpdated
                )
                if (urlToRetry != null) {
                    loadCatalog(urlToRetry, snapshot.history)
                }
            }.onFailure { error ->
                opdsState.update { state ->
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
}

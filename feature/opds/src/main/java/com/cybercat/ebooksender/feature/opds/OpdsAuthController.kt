package com.cybercat.ebooksender.feature.opds

import com.cybercat.ebooksender.data.opds.OpdsRepository
import com.cybercat.ebooksender.data.opds.OpdsSource
import com.cybercat.ebooksender.localization.LocalizationManager
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
                authDialog = OpdsAuthDialogState.forSource(
                    source = source,
                    urlToRetry = urlToRetry
                )
            )
        }
    }

    fun onAuthUsernameChanged(value: String) {
        opdsState.update { state ->
            state.copy(authDialog = state.authDialog.withUsername(value))
        }
    }

    fun onAuthPasswordChanged(value: String) {
        opdsState.update { state ->
            state.copy(authDialog = state.authDialog.withPassword(value))
        }
    }

    fun dismissCredentialsDialog() {
        opdsState.update { state ->
            state.copy(authDialog = OpdsAuthDialogState())
        }
    }

    fun saveCredentials() {
        val snapshot = opdsState.value
        val authDialog = snapshot.authDialog
        val sourceId = authDialog.sourceId ?: return
        val username = authDialog.username
        val password = authDialog.password
        val urlToRetry = authDialog.urlToRetry

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
                    it.copy(authDialog = OpdsAuthDialogState())
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
                        authDialog = OpdsAuthDialogState()
                    )
                }
            }
        }
    }
}

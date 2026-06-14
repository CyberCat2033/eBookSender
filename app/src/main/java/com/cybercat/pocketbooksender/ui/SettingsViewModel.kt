package com.cybercat.pocketbooksender.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybercat.pocketbooksender.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        _statusMessage
    ) { settings, status ->
        SettingsUiState(settings = settings, settingsStatusMessage = status)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setRootPath(value: String) {
        viewModelScope.launch { settingsRepository.setRootPath(value) }
    }

    fun setBookFileNameTemplate(value: String) {
        viewModelScope.launch { settingsRepository.setBookFileNameTemplate(value) }
    }

    fun setProgrammingFileNameTemplate(value: String) {
        viewModelScope.launch { settingsRepository.setProgrammingFileNameTemplate(value) }
    }

    fun setMangaFileNameTemplate(value: String) {
        viewModelScope.launch { settingsRepository.setMangaFileNameTemplate(value) }
    }

    fun setDefaultProgrammingTag(value: String) {
        viewModelScope.launch { settingsRepository.setDefaultProgrammingTag(value) }
    }

    fun setDefaultMangaSeries(value: String) {
        viewModelScope.launch { settingsRepository.setDefaultMangaSeries(value) }
    }

    fun setUseDynamicColor(value: Boolean) {
        viewModelScope.launch { settingsRepository.setUseDynamicColor(value) }
    }

    fun setEnableHaptics(value: Boolean) {
        viewModelScope.launch { settingsRepository.setEnableHaptics(value) }
    }

    fun clearDownloadCache() {
        viewModelScope.launch {
            BitmapCache.clear(context)
            showTemporaryStatus("Download cache cleared")
        }
    }

    private fun showTemporaryStatus(message: String) {
        _statusMessage.value = message
        viewModelScope.launch {
            delay(3000)
            if (_statusMessage.value == message) {
                _statusMessage.value = null
            }
        }
    }
}

package com.cybercat.pocketbooksender.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybercat.pocketbooksender.data.settings.SettingsRepository
import com.cybercat.pocketbooksender.model.AppTheme
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
import java.io.File

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

    fun setTheme(value: AppTheme) {
        viewModelScope.launch { settingsRepository.setTheme(value) }
    }

    fun clearDownloadCache() {
        viewModelScope.launch {
            val cacheDirs = listOf(
                File(context.cacheDir, "previews"),
                File(context.cacheDir, "opds"),
                File(context.cacheDir, "manga"),
                File(context.cacheDir, "pocketbook-catalog")
            )

            var totalBytes = 0L
            for (dir in cacheDirs) {
                totalBytes += getFolderSize(dir)
            }

            if (totalBytes == 0L) {
                showTemporaryStatus("Nothing to clear")
                return@launch
            }

            BitmapCache.clear(context)
            runCatching { File(context.cacheDir, "opds").deleteRecursively() }
            runCatching { File(context.cacheDir, "manga").deleteRecursively() }
            runCatching { File(context.cacheDir, "pocketbook-catalog").deleteRecursively() }

            val sizeInMb = totalBytes.toDouble() / (1024.0 * 1024.0)
            val message = String.format(java.util.Locale.US, "Cleared %.2f MB", sizeInMb)
            showTemporaryStatus(message)
        }
    }

    private fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        val files = file.listFiles()
        if (files != null) {
            for (f in files) {
                size += getFolderSize(f)
            }
        }
        return size
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

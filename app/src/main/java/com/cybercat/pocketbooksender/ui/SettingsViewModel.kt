package com.cybercat.pocketbooksender.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybercat.pocketbooksender.data.catalog.DeviceCatalogRepository
import com.cybercat.pocketbooksender.data.ftp.FtpGateway
import com.cybercat.pocketbooksender.data.settings.SettingsRepository
import com.cybercat.pocketbooksender.model.AppTheme
import com.cybercat.pocketbooksender.model.PocketBookDevice
import com.cybercat.pocketbooksender.transfer.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val connectionManager: ConnectionManager,
    private val ftpGateway: FtpGateway,
    private val localizationManager: com.cybercat.pocketbooksender.localization.LocalizationManager,
) : ViewModel() {
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _pendingRename = MutableStateFlow<PendingRename?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        _statusMessage,
        _pendingRename,
        localizationManager.availableLocales
    ) { settings, status, pending, locales ->
        SettingsUiState(
            settings = settings,
            settingsStatusMessage = status,
            pendingRename = pending,
            availableLocales = locales
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setRootPath(value: String) {
        viewModelScope.launch { settingsRepository.setRootPath(value) }
    }

    fun setBooksFolderName(value: String) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val oldName = settings.booksFolderName
            val newName = value.ifBlank { "Books" }
            if (oldName != newName) {
                val device = connectionManager.connectedDevice.value
                if (device == null) {
                    if (settings.warnOnDisconnectedRename) {
                        _pendingRename.value = PendingRename(FolderType.Books, oldName, newName)
                    } else {
                        settingsRepository.setBooksFolderName(newName)
                    }
                } else {
                    if (renameFolderOnDevice(oldName, newName)) {
                        settingsRepository.setBooksFolderName(newName)
                    }
                }
            }
        }
    }

    fun setDocumentsFolderName(value: String) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val oldName = settings.documentsFolderName
            val newName = value.ifBlank { "Documents" }
            if (oldName != newName) {
                val device = connectionManager.connectedDevice.value
                if (device == null) {
                    if (settings.warnOnDisconnectedRename) {
                        _pendingRename.value = PendingRename(FolderType.Documents, oldName, newName)
                    } else {
                        settingsRepository.setDocumentsFolderName(newName)
                    }
                } else {
                    if (renameFolderOnDevice(oldName, newName)) {
                        settingsRepository.setDocumentsFolderName(newName)
                    }
                }
            }
        }
    }

    fun setMangaFolderName(value: String) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val oldName = settings.mangaFolderName
            val newName = value.ifBlank { "Manga" }
            if (oldName != newName) {
                val device = connectionManager.connectedDevice.value
                if (device == null) {
                    if (settings.warnOnDisconnectedRename) {
                        _pendingRename.value = PendingRename(FolderType.Manga, oldName, newName)
                    } else {
                        settingsRepository.setMangaFolderName(newName)
                    }
                } else {
                    if (renameFolderOnDevice(oldName, newName)) {
                        settingsRepository.setMangaFolderName(newName)
                    }
                }
            }
        }
    }

    fun confirmPendingRename() {
        val pending = _pendingRename.value ?: return
        viewModelScope.launch {
            when (pending.folderType) {
                FolderType.Books -> settingsRepository.setBooksFolderName(pending.newName)
                FolderType.Documents -> settingsRepository.setDocumentsFolderName(pending.newName)
                FolderType.Manga -> settingsRepository.setMangaFolderName(pending.newName)
            }
            _pendingRename.value = null
        }
    }

    fun cancelPendingRename() {
        _pendingRename.value = null
    }

    fun setWarnOnDisconnectedRename(value: Boolean) {
        viewModelScope.launch { settingsRepository.setWarnOnDisconnectedRename(value) }
    }

    private suspend fun renameFolderOnDevice(oldName: String, newName: String): Boolean {
        val device = connectionManager.connectedDevice.value ?: return false
        return ftpGateway.rename(device, oldName, newName)
            .onSuccess {
                showTemporaryStatus("Renamed '$oldName' to '$newName' on device")
            }
            .onFailure { error ->
                val errorMsg = error.message.orEmpty()
                val statusText = when {
                    errorMsg.contains("550") || errorMsg.contains("exist", ignoreCase = true) ->
                        "Could not rename: folder '$newName' already exists"
                    else -> "Could not rename folder on device: ${error.localizedMessage ?: "unknown error"}"
                }
                showTemporaryStatus(statusText)
            }
            .isSuccess
    }

    fun setBookFileNameTemplate(value: String) {
        viewModelScope.launch { settingsRepository.setBookFileNameTemplate(value) }
    }

    fun setDocumentsFileNameTemplate(value: String) {
        viewModelScope.launch { settingsRepository.setDocumentsFileNameTemplate(value) }
    }

    fun setMangaFileNameTemplate(value: String) {
        viewModelScope.launch { settingsRepository.setMangaFileNameTemplate(value) }
    }

    fun setDefaultDocumentsTag(value: String) {
        viewModelScope.launch { settingsRepository.setDefaultDocumentsTag(value) }
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

    fun setLanguageCode(value: String) {
        viewModelScope.launch { settingsRepository.setLanguageCode(value) }
    }

    fun scanLocales() {
        viewModelScope.launch { localizationManager.scanLocales() }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
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

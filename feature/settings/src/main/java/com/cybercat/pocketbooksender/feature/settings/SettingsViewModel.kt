package com.cybercat.pocketbooksender.feature.settings

import android.content.Context
import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybercat.pocketbooksender.data.ftp.FtpGateway
import com.cybercat.pocketbooksender.data.manga.MangaRepository
import com.cybercat.pocketbooksender.data.opds.OpdsRepository
import com.cybercat.pocketbooksender.data.pocketbook.PocketBookRescanCoordinator
import com.cybercat.pocketbooksender.data.settings.SettingsRepository
import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.AppTheme
import com.cybercat.pocketbooksender.model.PocketBookDevice
import com.cybercat.pocketbooksender.transfer.ConnectionManager
import com.cybercat.pocketbooksender.ui.BitmapCache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val connectionManager: ConnectionManager,
    private val ftpGateway: FtpGateway,
    private val localizationManager: com.cybercat.pocketbooksender.localization.LocalizationManager,
    private val rescanCoordinator: PocketBookRescanCoordinator,
    private val opdsRepository: OpdsRepository,
    private val mangaRepository: MangaRepository
) : ViewModel() {
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _pendingRename = MutableStateFlow<PendingRename?>(null)
    private val _showLogoutWarning = MutableStateFlow(false)
    private val _activeFolderRename = MutableStateFlow<FolderType?>(null)
    private val _appearanceOverride = MutableStateFlow(AppearanceOverride())

    private val persistedSettings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )

    private val effectiveSettings = combine(
        persistedSettings,
        _appearanceOverride
    ) { settings, override ->
        settings.copy(
            useDynamicColor = override.useDynamicColor ?: settings.useDynamicColor,
            theme = override.theme ?: settings.theme
        )
    }

    init {
        viewModelScope.launch {
            persistedSettings.collect { settings ->
                _appearanceOverride.update { override ->
                    override.copy(
                        useDynamicColor = override.useDynamicColor?.takeUnless {
                            it ==
                                settings.useDynamicColor
                        },
                        theme = override.theme?.takeUnless { it == settings.theme }
                    )
                }
            }
        }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            effectiveSettings,
            _statusMessage,
            _pendingRename,
            localizationManager.availableLocales,
            _activeFolderRename
        ) { settings, status, pending, locales, activeFolderRename ->
            SettingsUiState(
                settings = settings,
                settingsStatusMessage = status,
                pendingRename = pending,
                activeFolderRename = activeFolderRename,
                availableLocales = locales
            )
        },
        _showLogoutWarning
    ) { state, showLogoutWarning ->
        state.copy(showLogoutWarning = showLogoutWarning)
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
                    if (renameFolderOnDevice(FolderType.Books, oldName, newName)) {
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
                    if (renameFolderOnDevice(FolderType.Documents, oldName, newName)) {
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
                    if (renameFolderOnDevice(FolderType.Manga, oldName, newName)) {
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

    private suspend fun renameFolderOnDevice(
        folderType: FolderType,
        oldName: String,
        newName: String
    ): Boolean {
        val device = connectionManager.connectedDevice.value ?: return false
        if (_activeFolderRename.value != null) return false

        _activeFolderRename.value = folderType
        return try {
            val result = ftpGateway.rename(device, oldName, newName)
            if (result.isSuccess) {
                showTemporaryStatus(
                    localizationManager.currentStrings.value.get(
                        "settings_renamed_on_device",
                        oldName,
                        newName
                    )
                )
                rescanCoordinator.requestRescanAndWait(device)
                true
            } else {
                val error = result.exceptionOrNull()
                val errorMsg = error?.message.orEmpty()
                val statusText = when {
                    errorMsg.contains("550") || errorMsg.contains("exist", ignoreCase = true) ->
                        localizationManager.currentStrings.value.get(
                            "settings_rename_failed_exists",
                            newName
                        )

                    else -> localizationManager.currentStrings.value.get(
                        "settings_rename_failed_error",
                        error?.localizedMessage ?: "unknown error"
                    )
                }
                showTemporaryStatus(statusText)
                false
            }
        } finally {
            _activeFolderRename.value = null
        }
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
        val override = _appearanceOverride.value
        val currentValue = override.useDynamicColor ?: persistedSettings.value.useDynamicColor
        if (currentValue == value && override.useDynamicColor == null) return

        _appearanceOverride.update { it.copy(useDynamicColor = value) }
        viewModelScope.launch {
            runCatching { settingsRepository.setUseDynamicColor(value) }
                .onFailure { clearUseDynamicColorOverrideIf(value) }
        }
    }

    fun setEnableHaptics(value: Boolean) {
        viewModelScope.launch { settingsRepository.setEnableHaptics(value) }
    }

    fun setBypassVpnForLocalConnections(value: Boolean) {
        viewModelScope.launch { settingsRepository.setBypassVpnForLocalConnections(value) }
    }

    fun setTheme(value: AppTheme) {
        val override = _appearanceOverride.value
        val currentValue = override.theme ?: persistedSettings.value.theme
        if (currentValue == value && override.theme == null) return

        _appearanceOverride.update { it.copy(theme = value) }
        viewModelScope.launch {
            runCatching { settingsRepository.setTheme(value) }
                .onFailure { clearThemeOverrideIf(value) }
        }
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
                showTemporaryStatus(localizationManager.currentStrings.value.settingsNothingToClear)
                return@launch
            }

            BitmapCache.clear(context)
            runCatching { File(context.cacheDir, "opds").deleteRecursively() }
            runCatching { File(context.cacheDir, "manga").deleteRecursively() }
            runCatching { File(context.cacheDir, "pocketbook-catalog").deleteRecursively() }

            val sizeInMb = totalBytes.toDouble() / (1024.0 * 1024.0)
            val message = localizationManager.currentStrings.value.get(
                "settings_cleared_cache",
                sizeInMb
            )
            showTemporaryStatus(message)
        }
    }

    fun logoutAll() {
        viewModelScope.launch {
            if (hasLogoutTargets()) {
                _showLogoutWarning.value = true
            } else {
                showTemporaryStatus(
                    localizationManager.currentStrings.value.settingsNoActiveAccounts
                )
            }
        }
    }

    fun confirmLogoutAll() {
        viewModelScope.launch {
            _showLogoutWarning.value = false
            val deviceConnected = connectionManager.connectedDevice.value != null
            val clearedOpds = runCatching { opdsRepository.logoutAll() }.getOrDefault(false)
            val clearedManga = runCatching {
                mangaRepository.clearSavedSeries()
            }.getOrDefault(false)
            val hasCookies = runCatching {
                CookieManager.getInstance().hasCookies()
            }.getOrDefault(false)

            if (hasCookies) {
                runCatching {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                }
            }

            if (deviceConnected) {
                connectionManager.disconnect()
            }

            val clearedAny = deviceConnected || clearedOpds || clearedManga || hasCookies
            val message = if (clearedAny) {
                localizationManager.currentStrings.value.settingsLoggedOutAll
            } else {
                localizationManager.currentStrings.value.settingsNoActiveAccounts
            }
            showTemporaryStatus(message)
        }
    }

    fun dismissLogoutWarning() {
        _showLogoutWarning.value = false
    }

    private suspend fun hasLogoutTargets(): Boolean {
        val deviceConnected = connectionManager.connectedDevice.value != null
        val hasOpdsCredentials = runCatching {
            opdsRepository.hasSavedCredentials()
        }.getOrDefault(false)
        val hasMangaSavedSeries = runCatching {
            mangaRepository.hasSavedSeries()
        }.getOrDefault(false)
        val hasCookies = runCatching {
            CookieManager.getInstance().hasCookies()
        }.getOrDefault(false)
        return deviceConnected || hasOpdsCredentials || hasMangaSavedSeries || hasCookies
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

    private fun clearUseDynamicColorOverrideIf(value: Boolean) {
        _appearanceOverride.update { override ->
            if (override.useDynamicColor == value) {
                override.copy(useDynamicColor = null)
            } else {
                override
            }
        }
    }

    private fun clearThemeOverrideIf(value: AppTheme) {
        _appearanceOverride.update { override ->
            if (override.theme == value) {
                override.copy(theme = null)
            } else {
                override
            }
        }
    }
}

private data class AppearanceOverride(
    val useDynamicColor: Boolean? = null,
    val theme: AppTheme? = null
)

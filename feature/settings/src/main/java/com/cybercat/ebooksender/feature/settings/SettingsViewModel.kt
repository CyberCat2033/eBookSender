package com.cybercat.ebooksender.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybercat.ebooksender.data.settings.DeviceFolderRenameUseCase
import com.cybercat.ebooksender.data.settings.LogoutUseCase
import com.cybercat.ebooksender.data.settings.SettingsRepository
import com.cybercat.ebooksender.data.transfer.UploadQueueManager
import com.cybercat.ebooksender.model.AppSettings
import com.cybercat.ebooksender.model.AppTheme
import com.cybercat.ebooksender.model.MangaLoginMode
import com.cybercat.ebooksender.model.normalizeFtpRelativeRootPath
import com.cybercat.ebooksender.transfer.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
@Suppress("ktlint:standard:backing-property-naming")
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val connectionManager: ConnectionManager,
    private val localizationManager: com.cybercat.ebooksender.localization.LocalizationManager,
    private val appCacheManager: SettingsCacheManager,
    private val uploadQueueManager: UploadQueueManager,
    private val logoutUseCase: LogoutUseCase,
    private val deviceFolderRenameUseCase: DeviceFolderRenameUseCase
) : ViewModel() {
    private val _statusMessage = MutableStateFlow<SettingsStatusMessage?>(null)
    private val _pendingRename = MutableStateFlow<PendingRename?>(null)
    private val _showLogoutWarning = MutableStateFlow(false)
    private val _showResetWarning = MutableStateFlow(false)
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
        combine(_showLogoutWarning, _showResetWarning) { showLogout, showReset ->
            showLogout to showReset
        }
    ) { state, (showLogoutWarning, showResetWarning) ->
        state.copy(
            showLogoutWarning = showLogoutWarning,
            showResetWarning = showResetWarning
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setRootPath(value: String) {
        viewModelScope.launch {
            val relativeRootPath = normalizeFtpRelativeRootPath(value)
            settingsRepository.setRootPath(relativeRootPath)
            connectionManager.connectedDevice.value?.let { device ->
                val updatedDevice = device.copy(relativeRootPath = relativeRootPath)
                if (updatedDevice != device) {
                    connectionManager.connect(updatedDevice)
                }
            }
        }
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
            val result = deviceFolderRenameUseCase.renameFolder(device, oldName, newName)
            when (result) {
                is DeviceFolderRenameUseCase.Result.Success -> {
                    showTemporaryStatus(
                        localizationManager.currentStrings.value.get(
                            "settings_renamed_on_device",
                            oldName,
                            newName
                        )
                    )
                    true
                }

                is DeviceFolderRenameUseCase.Result.AlreadyExists -> {
                    showTemporaryStatus(
                        localizationManager.currentStrings.value.get(
                            "settings_rename_failed_exists",
                            newName
                        )
                    )
                    false
                }

                is DeviceFolderRenameUseCase.Result.NotSupported -> {
                    val settings = settingsRepository.settings.first()
                    if (settings.warnOnDisconnectedRename) {
                        _pendingRename.value = PendingRename(folderType, oldName, newName)
                    }
                    showTemporaryStatus(
                        SettingsStatusMessage.FolderRenameNotSupported
                    )
                    settings.warnOnDisconnectedRename.not()
                }

                is DeviceFolderRenameUseCase.Result.Error -> {
                    showTemporaryStatus(
                        localizationManager.currentStrings.value.get(
                            "settings_rename_failed_error",
                            result.message
                        )
                    )
                    false
                }
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

    fun setMangaLoginMode(value: MangaLoginMode) {
        viewModelScope.launch { settingsRepository.setMangaLoginMode(value) }
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
            val totalBytes = appCacheManager.clearDownloadCache()
            val removedQueueItems = uploadQueueManager.removeDownloadCacheItems()

            if (totalBytes == 0L && removedQueueItems == 0) {
                showTemporaryStatus(localizationManager.currentStrings.value.settingsNothingToClear)
                return@launch
            }

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
            if (logoutUseCase.hasLogoutTargets()) {
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
            val clearedAny = logoutUseCase.logoutAll()

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

    fun resetSettings() {
        _showResetWarning.value = true
    }

    fun confirmResetSettings() {
        viewModelScope.launch {
            _showResetWarning.value = false
            settingsRepository.resetToDefaults()
            // Clear the transient appearance override so the UI picks up the
            // reset theme/dynamic-color defaults instead of the last in-flight pick.
            _appearanceOverride.value = AppearanceOverride()
            logoutUseCase.logoutAll()
            showTemporaryStatus(
                localizationManager.currentStrings.value.settingsResetDone
            )
        }
    }

    fun dismissResetWarning() {
        _showResetWarning.value = false
    }

    private fun showTemporaryStatus(message: String) {
        showTemporaryStatus(SettingsStatusMessage.Text(message))
    }

    private fun showTemporaryStatus(message: SettingsStatusMessage) {
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

package com.cybercat.pocketbooksender.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybercat.pocketbooksender.data.catalog.DeviceCatalogRepository
import com.cybercat.pocketbooksender.data.settings.SettingsRepository
import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.DeviceCatalog
import com.cybercat.pocketbooksender.model.PocketBookDevice
import com.cybercat.pocketbooksender.transfer.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val deviceCatalogRepository: DeviceCatalogRepository,
    private val connectionManager: ConnectionManager,
    private val settingsRepository: SettingsRepository,
    private val localizationManager: com.cybercat.pocketbooksender.localization.LocalizationManager,
) : ViewModel() {

    private val _isEditMode = MutableStateFlow(false)
    private val _selectedFilePaths = MutableStateFlow(emptySet<String>())
    private val _isDeleting = MutableStateFlow(false)
    private val _deleteErrorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CatalogUiState> = combine(
        connectionManager.connectedDevice,
        deviceCatalogRepository.catalog,
        settingsRepository.settings,
        _isEditMode,
        _selectedFilePaths,
        _isDeleting,
        _deleteErrorMessage
    ) { args ->
        val device = args[0] as PocketBookDevice?
        val catalog = args[1] as DeviceCatalog
        val settings = args[2] as AppSettings
        val isEdit = args[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val selected = args[4] as Set<String>
        val deleting = args[5] as Boolean
        val err = args[6] as String?

        CatalogUiState(
            connectedDevice = device,
            deviceCatalog = catalog,
            settings = settings,
            isEditMode = isEdit,
            selectedFilePaths = selected,
            isDeleting = deleting,
            deleteErrorMessage = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CatalogUiState()
    )

    init {
        viewModelScope.launch {
            connectionManager.connectedDevice.collect { device ->
                if (device == null) {
                    setEditMode(false)
                }
            }
        }
    }

    fun reloadDeviceCatalog() {
        val device = uiState.value.connectedDevice ?: return
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            deviceCatalogRepository.refresh(device)
            val elapsedTime = System.currentTimeMillis() - startTime
            val remaining = 800L - elapsedTime
            if (remaining > 0) {
                kotlinx.coroutines.delay(remaining)
            }
        }
    }

    fun setEditMode(enabled: Boolean) {
        _isEditMode.value = enabled
        if (!enabled) {
            _selectedFilePaths.value = emptySet()
            _deleteErrorMessage.value = null
        }
    }

    fun toggleFileSelection(path: String) {
        _selectedFilePaths.update { current ->
            if (path in current) {
                current - path
            } else {
                current + path
            }
        }
    }

    fun setFileSelection(path: String, selected: Boolean) {
        _selectedFilePaths.update { current ->
            if (selected) {
                current + path
            } else {
                current - path
            }
        }
    }

    fun toggleGroupSelection(filePaths: List<String>, selectAll: Boolean) {
        _selectedFilePaths.update { current ->
            if (selectAll) {
                current + filePaths
            } else {
                current - filePaths.toSet()
            }
        }
    }

    fun clearDeleteError() {
        _deleteErrorMessage.value = null
    }

    fun deleteSelectedFiles() {
        val device = uiState.value.connectedDevice ?: return
        val paths = _selectedFilePaths.value.toList()
        if (paths.isEmpty()) return

        viewModelScope.launch {
            _isDeleting.value = true
            _deleteErrorMessage.value = null
            runCatching {
                deviceCatalogRepository.deleteFiles(device, paths)
            }.onSuccess {
                _selectedFilePaths.value = emptySet()
                _isEditMode.value = false
            }.onFailure { error ->
                _deleteErrorMessage.value = error.message ?: localizationManager.currentStrings.value.catalogErrorFailedToDelete
            }
            _isDeleting.value = false
        }
    }
}

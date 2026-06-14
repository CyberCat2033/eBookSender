package com.cybercat.pocketbooksender.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybercat.pocketbooksender.data.catalog.DeviceCatalogRepository
import com.cybercat.pocketbooksender.data.settings.SettingsRepository
import com.cybercat.pocketbooksender.transfer.ConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val deviceCatalogRepository: DeviceCatalogRepository,
    private val connectionManager: ConnectionManager,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<CatalogUiState> = combine(
        connectionManager.connectedDevice,
        deviceCatalogRepository.catalog,
        settingsRepository.settings
    ) { device, catalog, settings ->
        CatalogUiState(
            connectedDevice = device,
            deviceCatalog = catalog,
            settings = settings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CatalogUiState()
    )

    fun reloadDeviceCatalog() {
        val device = uiState.value.connectedDevice ?: return
        viewModelScope.launch {
            deviceCatalogRepository.refresh(device)
        }
    }
}

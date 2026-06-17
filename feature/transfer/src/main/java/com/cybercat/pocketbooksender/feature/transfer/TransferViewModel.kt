package com.cybercat.pocketbooksender.feature.transfer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybercat.pocketbooksender.data.catalog.DeviceCatalogRepository
import com.cybercat.pocketbooksender.data.device.DeviceProfileDetector
import com.cybercat.pocketbooksender.data.ftp.FtpGateway
import com.cybercat.pocketbooksender.data.settings.SettingsRepository
import com.cybercat.pocketbooksender.data.transfer.TransferLauncher
import com.cybercat.pocketbooksender.data.transfer.UploadQueueManager
import com.cybercat.pocketbooksender.domain.FtpUrlParser
import com.cybercat.pocketbooksender.localization.LocalizationManager
import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.CatalogGroup
import com.cybercat.pocketbooksender.model.DeviceCatalog
import com.cybercat.pocketbooksender.model.MangaSeriesGroup
import com.cybercat.pocketbooksender.model.RemoteDevice
import com.cybercat.pocketbooksender.model.UploadItem
import com.cybercat.pocketbooksender.model.UploadStatus
import com.cybercat.pocketbooksender.model.normalizeFtpRelativeRootPath
import com.cybercat.pocketbooksender.network.isLocalNetworkBypassBlocked
import com.cybercat.pocketbooksender.transfer.ConnectionManager
import com.cybercat.pocketbooksender.transfer.TransferCoordinator
import com.cybercat.pocketbooksender.transfer.TransferEvent
import com.cybercat.pocketbooksender.transfer.TransferFailureReason
import com.cybercat.pocketbooksender.transfer.TransferUploadItem
import com.cybercat.pocketbooksender.ui.FtpErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Suppress("ktlint:standard:backing-property-naming")
@HiltViewModel
class TransferViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val queueManager: UploadQueueManager,
    private val catalogRepository: DeviceCatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val transferCoordinator: TransferCoordinator,
    private val ftpGateway: FtpGateway,
    private val deviceProfileDetector: DeviceProfileDetector,
    private val localizationManager: LocalizationManager,
    private val transferLauncher: TransferLauncher,
    private val ftpErrorMapper: FtpErrorMapper
) : ViewModel() {

    private val _ftpInput = MutableStateFlow("")
    private val _isConnecting = MutableStateFlow(false)
    private val _isTransferActive = MutableStateFlow(false)
    private val _activeTransferItemIds = MutableStateFlow<Set<String>>(emptySet())
    private val _uploadProgressById = MutableStateFlow<Map<String, Float>>(emptyMap())
    private val _errorState = MutableStateFlow<FtpErrorState?>(null)
    private val _showVpnBypassDialog = MutableStateFlow(false)
    private val _ftpSuggestions =
        MutableStateFlow<Pair<List<String>, List<String>>>(Pair(emptyList(), emptyList()))
    private var pendingClearQueueJob: Job? = null

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<TransferUiState> = combine<Any?, TransferUiState>(
        _ftpInput,
        _isConnecting,
        connectionManager.connectedDevice,
        _isTransferActive,
        _activeTransferItemIds,
        _uploadProgressById,
        queueManager.queue,
        settingsRepository.settings,
        _errorState,
        _showVpnBypassDialog,
        catalogRepository.catalog,
        _ftpSuggestions,
        localizationManager.currentStrings
    ) { values ->
        val ftpInput = values[0] as String
        val isConnecting = values[1] as Boolean
        val device = values[2] as RemoteDevice?
        val isTransfer = values[3] as Boolean
        val activeTransferItemIds = values[4] as Set<String>
        val uploadProgressById = values[5] as Map<String, Float>
        val queue = values[6] as List<UploadItem>
        val settings = values[7] as AppSettings
        val errorState = values[8] as FtpErrorState?
        val showVpnBypassDialog = values[9] as Boolean
        val catalog = values[10] as DeviceCatalog
        val suggestions = values[11] as Pair<List<String>, List<String>>
        val strings = values[12] as com.cybercat.pocketbooksender.localization.AppStrings

        val error = when (errorState) {
            is FtpErrorState.Connection -> ftpErrorMapper.mapConnectionError(
                errorState.error,
                errorState.device,
                strings
            )

            is FtpErrorState.InvalidUrl -> ftpErrorMapper.mapInvalidFtpError(
                errorState.error,
                strings
            )

            is FtpErrorState.RawMessage -> errorState.message

            is FtpErrorState.ConnectBeforeUpload -> strings.transferErrorConnectBeforeUpload

            is FtpErrorState.QueueEmpty -> strings.transferErrorQueueEmpty

            is FtpErrorState.NoPendingFiles -> strings.transferErrorNoPendingFiles

            null -> null
        }

        val tags = if (catalog.documents.isNotEmpty()) {
            catalog.documents.map(CatalogGroup::name)
        } else {
            suggestions.first
        }
        val series = if (catalog.manga.isNotEmpty()) {
            catalog.manga.map(MangaSeriesGroup::name)
        } else {
            suggestions.second
        }

        TransferUiState(
            ftpInput = ftpInput,
            isConnecting = isConnecting,
            connectedDevice = device,
            isTransferActive = isTransfer,
            activeTransferItemIds = activeTransferItemIds,
            uploadProgressById = uploadProgressById,
            queue = queue,
            settings = settings,
            errorMessage = error,
            showVpnBypassDialog = showVpnBypassDialog,
            documentsTags = tags,
            mangaSeriesSuggestions = series
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransferUiState()
    )

    init {
        // Collect FTP input field default values reactively on connection change
        connectionManager.connectedDevice
            .onEach { device ->
                if (device != null) {
                    _ftpInput.value = device.ftpUrl
                    _errorState.value = null
                    _showVpnBypassDialog.value = false
                    refreshRemoteFolderSuggestions(device)
                } else {
                    _ftpSuggestions.value = Pair(emptyList(), emptyList())
                }
            }
            .launchIn(viewModelScope)

        // Reactively handle foreground transfer service events and update queue
        transferCoordinator.events
            .onEach(::handleTransferEvent)
            .launchIn(viewModelScope)
    }

    fun onFtpInputChanged(value: String) {
        _ftpInput.value = value
    }

    fun connect() {
        connectTo(_ftpInput.value)
    }

    fun connectTo(rawLink: String) {
        val parsedDevice = FtpUrlParser.parse(rawLink)
            .getOrElse { error ->
                _isConnecting.value = false
                _errorState.value = FtpErrorState.InvalidUrl(error)
                return
            }

        val settingsRootPath = normalizeFtpRelativeRootPath(uiState.value.settings.rootPath)
        val device = parsedDevice.copy(
            relativeRootPath = settingsRootPath
        )

        _isConnecting.value = true
        connectionManager.disconnect()
        _ftpInput.value = device.ftpUrl
        _errorState.value = null
        _showVpnBypassDialog.value = false

        viewModelScope.launch {
            val connectionResult = ftpGateway.checkConnection(device)
            if (connectionResult.isSuccess) {
                val detectedDevice = deviceProfileDetector.detect(device)
                _isConnecting.value = false
                connectionManager.connect(detectedDevice)
                _ftpInput.value = detectedDevice.ftpUrl
                _errorState.value = null
                _showVpnBypassDialog.value = false
            } else {
                connectionResult.onFailure { error ->
                    _isConnecting.value = false
                    connectionManager.disconnect()
                    if (error.isLocalNetworkBypassBlocked()) {
                        _showVpnBypassDialog.value = true
                        _errorState.value = null
                    } else {
                        _errorState.value = FtpErrorState.Connection(error, device)
                    }
                }
            }
        }
    }

    fun disconnect() {
        _isConnecting.value = false
        connectionManager.disconnect()
    }

    fun dismissVpnBypassDialog() {
        _showVpnBypassDialog.value = false
    }

    fun disableVpnBypassForLocalConnections() {
        viewModelScope.launch {
            settingsRepository.setBypassVpnForLocalConnections(false)
            _showVpnBypassDialog.value = false
        }
    }

    fun addUris(uris: List<Uri>) {
        queueManager.addUris(uris)
    }

    fun removeItem(id: String) {
        queueManager.removeItem(id)
    }

    fun clearQueue() {
        pendingClearQueueJob?.cancel()
        pendingClearQueueJob = null
        queueManager.clearQueue()
    }

    fun clearQueueAfterDelay(delayMillis: Long) {
        pendingClearQueueJob?.cancel()
        if (delayMillis <= 0L) {
            clearQueue()
            return
        }
        pendingClearQueueJob = viewModelScope.launch {
            delay(delayMillis)
            queueManager.clearQueue()
        }
    }

    fun updateCategory(id: String, category: BookCategory) {
        queueManager.updateCategory(id, category)
    }

    fun updateDocumentsTag(id: String, tag: String) {
        queueManager.updateDocumentsTag(id, tag)
    }

    fun updateMangaSeries(id: String, series: String) {
        queueManager.updateMangaSeries(id, series)
    }

    fun updateQueuedMangaSeries(oldSeries: String?, series: String) {
        queueManager.updateQueuedMangaSeries(oldSeries, series)
    }

    fun uploadAll() {
        val device = uiState.value.connectedDevice
        if (device == null) {
            _errorState.value = FtpErrorState.ConnectBeforeUpload
            return
        }
        val items = uiState.value.queue
        if (items.isEmpty()) {
            _errorState.value = FtpErrorState.QueueEmpty
            return
        }
        val pending = items.filter {
            it.status == UploadStatus.Pending ||
                it.status == UploadStatus.Failed ||
                it.status == UploadStatus.Skipped
        }
        if (pending.isEmpty()) {
            _errorState.value = FtpErrorState.NoPendingFiles
            return
        }

        val requestId = transferCoordinator.submit(
            device = device,
            items = pending.map { item ->
                TransferUploadItem(
                    id = item.id,
                    sourceUri = item.sourceUri,
                    originalName = item.originalName,
                    extension = item.extension,
                    category = item.category,
                    title = item.title,
                    mangaSeries = item.mangaSeries,
                    mangaVolume = item.mangaVolume,
                    seriesIndex = item.seriesIndex,
                    plannedPath = item.plannedPath
                )
            }
        )

        _activeTransferItemIds.value = pending.map { it.id }.toSet()
        _uploadProgressById.value = pending.associate { item -> item.id to 0f }
        _isTransferActive.value = true
        _errorState.value = null
        _showVpnBypassDialog.value = false

        val pendingIds = pending.mapTo(mutableSetOf()) { item -> item.id }
        queueManager.updateQueue { current ->
            current.map { item ->
                if (item.id in pendingIds) {
                    item.copy(status = UploadStatus.Pending, progress = 0.0f)
                } else {
                    item
                }
            }
        }

        transferLauncher.startTransfer(requestId)
    }

    private fun handleTransferEvent(event: TransferEvent) {
        when (event) {
            is TransferEvent.ItemStarted -> {
                _uploadProgressById.update { current ->
                    current + (event.itemId to 0f)
                }
                queueManager.updateQueue { current ->
                    current.map { item ->
                        if (item.id == event.itemId) {
                            item.copy(status = UploadStatus.Uploading, progress = 0.0f)
                        } else {
                            item
                        }
                    }
                }
            }

            is TransferEvent.ItemProgress -> {
                _uploadProgressById.update { current ->
                    if (current[event.itemId] == event.progress) {
                        current
                    } else {
                        current + (event.itemId to event.progress)
                    }
                }
            }

            is TransferEvent.ItemUploaded -> {
                _uploadProgressById.update { current ->
                    current + (event.itemId to 1f)
                }
                queueManager.updateQueue { current ->
                    current.map { item ->
                        if (item.id == event.itemId) {
                            item.copy(status = UploadStatus.Uploaded, progress = 1f)
                        } else {
                            item
                        }
                    }
                }
            }

            is TransferEvent.ItemFailed -> {
                _uploadProgressById.update { current ->
                    current - event.itemId
                }
                queueManager.updateQueue { current ->
                    current.map { item ->
                        if (item.id == event.itemId) {
                            item.copy(status = UploadStatus.Failed, progress = 0f)
                        } else {
                            item
                        }
                    }
                }
                if (event.failureReason == TransferFailureReason.LocalNetworkBypassBlocked) {
                    _showVpnBypassDialog.value = true
                    _errorState.value = null
                } else {
                    _errorState.value = FtpErrorState.RawMessage(event.message)
                }
            }

            is TransferEvent.Completed -> {
                _isTransferActive.value = false
                _activeTransferItemIds.value = emptySet()
                _uploadProgressById.value = emptyMap()
                val device = connectionManager.connectedDevice.value
                if (device != null) {
                    refreshRemoteFolderSuggestions(device)
                    viewModelScope.launch {
                        catalogRepository.refresh(device)
                    }
                }
            }
        }
    }

    private fun refreshRemoteFolderSuggestions(device: RemoteDevice) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val tags = ftpGateway.listDirectories(
                device,
                settings.documentsFolderName
            ).getOrDefault(emptyList())
            val series = ftpGateway.listDirectories(
                device,
                settings.mangaFolderName
            ).getOrDefault(emptyList())
            _ftpSuggestions.value = Pair(tags, series)
        }
    }
}

sealed interface FtpErrorState {
    data class Connection(val error: Throwable, val device: RemoteDevice) : FtpErrorState
    data class InvalidUrl(val error: Throwable) : FtpErrorState
    data class RawMessage(val message: String) : FtpErrorState
    object ConnectBeforeUpload : FtpErrorState
    object QueueEmpty : FtpErrorState
    object NoPendingFiles : FtpErrorState
}

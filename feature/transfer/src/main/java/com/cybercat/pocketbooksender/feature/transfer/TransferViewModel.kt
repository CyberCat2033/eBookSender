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
import com.cybercat.pocketbooksender.localization.AppStrings
import com.cybercat.pocketbooksender.localization.LocalizationManager
import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.CatalogGroup
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
import com.cybercat.pocketbooksender.transfer.TransferSubmitResult
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
    private val _isTransferCanceling = MutableStateFlow(false)
    private val _activeTransferItemIds = MutableStateFlow<Set<String>>(emptySet())
    private val _currentUploadProgress = MutableStateFlow(ActiveUploadProgress())
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _errorState = MutableStateFlow<FtpErrorState?>(null)
    private val _showVpnBypassDialog = MutableStateFlow(false)
    private val _ftpSuggestions =
        MutableStateFlow<Pair<List<String>, List<String>>>(Pair(emptyList(), emptyList()))
    private var pendingClearQueueJob: Job? = null
    private var activeTransferRequestId: String? = null

    val transferRuntimeState: StateFlow<TransferRuntimeUiState> = combine(
        _isTransferActive,
        _isTransferCanceling,
        _activeTransferItemIds,
        _currentUploadProgress
    ) { isTransferActive, isTransferCanceling, activeTransferItemIds, currentUploadProgress ->
        TransferRuntimeUiState(
            isTransferActive = isTransferActive,
            isTransferCanceling = isTransferCanceling,
            activeTransferItemIds = activeTransferItemIds,
            currentUploadItemId = currentUploadProgress.itemId,
            currentUploadProgress = currentUploadProgress.progress
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransferRuntimeUiState()
    )

    private val transferContentState = combine(
        queueManager.queue,
        settingsRepository.settings,
        catalogRepository.catalog,
        _ftpSuggestions
    ) { queue, settings, catalog, suggestions ->
        TransferContentState(
            queue = queue,
            settings = settings,
            documentsTags = if (catalog.documents.isNotEmpty()) {
                catalog.documents.map(CatalogGroup::name)
            } else {
                suggestions.first
            },
            mangaSeriesSuggestions = if (catalog.manga.isNotEmpty()) {
                catalog.manga.map(MangaSeriesGroup::name)
            } else {
                suggestions.second
            }
        )
    }

    private val transferUiDetailsState = combine(
        transferContentState,
        _errorState,
        _statusMessage,
        _showVpnBypassDialog,
        localizationManager.currentStrings
    ) { content, errorState, statusMessage, showVpnBypassDialog, strings ->
        TransferUiDetailsState(
            queue = content.queue,
            settings = content.settings,
            statusMessage = statusMessage,
            errorMessage = mapErrorMessage(errorState, strings),
            showVpnBypassDialog = showVpnBypassDialog,
            documentsTags = content.documentsTags,
            mangaSeriesSuggestions = content.mangaSeriesSuggestions
        )
    }

    val uiState: StateFlow<TransferUiState> = combine(
        _ftpInput,
        _isConnecting,
        connectionManager.connectedDevice,
        transferUiDetailsState
    ) { ftpInput, isConnecting, device, details ->
        TransferUiState(
            ftpInput = ftpInput,
            isConnecting = isConnecting,
            connectedDevice = device,
            queue = details.queue,
            settings = details.settings,
            statusMessage = details.statusMessage,
            errorMessage = details.errorMessage,
            showVpnBypassDialog = details.showVpnBypassDialog,
            documentsTags = details.documentsTags,
            mangaSeriesSuggestions = details.mangaSeriesSuggestions
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
                _statusMessage.value = null
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
        _statusMessage.value = null
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
                        _statusMessage.value = null
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
        _statusMessage.value = null
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
            _statusMessage.value = null
            _errorState.value = FtpErrorState.ConnectBeforeUpload
            return
        }
        val items = uiState.value.queue
        if (items.isEmpty()) {
            _statusMessage.value = null
            _errorState.value = FtpErrorState.QueueEmpty
            return
        }
        val pending = items.filter {
            it.status == UploadStatus.Pending ||
                it.status == UploadStatus.Failed ||
                it.status == UploadStatus.Skipped
        }
        if (pending.isEmpty()) {
            _statusMessage.value = null
            _errorState.value = FtpErrorState.NoPendingFiles
            return
        }

        val submitResult = transferCoordinator.submit(
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
        val requestId = when (submitResult) {
            is TransferSubmitResult.Accepted -> submitResult.requestId

            TransferSubmitResult.RejectedAlreadyRunning -> {
                _statusMessage.value = null
                _errorState.value = FtpErrorState.RawMessage(
                    localizationManager.currentStrings.value.get(
                        "transfer_error_upload_in_progress"
                    )
                )
                return
            }
        }

        _activeTransferItemIds.value = pending.map { it.id }.toSet()
        _currentUploadProgress.value = ActiveUploadProgress()
        _isTransferActive.value = true
        _isTransferCanceling.value = false
        activeTransferRequestId = requestId
        _statusMessage.value = null
        _errorState.value = null
        _showVpnBypassDialog.value = false

        val pendingIds = pending.mapTo(mutableSetOf()) { item -> item.id }
        updateQueuedTransferStatus(
            itemIds = pendingIds,
            status = UploadStatus.Pending,
            progress = 0f
        )

        transferLauncher.startTransfer(requestId)
    }

    fun cancelUpload() {
        if (_isTransferCanceling.value) return
        val requestId = activeTransferRequestId ?: return
        _isTransferCanceling.value = true
        _statusMessage.value = null
        _errorState.value = null

        val canceledPendingRequest = transferCoordinator.cancelPendingRequest(requestId)
        if (canceledPendingRequest != null) {
            handleTransferEvent(TransferEvent.Canceled(uploaded = 0, failed = 0))
            return
        }

        transferLauncher.cancelTransfer(requestId)
    }

    private fun handleTransferEvent(event: TransferEvent) {
        when (event) {
            is TransferEvent.ItemStarted -> {
                _currentUploadProgress.value =
                    ActiveUploadProgress(itemId = event.itemId, progress = 0f)
                updateQueuedTransferStatus(
                    itemId = event.itemId,
                    status = UploadStatus.Uploading,
                    progress = 0f
                )
            }

            is TransferEvent.ItemProgress -> {
                _currentUploadProgress.update { current ->
                    if (current.itemId == event.itemId && current.progress == event.progress) {
                        current
                    } else {
                        ActiveUploadProgress(itemId = event.itemId, progress = event.progress)
                    }
                }
            }

            is TransferEvent.ItemUploaded -> {
                _currentUploadProgress.update { current ->
                    if (current.itemId == event.itemId) {
                        current.copy(progress = 1f)
                    } else {
                        current
                    }
                }
                updateQueuedTransferStatus(
                    itemId = event.itemId,
                    status = UploadStatus.Uploaded,
                    progress = 1f
                )
            }

            is TransferEvent.ItemFailed -> {
                _isTransferCanceling.value = false
                _currentUploadProgress.update { current ->
                    if (current.itemId == event.itemId) ActiveUploadProgress() else current
                }
                updateQueuedTransferStatus(
                    itemId = event.itemId,
                    status = UploadStatus.Failed,
                    progress = 0f
                )
                if (event.failureReason == TransferFailureReason.LocalNetworkBypassBlocked) {
                    _showVpnBypassDialog.value = true
                    _errorState.value = null
                } else {
                    _statusMessage.value = null
                    _errorState.value = FtpErrorState.RawMessage(event.message)
                }
            }

            is TransferEvent.Completed -> {
                _isTransferActive.value = false
                _activeTransferItemIds.value = emptySet()
                _currentUploadProgress.value = ActiveUploadProgress()
                _isTransferCanceling.value = false
                activeTransferRequestId = null
                val device = connectionManager.connectedDevice.value
                if (device != null && event.uploaded > 0) {
                    refreshRemoteFolderSuggestions(device)
                    viewModelScope.launch {
                        catalogRepository.refresh(device)
                    }
                }
            }

            is TransferEvent.Canceled -> {
                val activeItemIds = _activeTransferItemIds.value
                restoreCanceledQueuedItems(activeItemIds)
                _isTransferActive.value = false
                _activeTransferItemIds.value = emptySet()
                _currentUploadProgress.value = ActiveUploadProgress()
                _isTransferCanceling.value = false
                activeTransferRequestId = null
                _statusMessage.value = transferCanceledMessage(event.uploaded)
                val device = connectionManager.connectedDevice.value
                if (device != null && event.uploaded > 0) {
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

    private fun mapErrorMessage(errorState: FtpErrorState?, strings: AppStrings): String? =
        when (errorState) {
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

    private fun updateQueuedTransferStatus(itemId: String, status: UploadStatus, progress: Float) {
        updateQueuedTransferStatus(
            itemIds = setOf(itemId),
            status = status,
            progress = progress
        )
    }

    private fun updateQueuedTransferStatus(
        itemIds: Set<String>,
        status: UploadStatus,
        progress: Float
    ) {
        if (itemIds.isEmpty()) return
        queueManager.updateQueue { current ->
            var changed = false
            val updated = current.map { item ->
                if (item.id !in itemIds) {
                    item
                } else {
                    val updatedItem = if (item.status == status && item.progress == progress) {
                        item
                    } else {
                        item.copy(status = status, progress = progress)
                    }
                    changed = changed || updatedItem !== item
                    updatedItem
                }
            }
            if (changed) updated else current
        }
    }

    private fun restoreCanceledQueuedItems(itemIds: Set<String>) {
        if (itemIds.isEmpty()) return
        queueManager.updateQueue { current ->
            var changed = false
            val updated = current.map { item ->
                if (item.id !in itemIds) {
                    item
                } else {
                    val restoredItem = when (item.status) {
                        UploadStatus.Uploading -> item.copy(
                            status = UploadStatus.Pending,
                            progress = 0f
                        )

                        UploadStatus.Pending,
                        UploadStatus.Preparing -> item.copy(progress = 0f)

                        UploadStatus.Uploaded,
                        UploadStatus.Failed,
                        UploadStatus.Skipped -> item
                    }
                    changed = changed || restoredItem != item
                    restoredItem
                }
            }
            if (changed) updated else current
        }
    }

    private fun transferCanceledMessage(uploaded: Int): String {
        val strings = localizationManager.currentStrings.value
        return if (uploaded > 0) {
            strings.get("transfer_status_upload_canceled_with_files", uploaded)
        } else {
            strings.get("transfer_status_upload_canceled")
        }
    }
}

private data class TransferContentState(
    val queue: List<UploadItem>,
    val settings: AppSettings,
    val documentsTags: List<String>,
    val mangaSeriesSuggestions: List<String>
)

private data class TransferUiDetailsState(
    val queue: List<UploadItem>,
    val settings: AppSettings,
    val statusMessage: String?,
    val errorMessage: String?,
    val showVpnBypassDialog: Boolean,
    val documentsTags: List<String>,
    val mangaSeriesSuggestions: List<String>
)

sealed interface FtpErrorState {
    data class Connection(val error: Throwable, val device: RemoteDevice) : FtpErrorState
    data class InvalidUrl(val error: Throwable) : FtpErrorState
    data class RawMessage(val message: String) : FtpErrorState
    object ConnectBeforeUpload : FtpErrorState
    object QueueEmpty : FtpErrorState
    object NoPendingFiles : FtpErrorState
}

private data class ActiveUploadProgress(val itemId: String? = null, val progress: Float = 0f)

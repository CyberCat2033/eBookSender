package com.cybercat.pocketbooksender.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybercat.pocketbooksender.data.catalog.DeviceCatalogRepository
import com.cybercat.pocketbooksender.data.ftp.FtpGateway
import com.cybercat.pocketbooksender.data.settings.SettingsRepository
import com.cybercat.pocketbooksender.domain.FtpUrlParser
import com.cybercat.pocketbooksender.model.AppSettings
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.CatalogGroup
import com.cybercat.pocketbooksender.model.DeviceCatalog
import com.cybercat.pocketbooksender.model.MangaSeriesGroup
import com.cybercat.pocketbooksender.model.PocketBookDevice
import com.cybercat.pocketbooksender.model.UploadItem
import com.cybercat.pocketbooksender.model.UploadStatus
import com.cybercat.pocketbooksender.transfer.ConnectionManager
import com.cybercat.pocketbooksender.transfer.TransferCoordinator
import com.cybercat.pocketbooksender.transfer.TransferEvent
import com.cybercat.pocketbooksender.transfer.TransferForegroundService
import com.cybercat.pocketbooksender.transfer.TransferUploadItem
import com.cybercat.pocketbooksender.transfer.UploadQueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
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

@HiltViewModel
class TransferViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: ConnectionManager,
    private val queueManager: UploadQueueManager,
    private val catalogRepository: DeviceCatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val transferCoordinator: TransferCoordinator,
    private val ftpGateway: FtpGateway,
    private val localizationManager: com.cybercat.pocketbooksender.localization.LocalizationManager,
) : ViewModel() {

    private val _ftpInput = MutableStateFlow("")
    private val _isConnecting = MutableStateFlow(false)
    private val _isTransferActive = MutableStateFlow(false)
    private val _activeTransferItemIds = MutableStateFlow<Set<String>>(emptySet())
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _ftpSuggestions = MutableStateFlow<Pair<List<String>, List<String>>>(Pair(emptyList(), emptyList()))

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<TransferUiState> = combine<Any?, TransferUiState>(
        _ftpInput,
        _isConnecting,
        connectionManager.connectedDevice,
        _isTransferActive,
        _activeTransferItemIds,
        queueManager.queue,
        settingsRepository.settings,
        _errorMessage,
        catalogRepository.catalog,
        _ftpSuggestions
    ) { values ->
        val ftpInput = values[0] as String
        val isConnecting = values[1] as Boolean
        val device = values[2] as PocketBookDevice?
        val isTransfer = values[3] as Boolean
        val activeTransferItemIds = values[4] as Set<String>
        val queue = values[5] as List<UploadItem>
        val settings = values[6] as AppSettings
        val error = values[7] as String?
        val catalog = values[8] as DeviceCatalog
        val suggestions = values[9] as Pair<List<String>, List<String>>
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
            queue = queue,
            settings = settings,
            errorMessage = error,
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
                    _errorMessage.value = null
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
                _errorMessage.value = error.message ?: localizationManager.currentStrings.value.transferErrorInvalidFtp
                return
            }

        val device = parsedDevice.copy(
            rootPath = uiState.value.settings.rootPath.ifBlank { "/mnt/ext1" },
        )

        _isConnecting.value = true
        connectionManager.disconnect()
        _ftpInput.value = device.ftpUrl
        _errorMessage.value = null

        viewModelScope.launch {
            ftpGateway.checkConnection(device)
                .onSuccess {
                    _isConnecting.value = false
                    connectionManager.connect(device)
                    _ftpInput.value = device.ftpUrl
                    _errorMessage.value = null
                }
                .onFailure { error ->
                    _isConnecting.value = false
                    connectionManager.disconnect()
                    _errorMessage.value = error.toFtpConnectionMessage(device)
                }
        }
    }

    fun disconnect() {
        _isConnecting.value = false
        connectionManager.disconnect()
    }

    fun addUris(uris: List<Uri>) {
        queueManager.addUris(uris)
    }

    fun removeItem(id: String) {
        queueManager.removeItem(id)
    }

    fun clearQueue() {
        queueManager.clearQueue()
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
        val snapshot = uiState.value
        val device = snapshot.connectedDevice
        if (device == null) {
            _errorMessage.value = localizationManager.currentStrings.value.transferErrorConnectBeforeUpload
            return
        }
        if (snapshot.queue.isEmpty()) {
            _errorMessage.value = localizationManager.currentStrings.value.transferErrorQueueEmpty
            return
        }

        val uploadableItems = snapshot.queue.filter {
            it.status == UploadStatus.Pending ||
                it.status == UploadStatus.Failed ||
                it.status == UploadStatus.Skipped
        }
        if (uploadableItems.isEmpty()) {
            _errorMessage.value = localizationManager.currentStrings.value.transferErrorNoPendingFiles
            return
        }

        val requestId = transferCoordinator.submit(
            device = device,
            items = uploadableItems.map { item ->
                TransferUploadItem(
                    id = item.id,
                    sourceUri = item.sourceUri,
                    originalName = item.originalName,
                    extension = item.extension,
                    category = item.category,
                    title = item.title,
                    mangaSeries = item.mangaSeries,
                    mangaVolume = item.mangaVolume,
                    plannedPath = item.plannedPath,
                )
            },
        )

        _activeTransferItemIds.value = uploadableItems.map { it.id }.toSet()
        _isTransferActive.value = true
        _errorMessage.value = null

        queueManager.updateQueue { current ->
            current.map { item ->
                if (uploadableItems.any { it.id == item.id }) {
                    item.copy(status = UploadStatus.Pending, progress = 0.0f)
                } else {
                    item
                }
            }
        }

        ContextCompat.startForegroundService(
            context,
            TransferForegroundService.createIntent(context, requestId),
        )
    }

    private fun handleTransferEvent(event: TransferEvent) {
        when (event) {
            is TransferEvent.ItemStarted -> {
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
                queueManager.updateQueue { current ->
                    current.map { item ->
                        if (item.id == event.itemId) {
                            item.copy(status = UploadStatus.Uploading, progress = event.progress)
                        } else {
                            item
                        }
                    }
                }
            }
            is TransferEvent.ItemUploaded -> {
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
                queueManager.updateQueue { current ->
                    current.map { item ->
                        if (item.id == event.itemId) {
                            item.copy(status = UploadStatus.Failed, progress = 0f)
                        } else {
                            item
                        }
                    }
                }
                _errorMessage.value = event.message
            }
            is TransferEvent.Completed -> {
                _isTransferActive.value = false
                _activeTransferItemIds.value = emptySet()
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

    private fun refreshRemoteFolderSuggestions(device: PocketBookDevice) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val tags = ftpGateway.listDirectories(device, settings.documentsFolderName).getOrDefault(emptyList())
            val series = ftpGateway.listDirectories(device, settings.mangaFolderName).getOrDefault(emptyList())
            _ftpSuggestions.value = Pair(tags, series)
        }
    }

    private fun Throwable.toFtpConnectionMessage(device: PocketBookDevice): String {
        val strings = localizationManager.currentStrings.value
        val reason = when (this) {
            is UnknownHostException -> strings.get("transfer_error_reason_host_unresolved", device.host)
            is ConnectException -> strings.get("transfer_error_reason_connection_refused", device.host, device.port)
            is SocketTimeoutException -> strings.get("transfer_error_reason_connection_timeout", device.host, device.port)
            else -> message ?: this::class.java.simpleName
        }
        return strings.get("transfer_error_cannot_connect", device.host, device.port, reason)
    }
}

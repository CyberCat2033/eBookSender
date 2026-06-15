package com.cybercat.pocketbooksender.transfer

import com.cybercat.pocketbooksender.data.pocketbook.PocketBookControlClient
import com.cybercat.pocketbooksender.model.PocketBookDevice
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class ConnectionManager @Inject constructor(
    private val controlClient: PocketBookControlClient,
) {
    private val _connectedDevice = MutableStateFlow<PocketBookDevice?>(null)
    val connectedDevice: StateFlow<PocketBookDevice?> = _connectedDevice.asStateFlow()

    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var appInForeground = true

    @Synchronized
    fun connect(device: PocketBookDevice) {
        monitorJob?.cancel()
        _connectedDevice.value = device
        startMonitorIfAllowed(device)
    }

    @Synchronized
    fun disconnect() {
        monitorJob?.cancel()
        monitorJob = null
        _connectedDevice.value = null
    }

    @Synchronized
    fun onAppForegrounded() {
        appInForeground = true
        val device = _connectedDevice.value ?: return
        startMonitorIfAllowed(device)
    }

    @Synchronized
    fun onAppBackgrounded() {
        appInForeground = false
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun startMonitorIfAllowed(device: PocketBookDevice) {
        if (!appInForeground || _connectedDevice.value != device || monitorJob?.isActive == true) return
        monitorJob = monitorScope.launch {
            monitorConnection(device)
        }
    }

    private suspend fun monitorConnection(device: PocketBookDevice) {
        while (kotlin.coroutines.coroutineContext.isActive) {
            delay(AliveIntervalMillis)
            if (!shouldMonitor(device)) return
            if (!isAliveWithRetries(device)) {
                if (shouldMonitor(device)) {
                    disconnectIfCurrent(device)
                }
                return
            }
        }
    }

    private suspend fun isAliveWithRetries(device: PocketBookDevice): Boolean {
        repeat(AliveAttempts) { attempt ->
            if (controlClient.isAlive(device)) {
                return true
            }
            if (attempt < AliveAttempts - 1) {
                delay(AliveRetryDelayMillis)
            }
        }
        return false
    }

    @Synchronized
    private fun shouldMonitor(device: PocketBookDevice): Boolean =
        appInForeground && _connectedDevice.value == device

    @Synchronized
    private fun disconnectIfCurrent(device: PocketBookDevice) {
        if (_connectedDevice.value == device) {
            monitorJob = null
            _connectedDevice.value = null
        }
    }

    private companion object {
        const val AliveIntervalMillis = 30_000L
        const val AliveRetryDelayMillis = 1_000L
        const val AliveAttempts = 3
    }
}

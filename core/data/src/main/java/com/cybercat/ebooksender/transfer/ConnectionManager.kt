package com.cybercat.ebooksender.transfer

import com.cybercat.ebooksender.model.RemoteDevice
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class ConnectionManager @Inject constructor() {
    private val _connectedDevice = MutableStateFlow<RemoteDevice?>(null)
    val connectedDevice: StateFlow<RemoteDevice?> = _connectedDevice.asStateFlow()

    fun connect(device: RemoteDevice) {
        _connectedDevice.value = device
    }

    fun disconnect() {
        _connectedDevice.value = null
    }

    fun disconnectIfCurrent(device: RemoteDevice) {
        _connectedDevice.update { current ->
            if (current == device) null else current
        }
    }
}

package com.cybercat.pocketbooksender.transfer

import com.cybercat.pocketbooksender.model.RemoteDevice
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ConnectionManager @Inject constructor() {
    private val _connectedDevice = MutableStateFlow<RemoteDevice?>(null)
    val connectedDevice: StateFlow<RemoteDevice?> = _connectedDevice.asStateFlow()

    @Synchronized
    fun connect(device: RemoteDevice) {
        _connectedDevice.value = device
    }

    @Synchronized
    fun disconnect() {
        _connectedDevice.value = null
    }
}

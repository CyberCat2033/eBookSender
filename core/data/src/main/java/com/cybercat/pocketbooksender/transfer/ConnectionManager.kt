package com.cybercat.pocketbooksender.transfer

import com.cybercat.pocketbooksender.model.PocketBookDevice
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ConnectionManager @Inject constructor() {
    private val _connectedDevice = MutableStateFlow<PocketBookDevice?>(null)
    val connectedDevice: StateFlow<PocketBookDevice?> = _connectedDevice.asStateFlow()

    fun connect(device: PocketBookDevice) {
        _connectedDevice.value = device
    }

    fun disconnect() {
        _connectedDevice.value = null
    }
}

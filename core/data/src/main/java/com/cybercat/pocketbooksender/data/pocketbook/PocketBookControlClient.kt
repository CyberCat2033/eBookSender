package com.cybercat.pocketbooksender.data.pocketbook

import com.cybercat.pocketbooksender.data.network.LocalDeviceNetworkProvider
import com.cybercat.pocketbooksender.model.RemoteDevice
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class PocketBookControlClient @Inject constructor(
    private val localDeviceNetworkProvider: LocalDeviceNetworkProvider
) {
    suspend fun requestDatabaseRescan(device: RemoteDevice): Result<Unit> =
        request(device = device, method = "POST", path = "rescan")

    private suspend fun request(device: RemoteDevice, method: String, path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection =
                    (
                        localDeviceNetworkProvider.openConnection(
                            device.controlUrl(path)
                        ) as HttpURLConnection
                        )
                        .apply {
                            requestMethod = method
                            connectTimeout = CONNECT_TIMEOUT_MILLIS
                            readTimeout = READ_TIMEOUT_MILLIS
                            useCaches = false
                            setRequestProperty("Cache-Control", "no-cache")
                            setRequestProperty("Accept", "application/json")
                        }

                try {
                    val code = connection.responseCode
                    runCatching {
                        if (code in 200..299) {
                            connection.inputStream
                        } else {
                            connection.errorStream
                        }?.close()
                    }
                    if (code !in 200..299) {
                        throw IOException("PocketBook control $method /$path failed: HTTP $code")
                    }
                } finally {
                    connection.disconnect()
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
        }

    private fun RemoteDevice.controlUrl(path: String): URL =
        URL("http", host, controlPort(), "/$path")

    private fun RemoteDevice.controlPort(): Int =
        (port + 1).takeIf { it in 1..MAX_PORT } ?: DEFAULT_CONTROL_PORT

    private companion object {
        const val DEFAULT_CONTROL_PORT = 2122
        const val MAX_PORT = 65_535
        const val CONNECT_TIMEOUT_MILLIS = 1_500
        const val READ_TIMEOUT_MILLIS = 2_000
    }
}

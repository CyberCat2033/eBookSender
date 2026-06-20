package com.cybercat.ebooksender.data.pocketbook

import com.cybercat.ebooksender.data.network.LocalDeviceNetworkProvider
import com.cybercat.ebooksender.data.network.requireLocalCleartextAddress
import com.cybercat.ebooksender.model.RemoteDevice
import com.cybercat.ebooksender.model.update.PocketBookServerVersionInfo
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class PocketBookControlClient @Inject constructor(
    private val localDeviceNetworkProvider: LocalDeviceNetworkProvider
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun requestDatabaseRescan(device: RemoteDevice): Result<Unit> =
        request(device = device, method = "POST", path = "rescan")

    suspend fun readVersion(device: RemoteDevice): Result<PocketBookServerVersionInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = sendControlRequest(
                    device = device,
                    method = "GET",
                    path = "version",
                    readTimeoutMillis = READ_TIMEOUT_MILLIS
                )
                if (response.code !in 200..299) {
                    throw IOException(
                        "PocketBook control GET /version failed: HTTP ${response.code}"
                    )
                }
                try {
                    json.decodeFromString(
                        PocketBookServerVersionInfo.serializer(),
                        response.body
                    )
                } catch (exception: SerializationException) {
                    throw IOException("Invalid PocketBook version response", exception)
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
        }

    suspend fun applyUpdate(
        device: RemoteDevice,
        request: PocketBookServerApplyUpdateRequest
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = json.encodeToString(request).toByteArray(Charsets.UTF_8)
            val response = sendControlRequest(
                device = device,
                method = "POST",
                path = "update",
                body = body,
                readTimeoutMillis = UPDATE_READ_TIMEOUT_MILLIS
            )
            if (response.code == HTTP_NOT_FOUND) {
                throw PocketBookUpdateEndpointUnavailableException()
            }
            if (response.code !in 200..299) {
                throw IOException("PocketBook control POST /update failed: HTTP ${response.code}")
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
        }
    }

    private suspend fun request(device: RemoteDevice, method: String, path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = sendControlRequest(
                    device = device,
                    method = method,
                    path = path,
                    readTimeoutMillis = READ_TIMEOUT_MILLIS
                )
                if (response.code !in 200..299) {
                    throw IOException(
                        "PocketBook control $method /$path failed: HTTP ${response.code}"
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
        }

    private suspend fun sendControlRequest(
        device: RemoteDevice,
        method: String,
        path: String,
        body: ByteArray? = null,
        readTimeoutMillis: Int
    ): ControlResponse {
        val address = device.resolveLocalControlAddress()
        return openControlSocket(address, device.controlPort(), readTimeoutMillis).use { socket ->
            val output = socket.getOutputStream()
            output.write(
                buildControlRequestHeaders(
                    device = device,
                    method = method,
                    path = path,
                    contentLength = body?.size
                ).toByteArray(StandardCharsets.US_ASCII)
            )
            if (body != null) {
                output.write(body)
            }
            output.flush()

            val input = BufferedInputStream(socket.getInputStream())
            val statusLine = input.readHttpLine()
            val code = statusLine.httpStatusCode()
            input.skipHttpHeaders()
            ControlResponse(
                code = code,
                body = input.readLimitedBody().toString(Charsets.UTF_8)
            )
        }
    }

    private suspend fun RemoteDevice.resolveLocalControlAddress(): InetAddress =
        requireLocalCleartextAddress(
            host = host,
            addresses = localDeviceNetworkProvider.resolveAllByName(host)
        )

    private suspend fun openControlSocket(
        address: InetAddress,
        port: Int,
        readTimeoutMillis: Int
    ): Socket {
        val socket = localDeviceNetworkProvider.socketFactory()?.createSocket() ?: Socket()
        try {
            socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MILLIS)
            socket.soTimeout = readTimeoutMillis
            return socket
        } catch (error: Throwable) {
            runCatching { socket.close() }
            throw error
        }
    }

    private fun buildControlRequestHeaders(
        device: RemoteDevice,
        method: String,
        path: String,
        contentLength: Int?
    ): String = buildString {
        append(method)
        append(" /")
        append(path.trimStart('/'))
        append(" HTTP/1.1\r\n")
        append("Host: ")
        append(device.hostHeader())
        append("\r\n")
        append("Connection: close\r\n")
        append("Cache-Control: no-cache\r\n")
        append("Accept: application/json\r\n")
        if (contentLength != null) {
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ")
            append(contentLength)
            append("\r\n")
        }
        append("\r\n")
    }

    private fun RemoteDevice.hostHeader(): String {
        val hostPart = if (host.contains(":") && !host.startsWith("[")) {
            "[$host]"
        } else {
            host
        }
        return "$hostPart:${controlPort()}"
    }

    private fun BufferedInputStream.skipHttpHeaders() {
        while (readHttpLine().isNotEmpty()) {
            // Skip headers until the empty delimiter line.
        }
    }

    private fun BufferedInputStream.readHttpLine(): String {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val byte = read()
            if (byte == -1) {
                if (buffer.size() == 0) throw IOException("Unexpected end of HTTP response")
                break
            }
            if (byte == '\n'.code) break
            if (byte != '\r'.code) {
                if (buffer.size() >= MAX_HTTP_LINE_BYTES) {
                    throw IOException("PocketBook control HTTP response line is too large")
                }
                buffer.write(byte)
            }
        }
        return buffer.toString(StandardCharsets.US_ASCII.name())
    }

    private fun String.httpStatusCode(): Int {
        val parts = split(' ', limit = 3)
        if (parts.size < 2 || !parts[0].startsWith("HTTP/")) {
            throw IOException("Invalid PocketBook control HTTP response")
        }
        return parts[1].toIntOrNull()
            ?: throw IOException("Invalid PocketBook control HTTP status code")
    }

    private fun BufferedInputStream.readLimitedBody(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            totalBytes += read
            if (totalBytes > MAX_CONTROL_RESPONSE_BYTES) {
                throw IOException("PocketBook control HTTP response body is too large")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun RemoteDevice.controlPort(): Int =
        (port + 1).takeIf { it in 1..MAX_PORT } ?: DEFAULT_CONTROL_PORT

    private data class ControlResponse(val code: Int, val body: String)

    private companion object {
        const val DEFAULT_CONTROL_PORT = 2122
        const val MAX_PORT = 65_535
        const val CONNECT_TIMEOUT_MILLIS = 1_500
        const val READ_TIMEOUT_MILLIS = 2_000
        const val UPDATE_READ_TIMEOUT_MILLIS = 10_000
        const val HTTP_NOT_FOUND = 404
        const val MAX_HTTP_LINE_BYTES = 8 * 1024
        const val MAX_CONTROL_RESPONSE_BYTES = 64 * 1024
    }
}

@Serializable
data class PocketBookServerApplyUpdateRequest(
    @SerialName("sourcePath")
    val sourcePath: String,
    @SerialName("versionName")
    val versionName: String,
    @SerialName("versionCode")
    val versionCode: Long,
    @SerialName("releasedAt")
    val releasedAt: String,
    @SerialName("buildId")
    val buildId: String? = null,
    @SerialName("sha256")
    val sha256: String
)

class PocketBookUpdateEndpointUnavailableException :
    IOException("PocketBook update endpoint is unavailable")

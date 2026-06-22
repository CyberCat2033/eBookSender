package com.cybercat.ebooksender.ui

import com.cybercat.ebooksender.localization.AppStrings
import com.cybercat.ebooksender.model.RemoteDevice
import com.cybercat.ebooksender.network.LocalNetworkBypassUnavailableException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FtpErrorMapperTest {
    private val mapper = FtpErrorMapper()
    private val device = RemoteDevice(host = "192.168.1.50", port = 2121)

    private val fakeStrings = AppStrings(
        languageCode = "en",
        languageName = "English",
        translationMap = mapOf(
            "transfer_error_cannot_connect" to "Cannot connect to %s:%d: %s",
            "transfer_error_reason_vpn_bypass_blocked" to "VPN bypass blocked",
            "transfer_error_reason_host_unresolved" to "Host unresolved for %s",
            "transfer_error_reason_connection_timeout" to "Timeout for %s:%d",
            "transfer_error_reason_connection_refused" to "Refused for %s:%d",
            "transfer_error_invalid_ftp_empty" to "FTP URL is empty",
            "transfer_error_invalid_ftp_scheme" to "Only ftp:// links are supported",
            "transfer_error_invalid_ftp_host" to "FTP host is missing"
        ),
        fallbackMap = emptyMap()
    )

    @Test
    fun mapConnectionError_vpnBypassBlocked() {
        val error = LocalNetworkBypassUnavailableException()
        val result = mapper.mapConnectionError(error, device, fakeStrings)
        assertEquals("Cannot connect to 192.168.1.50:2121: VPN bypass blocked", result)
    }

    @Test
    fun mapConnectionError_unknownHost() {
        val error = IOException("connection failed", UnknownHostException("dns error"))
        val result = mapper.mapConnectionError(error, device, fakeStrings)
        assertEquals(
            "Cannot connect to 192.168.1.50:2121: Host unresolved for 192.168.1.50",
            result
        )
    }

    @Test
    fun mapConnectionError_socketTimeout() {
        val error = SocketTimeoutException("read timed out")
        val result = mapper.mapConnectionError(error, device, fakeStrings)
        assertEquals("Cannot connect to 192.168.1.50:2121: Timeout for 192.168.1.50:2121", result)
    }

    @Test
    fun mapConnectionError_interruptedIOException() {
        val error = InterruptedIOException("interrupted")
        val result = mapper.mapConnectionError(error, device, fakeStrings)
        assertEquals("Cannot connect to 192.168.1.50:2121: Timeout for 192.168.1.50:2121", result)
    }

    @Test
    fun mapConnectionError_connectException() {
        val error = ConnectException("connection refused")
        val result = mapper.mapConnectionError(error, device, fakeStrings)
        assertEquals("Cannot connect to 192.168.1.50:2121: Refused for 192.168.1.50:2121", result)
    }

    @Test
    fun mapConnectionError_noRouteToHostException() {
        val error = NoRouteToHostException("no route")
        val result = mapper.mapConnectionError(error, device, fakeStrings)
        assertEquals("Cannot connect to 192.168.1.50:2121: Refused for 192.168.1.50:2121", result)
    }

    @Test
    fun mapConnectionError_otherException() {
        val error = RuntimeException("Some random error")
        val result = mapper.mapConnectionError(error, device, fakeStrings)
        assertEquals("Cannot connect to 192.168.1.50:2121: Some random error", result)
    }

    @Test
    fun mapInvalidFtpError_cases() {
        assertEquals(
            "FTP URL is empty",
            mapper.mapInvalidFtpError(RuntimeException("FTP URL is empty"), fakeStrings)
        )
        assertEquals(
            "Only ftp:// links are supported",
            mapper.mapInvalidFtpError(
                RuntimeException("Only ftp:// links are supported"),
                fakeStrings
            )
        )
        assertEquals(
            "FTP host is missing",
            mapper.mapInvalidFtpError(RuntimeException("FTP host is missing"), fakeStrings)
        )
    }
}

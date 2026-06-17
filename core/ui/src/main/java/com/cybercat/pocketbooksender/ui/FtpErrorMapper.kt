package com.cybercat.pocketbooksender.ui

import com.cybercat.pocketbooksender.localization.AppStrings
import com.cybercat.pocketbooksender.model.PocketBookDevice
import com.cybercat.pocketbooksender.network.isLocalNetworkBypassBlocked
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FtpErrorMapper @Inject constructor() {
    fun mapConnectionError(
        error: Throwable,
        device: PocketBookDevice,
        strings: AppStrings
    ): String {
        val causes = error.causalChain()
        val reason = when {
            error.isLocalNetworkBypassBlocked() ->
                strings.get("transfer_error_reason_vpn_bypass_blocked")

            causes.any { it is UnknownHostException } ->
                strings.get("transfer_error_reason_host_unresolved", device.host)

            causes.any {
                it is SocketTimeoutException || it is InterruptedIOException ||
                    it.message.isTimeoutMessage()
            } ->
                strings.get("transfer_error_reason_connection_timeout", device.host, device.port)

            causes.any {
                it is ConnectException || it is NoRouteToHostException ||
                    it.message.isConnectionRefusedMessage()
            } ->
                strings.get("transfer_error_reason_connection_refused", device.host, device.port)

            else -> error.message ?: error::class.java.simpleName
        }
        return strings.get("transfer_error_cannot_connect", device.host, device.port, reason)
    }

    fun mapInvalidFtpError(error: Throwable, strings: AppStrings): String = when (error.message) {
        "FTP URL is empty" -> strings.get("transfer_error_invalid_ftp_empty")
        "Only ftp:// links are supported" -> strings.get("transfer_error_invalid_ftp_scheme")
        "FTP host is missing" -> strings.get("transfer_error_invalid_ftp_host")
        else -> strings.transferErrorInvalidFtp
    }

    private fun Throwable.causalChain(): List<Throwable> = buildList {
        val seen = mutableSetOf<Throwable>()
        var current: Throwable? = this@causalChain
        while (current != null && seen.add(current)) {
            add(current)
            current = current.cause
        }
    }

    private fun String?.isTimeoutMessage(): Boolean {
        val text = this?.lowercase() ?: return false
        return "timeout" in text || "timed out" in text
    }

    private fun String?.isConnectionRefusedMessage(): Boolean {
        val text = this?.lowercase() ?: return false
        return "connection refused" in text || "failed to connect" in text ||
            "no route to host" in text
    }
}

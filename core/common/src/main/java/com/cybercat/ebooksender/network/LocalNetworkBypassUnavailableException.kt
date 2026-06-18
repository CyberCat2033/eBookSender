package com.cybercat.ebooksender.network

import java.io.IOException

class LocalNetworkBypassUnavailableException(cause: Throwable? = null) :
    IOException("Active VPN does not allow local network bypass", cause)

fun Throwable.isLocalNetworkBypassBlocked(): Boolean = causalChain().any {
    it is LocalNetworkBypassUnavailableException ||
        it.message.isLocalNetworkBypassBlockedMessage()
}

private fun Throwable.causalChain(): List<Throwable> = buildList {
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this@causalChain
    while (current != null && seen.add(current)) {
        add(current)
        current = current.cause
    }
}

private fun String?.isLocalNetworkBypassBlockedMessage(): Boolean {
    val text = this?.lowercase() ?: return false
    return "binding socket to network" in text && "eperm" in text
}

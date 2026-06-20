package com.cybercat.ebooksender.data.network

import java.io.IOException
import java.net.InetAddress

fun requireLocalCleartextAddress(host: String, addresses: Array<InetAddress>): InetAddress =
    addresses.firstOrNull(InetAddress::isPrivateOrLocalAddress)
        ?: throw IOException(
            "Cleartext local device HTTP is only allowed for private or local hosts: $host"
        )

fun InetAddress.isPrivateOrLocalAddress(): Boolean = isAnyLocalAddress ||
    isLoopbackAddress ||
    isLinkLocalAddress ||
    isSiteLocalAddress ||
    isUniqueLocalIpv6()

private fun InetAddress.isUniqueLocalIpv6(): Boolean {
    val firstByte = address.firstOrNull()?.toInt() ?: return false
    return firstByte and IPV6_UNIQUE_LOCAL_MASK == IPV6_UNIQUE_LOCAL_PREFIX
}

private const val IPV6_UNIQUE_LOCAL_MASK = 0xfe
private const val IPV6_UNIQUE_LOCAL_PREFIX = 0xfc

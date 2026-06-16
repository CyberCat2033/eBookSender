package com.cybercat.pocketbooksender.network

import java.io.IOException

class LocalNetworkBypassUnavailableException(
    cause: Throwable? = null,
) : IOException("Active VPN does not allow local network bypass", cause)

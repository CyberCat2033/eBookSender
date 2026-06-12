package com.cybercat.pocketbooksender.model

data class PocketBookDevice(
    val host: String,
    val port: Int = 2121,
    val username: String = "anonymous",
    val rootPath: String = "/mnt/ext1",
) {
    val ftpUrl: String
        get() = "ftp://$username@$host:$port/"
}

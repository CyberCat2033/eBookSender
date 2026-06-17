package com.cybercat.pocketbooksender.model

data class PocketBookDevice(
    val host: String,
    val port: Int = 2121,
    val username: String = "anonymous",
    val rootPath: String = DEFAULT_FTP_ROOT_PATH,
    val relativeRootPath: String = DEFAULT_FTP_RELATIVE_ROOT_PATH,
    val supportsFolderRename: FolderRenameMethod = FolderRenameMethod.FtpOnly,
    val supportsRescan: Boolean = true
) {
    val workingRootPath: String
        get() = combineFtpRootPath(rootPath, relativeRootPath)

    val ftpUrl: String
        get() {
            val path = normalizeFtpRootPath(rootPath)
            return "ftp://$username@$host:$port$path"
        }
}

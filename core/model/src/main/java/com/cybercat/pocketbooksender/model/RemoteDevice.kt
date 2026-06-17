package com.cybercat.pocketbooksender.model

data class RemoteDevice(
    val host: String,
    val port: Int = 2121,
    val username: String = "anonymous",
    val rootPath: String = DEFAULT_FTP_ROOT_PATH,
    val relativeRootPath: String = DEFAULT_FTP_RELATIVE_ROOT_PATH,
    val profile: DeviceProfile = DeviceProfile.GenericFtp,
    val supportsFolderRename: FolderRenameMethod = FolderRenameMethod.FtpOnly,
    val supportsRescan: Boolean = false
) {
    val workingRootPath: String
        get() = combineFtpRootPath(rootPath, relativeRootPath)

    val ftpUrl: String
        get() {
            val path = normalizeFtpRootPath(rootPath)
            return "ftp://$username@$host:$port$path"
        }

    fun withProfile(profile: DeviceProfile): RemoteDevice = copy(
        profile = profile,
        supportsFolderRename = profile.defaultFolderRenameMethod,
        supportsRescan = profile.supportsLibraryRescan
    )
}

enum class DeviceProfile {
    PocketBook,
    GenericFtp
}

val DeviceProfile.supportsLibraryRescan: Boolean
    get() = this == DeviceProfile.PocketBook

val DeviceProfile.defaultFolderRenameMethod: FolderRenameMethod
    get() = when (this) {
        DeviceProfile.PocketBook,
        DeviceProfile.GenericFtp -> FolderRenameMethod.FtpOnly
    }

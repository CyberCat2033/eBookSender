package com.cybercat.pocketbooksender.model

enum class FolderManagementSupport {
    Supported,
    Unsupported,
    Unknown
}

enum class FolderRenameMethod {
    FtpOnly,
    None
}

data class DeviceFolderCapabilities(
    val folderManagementSupport: FolderManagementSupport = FolderManagementSupport.Unknown,
    val renameMethod: FolderRenameMethod = FolderRenameMethod.FtpOnly,
    val supportsRescan: Boolean = true
)

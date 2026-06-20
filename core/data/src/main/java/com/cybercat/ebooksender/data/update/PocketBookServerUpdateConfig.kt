package com.cybercat.ebooksender.data.update

internal object PocketBookServerUpdateConfig {
    const val UPDATE_MANIFEST_URL = "https://cybercat2033.github.io/pb-ftp/updates/latest.json"
    const val UPDATE_CACHE_DIR = "pocketbook-server-updates"
    const val CHANGELOG_CACHE_DIR = "pocketbook-server-update-changelogs"
    const val POCKETBOOK_SERVER_APP_NAME = "pb-ftp"
    const val LAUNCHER_ARTIFACT_TYPE = "launcher"
    const val EXPECTED_LAUNCHER_INSTALL_PATH = "/mnt/ext1/applications/pb-ftp.app"
    const val POCKETBOOK_MOUNT_ROOT_WITH_SLASH = "/mnt/ext1/"
    const val STAGING_REMOTE_DIR = "applications/.pb-ftp-update"
    const val CONNECT_TIMEOUT_MS = 10_000
    const val READ_TIMEOUT_MS = 10_000
    const val DOWNLOAD_READ_TIMEOUT_MS = 30_000
    const val MAX_MANIFEST_BYTES = 512 * 1024
    const val STATUS_AUTO_CLEAR_MS = 3_000L
    const val UPDATE_VERSION_POLL_ATTEMPTS = 12
    const val UPDATE_VERSION_POLL_DELAY_MS = 500L
    const val USER_AGENT = "eBookSender"
    val SHA256_PATTERN = Regex("^[a-fA-F0-9]{64}$")
}

internal class PocketBookServerUpdateException(
    val reason: PocketBookServerUpdateErrorReason,
    cause: Throwable? = null
) : Exception(cause)

package com.cybercat.ebooksender.update

import com.cybercat.ebooksender.BuildConfig
import com.cybercat.ebooksender.data.update.AppUpdateErrorReason

internal object AppUpdateConfig {
    const val UPDATE_CACHE_DIR = "update-apks"
    const val CHANGELOG_CACHE_DIR = "update-changelogs"
    const val UNIVERSAL_ABI = "universal"
    const val CONNECT_TIMEOUT_MS = 10_000
    const val READ_TIMEOUT_MS = 10_000
    const val DOWNLOAD_READ_TIMEOUT_MS = 30_000
    const val MAX_MANIFEST_BYTES = 512 * 1024
    const val STATUS_AUTO_CLEAR_MS = 3_000L
    const val USER_AGENT = "eBookSender/${BuildConfig.VERSION_NAME}"
    val SHA256_PATTERN = Regex("^[a-fA-F0-9]{64}$")
}

internal class AppUpdateException(val reason: AppUpdateErrorReason, cause: Throwable? = null) :
    Exception(cause)

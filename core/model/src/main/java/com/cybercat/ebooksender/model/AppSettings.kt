package com.cybercat.ebooksender.model

const val DEFAULT_FTP_ROOT_PATH = "/"
const val DEFAULT_FTP_RELATIVE_ROOT_PATH = ""

data class AppSettings(
    val rootPath: String = DEFAULT_FTP_RELATIVE_ROOT_PATH,
    val booksFolderName: String = "Books",
    val documentsFolderName: String = "Documents",
    val mangaFolderName: String = "Manga",
    val defaultDocumentsTag: String = "Untagged",
    val defaultMangaSeries: String = "Unknown_Series",
    val bookFileNameTemplate: String = "{title}",
    val documentsFileNameTemplate: String = "{title}",
    val mangaFileNameTemplate: String = "{series}_{volume}",
    val conflictStrategy: ConflictStrategy = ConflictStrategy.Ask,
    val useDynamicColor: Boolean = true,
    val enableHaptics: Boolean = true,
    val bypassVpnForLocalConnections: Boolean = false,
    val mangaLoginMode: MangaLoginMode = MangaLoginMode.Ask,
    val theme: AppTheme = AppTheme.System,
    val warnOnDisconnectedRename: Boolean = true,
    val languageCode: String = "system"
)

fun normalizeFtpRootPath(value: String): String {
    val normalized = value.trim().replace('\\', '/')
    if (normalized.isBlank()) return DEFAULT_FTP_ROOT_PATH

    val segments = normalized
        .split('/')
        .filter { it.isNotBlank() }
    if (segments.isEmpty()) return "/"
    if (segments.any { it == "." || it == ".." }) return DEFAULT_FTP_ROOT_PATH

    return "/" + segments.joinToString("/")
}

fun normalizeFtpRelativeRootPath(value: String): String {
    val normalized = value.trim().replace('\\', '/')
    if (normalized.isBlank()) return DEFAULT_FTP_RELATIVE_ROOT_PATH

    val segments = normalized
        .split('/')
        .filter { it.isNotBlank() }
    if (segments.isEmpty()) return DEFAULT_FTP_RELATIVE_ROOT_PATH
    if (segments.any { it == "." || it == ".." }) return DEFAULT_FTP_RELATIVE_ROOT_PATH

    return segments.joinToString("/")
}

fun combineFtpRootPath(rootPath: String, relativeRootPath: String): String {
    val root = normalizeFtpRootPath(rootPath)
    val relativeRoot = normalizeFtpRelativeRootPath(relativeRootPath)
    if (relativeRoot.isBlank()) return root

    return if (root == DEFAULT_FTP_ROOT_PATH) {
        "$DEFAULT_FTP_ROOT_PATH$relativeRoot"
    } else {
        "${root.trimEnd('/')}/$relativeRoot"
    }
}

enum class ConflictStrategy {
    Ask,
    Replace,
    Rename,
    Skip
}

enum class AppTheme {
    Light,
    Dark,
    System
}

enum class MangaLoginMode {
    Ask,
    WebView,
    Native
}

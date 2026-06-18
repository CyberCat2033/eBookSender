package com.cybercat.ebooksender.data.ftp

internal fun String.toSafeRelativeFtpPath(): String {
    val trimmed = replace('\\', '/').trim()
    require(trimmed.isNotBlank()) { "FTP path is empty" }
    require(!trimmed.startsWith("/")) { "FTP path must be relative" }

    val segments =
        trimmed
            .split('/')
            .filter { it.isNotBlank() }
    require(segments.none { it == "." || it == ".." }) {
        "FTP path must not traverse directories"
    }

    return segments.joinToString("/")
}

internal fun String.toSafeRelativeFtpPathOrBlank(): String {
    val normalized = replace('\\', '/').trim()
    if (normalized.isBlank()) return ""
    return normalized.toSafeRelativeFtpPath()
}

internal fun String.toSafeFtpEntryNameOrNull(): String? {
    if (isBlank()) return null
    if (this == "." || this == "..") return null
    if ('/' in this || '\\' in this) return null
    if (any { Character.isISOControl(it.code) }) return null
    return this
}

internal fun buildSafeChildFtpPath(parentRelativePath: String, childName: String): String? {
    val safeParent = parentRelativePath.toSafeRelativeFtpPathOrBlank()
    val safeChild = childName.toSafeFtpEntryNameOrNull() ?: return null
    return if (safeParent.isBlank()) {
        safeChild
    } else {
        "$safeParent/$safeChild"
    }
}

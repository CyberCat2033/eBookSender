package com.cybercat.pocketbooksender.domain

private val UnsafePathChars = Regex("""[\\/:*?"<>|]+""")
private val Whitespace = Regex("""\s+""")
private val RepeatedUnderscores = Regex("""_+""")

object FilenameSanitizer {
    fun directoryName(value: String?, fallback: String): String {
        return directoryNameOrNull(value) ?: fallback
    }

    fun directoryNameOrNull(value: String?): String? {
        return sanitize(
            value = value,
            whitespaceReplacement = " ",
            trimChars = charArrayOf(' ', '.'),
            collapseUnderscores = false
        )
    }

    fun fileTitle(value: String?, fallback: String): String {
        return fileTitleOrNull(value) ?: fallback
    }

    fun fileTitleOrNull(value: String?): String? {
        return sanitize(
            value = value,
            whitespaceReplacement = "_",
            trimChars = charArrayOf('_', '.'),
            collapseUnderscores = true
        )
    }

    private fun sanitize(
        value: String?,
        whitespaceReplacement: String,
        trimChars: CharArray,
        collapseUnderscores: Boolean
    ): String? {
        var normalized = value
            .orEmpty()
            .trim()
            .replace(UnsafePathChars, "_")
            .replace(Whitespace, whitespaceReplacement)

        if (collapseUnderscores) {
            normalized = normalized.replace(RepeatedUnderscores, "_")
        }

        val trimmed = normalized.trim { it in trimChars }
        return trimmed.ifBlank { null }
    }
}

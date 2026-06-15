package com.cybercat.pocketbooksender.domain

private val UnsafePathChars = Regex("""[\\/:*?"<>|]+""")
private val Whitespace = Regex("""\s+""")
private val RepeatedUnderscores = Regex("""_+""")

object FilenameSanitizer {
    fun directoryName(value: String?, fallback: String): String {
        val normalized = value
            .orEmpty()
            .trim()
            .replace(UnsafePathChars, "_")
            .replace(Whitespace, " ")
            .trim(' ', '.')

        return normalized.ifBlank { fallback }
    }

    fun fileTitle(value: String?, fallback: String): String {
        val normalized = value
            .orEmpty()
            .trim()
            .replace(UnsafePathChars, "_")
            .replace(Whitespace, "_")
            .replace(RepeatedUnderscores, "_")
            .trim('_', '.')

        return normalized.ifBlank { fallback }
    }
}

package com.cybercat.ebooksender.data.update

object UpdateChangelogFormatter {
    private val secondLevelHeadingPattern = Regex("^##\\s+.*$")

    fun extractVersionChangelog(markdown: String, versionName: String): String {
        val lines = markdown.lineSequence().toList()
        val versionHeading = Regex(
            "^##\\s+\\[?v?${Regex.escape(versionName)}(?:]|\\b).*$",
            option = RegexOption.IGNORE_CASE
        )
        val unreleasedHeading = Regex(
            pattern = "^##\\s+\\[?Unreleased(?:]|\\b).*$",
            option = RegexOption.IGNORE_CASE
        )
        val changelog = findFormattedSection(lines, versionHeading)
            ?: findFormattedSection(lines, unreleasedHeading)
            ?: formatChangelogSection(
                lines.dropWhile { line ->
                    val trimmed = line.trim()
                    trimmed.isEmpty() || trimmed.startsWith("#")
                }
            )

        return changelog
    }

    private fun findFormattedSection(lines: List<String>, headingPattern: Regex): String? {
        val startIndex = lines.indexOfFirst { line ->
            headingPattern.matches(line.trim())
        }
        if (startIndex < 0) return null

        return formatChangelogSection(
            lines.drop(startIndex + 1)
                .takeWhile { line -> !line.trim().matches(secondLevelHeadingPattern) }
        ).takeIf { it.isNotBlank() }
    }

    private fun formatChangelogSection(lines: List<String>): String =
        lines.joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("###") -> trimmed.trimStart('#').trim()
                trimmed.startsWith("- ") -> "- ${trimmed.drop(2)}"
                trimmed.startsWith("* ") -> "- ${trimmed.drop(2)}"
                else -> trimmed
            }
        }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
}

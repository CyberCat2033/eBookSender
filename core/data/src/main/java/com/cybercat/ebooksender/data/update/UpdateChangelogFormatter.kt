package com.cybercat.ebooksender.data.update

object UpdateChangelogFormatter {
    private val secondLevelHeadingPattern = Regex("^##\\s+.*$")

    fun extractVersionChangelog(markdown: String, versionName: String): String {
        val lines = markdown.lineSequence().toList()
        val versionHeading = Regex(
            "^##\\s+\\[?${Regex.escape(versionName)}(?:]|\\b).*$"
        )
        val unreleasedHeading = Regex(
            pattern = "^##\\s+\\[?Unreleased(?:]|\\b).*$",
            option = RegexOption.IGNORE_CASE
        )
        val startIndex = lines.indexOfFirst { line ->
            val trimmed = line.trim()
            versionHeading.matches(trimmed) || unreleasedHeading.matches(trimmed)
        }

        val sectionLines = if (startIndex >= 0) {
            lines.drop(startIndex + 1)
                .takeWhile { line -> !line.trim().matches(secondLevelHeadingPattern) }
        } else {
            lines.dropWhile { line ->
                val trimmed = line.trim()
                trimmed.isEmpty() || trimmed.startsWith("#")
            }
        }

        return formatChangelogSection(sectionLines)
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

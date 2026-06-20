package com.cybercat.ebooksender.data.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateChangelogFormatterTest {

    @Test
    fun extractVersionChangelog_prioritizesVersionHeadingOverUnreleased() {
        val markdown = """
            # Changelog
            
            ## [Unreleased]
            
            ### Added
            - Added something new that is unreleased
            
            ## [0.11.5] - 2026-06-20
            
            ### Added
            - Fixed dynamic color toggle
            - Optimized manga download RAM footprint
            
            ## [0.11.4] - 2026-06-15
            - Old fix
        """.trimIndent()

        val changelog = UpdateChangelogFormatter.extractVersionChangelog(markdown, "0.11.5")

        val expected = """
            Added
            - Fixed dynamic color toggle
            - Optimized manga download RAM footprint
        """.trimIndent()

        assertEquals(expected, changelog)
    }

    @Test
    fun extractVersionChangelog_fallsBackToUnreleasedIfVersionNotFound() {
        val markdown = """
            # Changelog
            
            ## [Unreleased]
            
            ### Added
            - Unreleased feature
            
            ## [0.11.4] - 2026-06-15
            - Old fix
        """.trimIndent()

        val changelog = UpdateChangelogFormatter.extractVersionChangelog(markdown, "0.11.5")

        val expected = """
            Added
            - Unreleased feature
        """.trimIndent()

        assertEquals(expected, changelog)
    }
}

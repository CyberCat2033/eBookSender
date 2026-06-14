package com.cybercat.pocketbooksender.model

data class AppSettings(
    val rootPath: String = "/mnt/ext1",
    val defaultProgrammingTag: String = "Untagged",
    val defaultMangaSeries: String = "Unknown_Series",
    val bookFileNameTemplate: String = "{title}",
    val programmingFileNameTemplate: String = "{title}",
    val mangaFileNameTemplate: String = "{volume}",
    val conflictStrategy: ConflictStrategy = ConflictStrategy.Ask,
    val useDynamicColor: Boolean = true,
    val enableHaptics: Boolean = true,
)

enum class ConflictStrategy {
    Ask,
    Replace,
    Rename,
    Skip,
}

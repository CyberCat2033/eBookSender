package com.cybercat.pocketbooksender.model

data class AppSettings(
    val rootPath: String = "/mnt/ext1",
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
    val theme: AppTheme = AppTheme.System,
)

enum class ConflictStrategy {
    Ask,
    Replace,
    Rename,
    Skip,
}

enum class AppTheme {
    Light,
    Dark,
    System,
}

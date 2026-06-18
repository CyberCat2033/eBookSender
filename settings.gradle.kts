pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "EBookSender"
include(
    ":app",
    ":core:model",
    ":core:common",
    ":core:domain",
    ":core:database",
    ":core:datastore",
    ":core:network",
    ":core:data",
    ":core:ui",
    ":feature:catalog",
    ":feature:manga",
    ":feature:opds",
    ":feature:settings",
    ":feature:transfer"
)

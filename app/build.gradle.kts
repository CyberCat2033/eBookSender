import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun releaseSigningProperty(name: String): String? = localProperties.getProperty(name)
    ?: providers.gradleProperty(name).orNull
    ?: providers.environmentVariable(name).orNull

val releaseStoreFile = releaseSigningProperty("RELEASE_STORE_FILE") ?: "release.keystore"
val releaseStorePassword = releaseSigningProperty("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningProperty("RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningProperty("RELEASE_KEY_PASSWORD")
val enableAbiSplits = providers.gradleProperty("enableAbiSplits")
    .map(String::toBoolean)
    .orElse(false)
val releaseAbiFilters = listOf("arm64-v8a", "armeabi-v7a")

/**
 * Запускает `git` с аргументами [args] в корне проекта и возвращает stdout,
 * либо [fallback], если git недоступен (например, при сборке из исходников без .git).
 */
fun gitCommand(args: List<String>, fallback: String): String = runCatching {
    providers.exec {
        commandLine("git", *args.toTypedArray())
        workingDir = rootProject.projectDir
    }.standardOutput.asText.get().trim()
}.getOrNull()?.takeIf { it.isNotEmpty() } ?: fallback

/**
 * versionName из последнего релизного тега вида `vX.Y.Z` (без ведущего `v`).
 * Фолбэк на `0.1.0` для дев-сборок без релизного тега или вне git-репозитория.
 *
 * Сначала проверяем наличие релизных тегов через `git tag --list v[0-9]*` (всегда exit 0),
 * чтобы не запускать `git describe`, который падает с non-zero exit code,
 * если тегов ещё нет — это сломало бы configuration cache.
 */
fun gitVersionName(): String {
    val releaseTagPattern = "v[0-9]*"
    val hasReleaseTags = gitCommand(
        listOf("tag", "--list", releaseTagPattern),
        fallback = ""
    ).isNotEmpty()
    if (!hasReleaseTags) return "0.1.0"
    return gitCommand(
        listOf("describe", "--tags", "--match", releaseTagPattern, "--abbrev=0"),
        fallback = "0.1.0"
    )
        .removePrefix("v")
        .removePrefix("V")
}

/**
 * Монотонно растущий versionCode из числа коммитов текущей ветки.
 * Фолбэк на `1`, чтобы дев-сборки без git оставались инсталлируемыми.
 */
fun gitVersionCode(): String = gitCommand(listOf("rev-list", "--count", "HEAD"), fallback = "1")

android {
    namespace = "com.cybercat.ebooksender"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cybercat.ebooksender"
        minSdk = 26
        targetSdk = 35
        versionCode = gitVersionCode().toInt()
        versionName = gitVersionName()
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"https://cybercat2033.github.io/eBookSender/updates/latest.json\""
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    splits {
        abi {
            isEnable = enableAbiSplits.get()
            reset()
            include(*releaseAbiFilters.toTypedArray())
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(releaseStoreFile)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.commons.net)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.junrar)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.code.scanner)

    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.compose.ui.tooling)
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:catalog"))
    implementation(project(":feature:manga"))
    implementation(project(":feature:opds"))
    implementation(project(":feature:transfer"))
}

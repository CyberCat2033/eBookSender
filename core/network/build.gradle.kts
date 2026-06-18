plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cybercat.ebooksender.core.network"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.jsoup)
    implementation(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)

    testImplementation(libs.junit4)
    testImplementation(libs.json)
}

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cybercat.ebooksender.core.domain"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.hilt.android)
}

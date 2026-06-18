plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cybercat.ebooksender.core.data"
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
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(libs.commons.net)
    implementation(libs.hilt.android)

    testImplementation(libs.junit4)
}

package com.cybercat.ebooksender.model.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateManifest(
    @SerialName("schemaVersion")
    val schemaVersion: Int = 1,
    @SerialName("packageName")
    val packageName: String,
    @SerialName("versionName")
    val versionName: String,
    @SerialName("versionCode")
    val versionCode: Long,
    @SerialName("minSdk")
    val minSdk: Int,
    @SerialName("releasedAt")
    val releasedAt: String,
    @SerialName("changelogUrl")
    val changelogUrl: String? = null,
    @SerialName("artifacts")
    val artifacts: List<AppUpdateArtifact>
)

@Serializable
data class AppUpdateArtifact(
    @SerialName("abi")
    val abi: String,
    @SerialName("fileName")
    val fileName: String,
    @SerialName("url")
    val url: String,
    @SerialName("sha256")
    val sha256: String,
    @SerialName("sizeBytes")
    val sizeBytes: Long? = null
)

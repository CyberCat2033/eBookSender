package com.cybercat.ebooksender.model.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PocketBookServerUpdateManifest(
    @SerialName("schemaVersion")
    val schemaVersion: Int = 1,
    @SerialName("appName")
    val appName: String,
    @SerialName("versionName")
    val versionName: String,
    @SerialName("versionCode")
    val versionCode: Long,
    @SerialName("buildId")
    val buildId: String? = null,
    @SerialName("releasedAt")
    val releasedAt: String,
    @SerialName("changelogUrl")
    val changelogUrl: String? = null,
    @SerialName("changelogUrls")
    val changelogUrls: Map<String, String> = emptyMap(),
    @SerialName("artifacts")
    val artifacts: List<PocketBookServerUpdateArtifact>
)

@Serializable
data class PocketBookServerUpdateArtifact(
    @SerialName("type")
    val type: String,
    @SerialName("fileName")
    val fileName: String,
    @SerialName("installPath")
    val installPath: String? = null,
    @SerialName("url")
    val url: String,
    @SerialName("sha256")
    val sha256: String,
    @SerialName("sizeBytes")
    val sizeBytes: Long? = null
)

@Serializable
data class PocketBookServerVersionInfo(
    @SerialName("schemaVersion")
    val schemaVersion: Int = 1,
    @SerialName("appName")
    val appName: String,
    @SerialName("versionName")
    val versionName: String,
    @SerialName("versionCode")
    val versionCode: Long,
    @SerialName("buildId")
    val buildId: String? = null,
    @SerialName("releasedAt")
    val releasedAt: String
)

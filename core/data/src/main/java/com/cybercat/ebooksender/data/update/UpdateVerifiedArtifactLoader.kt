package com.cybercat.ebooksender.data.update

import java.io.File

class UpdateVerifiedArtifactLoader(private val artifactDownloader: UpdateArtifactDownloader) {
    suspend fun getVerifiedArtifact(request: UpdateVerifiedArtifactRequest): File {
        val expectedHash = request.sha256.lowercase()
        if (request.target.isFile && request.target.updateFileSha256() == expectedHash) {
            request.afterVerified(request.target)
            return request.target
        }

        runCatching { request.target.delete() }
        request.beforeDownload()
        artifactDownloader.download(
            UpdateArtifactDownloadRequest(
                url = request.url,
                target = request.target,
                userAgent = request.userAgent,
                connectTimeoutMs = request.connectTimeoutMs,
                readTimeoutMs = request.readTimeoutMs,
                accept = request.accept,
                useCaches = request.useCaches,
                cancellationMessage = request.cancellationMessage,
                downloadFailedException = request.downloadFailedException,
                onProgress = request.onProgress
            )
        )
        if (request.target.updateFileSha256() != expectedHash) {
            runCatching { request.target.delete() }
            throw request.checksumMismatchException()
        }
        request.afterVerified(request.target)
        return request.target
    }
}

class UpdateVerifiedArtifactRequest(
    val url: String,
    val target: File,
    val sha256: String,
    val userAgent: String,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val accept: String? = null,
    val useCaches: Boolean = false,
    val cancellationMessage: String? = null,
    val downloadFailedException: (Throwable?) -> Exception,
    val checksumMismatchException: () -> Exception,
    val beforeDownload: () -> Unit = {},
    val afterVerified: (File) -> Unit = {},
    val onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit = { _, _ -> }
)

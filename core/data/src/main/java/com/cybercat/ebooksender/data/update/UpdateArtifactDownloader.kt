package com.cybercat.ebooksender.data.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive

class UpdateArtifactDownloader {
    suspend fun download(request: UpdateArtifactDownloadRequest) {
        request.target.parentFile?.mkdirs()
        val temporary = File(request.target.parentFile, "${request.target.name}.download")
        runCatching { temporary.delete() }

        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = request.connectTimeoutMs
            readTimeout = request.readTimeoutMs
            instanceFollowRedirects = true
            requestMethod = GET_METHOD
            useCaches = request.useCaches
            request.accept?.let { accept -> setRequestProperty("Accept", accept) }
            setRequestProperty("User-Agent", request.userAgent)
        }
        val cancellationHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                runCatching { connection.disconnect() }
            }
        }

        try {
            val code = connection.responseCode
            if (code !in SUCCESS_CODES) {
                throw request.downloadFailedException(null)
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
            var bytesRead = 0L
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        coroutineContext.ensureActive()
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        request.onProgress(bytesRead, totalBytes)
                    }
                }
            }

            coroutineContext.ensureActive()
            if (!temporary.renameTo(request.target)) {
                throw request.downloadFailedException(null)
            }
        } catch (exception: IOException) {
            if (coroutineContext[Job]?.isCancelled == true) {
                throw CancellationException(request.cancellationMessage)
            }
            throw request.downloadFailedException(exception)
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
            runCatching { temporary.delete() }
        }
    }

    private companion object {
        const val GET_METHOD = "GET"
        val SUCCESS_CODES = 200..299
    }
}

class UpdateArtifactDownloadRequest(
    val url: String,
    val target: File,
    val userAgent: String,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int,
    val accept: String? = null,
    val useCaches: Boolean = false,
    val cancellationMessage: String = "Update artifact download canceled",
    val downloadFailedException: (Throwable?) -> Exception,
    val onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit = { _, _ -> }
)

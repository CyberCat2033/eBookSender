package com.cybercat.ebooksender.transfer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DownloadCacheManager @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun deleteDownloadSource(
        sourceUri: String,
        retainedSourceUris: Set<String> = emptySet()
    ) {
        deleteDownloadSources(
            sourceUris = listOf(sourceUri),
            retainedSourceUris = retainedSourceUris
        )
    }

    suspend fun deleteDownloadSources(
        sourceUris: Collection<String>,
        retainedSourceUris: Set<String> = emptySet()
    ) {
        withContext(Dispatchers.IO) {
            val retainedFiles = retainedSourceUris.mapNotNull { uri ->
                uri.toDownloadCacheFile(requireExistingFile = true)
            }.toSet()

            sourceUris
                .mapNotNull { uri -> uri.toDownloadCacheFile(requireExistingFile = true) }
                .distinct()
                .filterNot { file -> file in retainedFiles }
                .forEach { file -> runCatching { file.delete() } }
        }
    }

    fun isDownloadCacheSource(sourceUri: String): Boolean =
        sourceUri.toDownloadCacheFile(requireExistingFile = false) != null

    private fun String.toDownloadCacheFile(requireExistingFile: Boolean): File? = runCatching {
        val uri = Uri.parse(this)
        if (uri.scheme != ContentResolver.SCHEME_FILE) {
            return@runCatching null
        }

        val sourceFile = File(uri.path.orEmpty()).canonicalFile
        if (requireExistingFile && !sourceFile.isFile) {
            return@runCatching null
        }

        sourceFile.takeIf { file ->
            downloadCacheDirs().any { directory -> file.isInsideOrSameAs(directory) }
        }
    }.getOrNull()

    private fun downloadCacheDirs(): List<File> = listOf(
        File(context.cacheDir, OPDS_DOWNLOAD_CACHE_DIRECTORY),
        File(context.cacheDir, MANGA_DOWNLOAD_CACHE_DIRECTORY)
    ).map { directory -> directory.canonicalFile }

    private companion object {
        const val OPDS_DOWNLOAD_CACHE_DIRECTORY = "opds"
        const val MANGA_DOWNLOAD_CACHE_DIRECTORY = "manga"
    }
}

private fun File.isInsideOrSameAs(directory: File): Boolean {
    var current: File? = this
    while (current != null) {
        if (current == directory) return true
        current = current.parentFile
    }
    return false
}

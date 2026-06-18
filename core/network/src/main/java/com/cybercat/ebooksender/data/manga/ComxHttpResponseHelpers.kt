package com.cybercat.ebooksender.data.manga

import java.io.File
import java.net.HttpURLConnection

internal fun HttpURLConnection.readTextBody(): String {
    val stream = if (responseCode in 200..399) {
        inputStream
    } else {
        errorStream
    } ?: return ""
    return stream.bufferedReader().use { reader -> reader.readText() }
}

internal fun HttpURLConnection.readErrorSnippet(): String = runCatching {
    (errorStream ?: inputStream)
        .bufferedReader()
        .use { reader -> reader.readText() }
        .errorSnippet()
}.getOrDefault("")

internal fun File.readSmallText(): String =
    takeIf { it.exists() && it.length() in 1..MAX_SMALL_TEXT_BYTES }
        ?.let { file ->
            runCatching { file.readText(Charsets.UTF_8).errorSnippet() }
                .getOrDefault("")
        }
        .orEmpty()

internal fun String.messageSuffix(): String = takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()

internal fun String.errorSnippet(): String = cleanWhitespace().take(MAX_ERROR_SNIPPET_LENGTH)

private const val MAX_SMALL_TEXT_BYTES = 4096L
private const val MAX_ERROR_SNIPPET_LENGTH = 180

package com.cybercat.ebooksender.data.manga

import android.content.Context
import android.webkit.WebSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComxUserAgentProvider @Inject constructor(@ApplicationContext private val context: Context) {
    val userAgent: String by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching { WebSettings.getDefaultUserAgent(context) }
            .getOrNull()
            ?.takeIf { value -> value.isNotBlank() }
            ?: FALLBACK_USER_AGENT
    }

    private companion object {
        const val FALLBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    }
}

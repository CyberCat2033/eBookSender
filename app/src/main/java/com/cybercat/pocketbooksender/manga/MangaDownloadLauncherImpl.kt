package com.cybercat.pocketbooksender.manga

import android.content.Context
import androidx.core.content.ContextCompat
import com.cybercat.pocketbooksender.data.manga.MangaDownloadLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaDownloadLauncherImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MangaDownloadLauncher {
    override fun startMangaDownload(requestId: String) {
        ContextCompat.startForegroundService(
            context,
            MangaDownloadForegroundService.createIntent(context, requestId)
        )
    }

    override fun cancelMangaDownload(requestId: String) {
        context.startService(MangaDownloadForegroundService.createCancelIntent(context, requestId))
    }
}

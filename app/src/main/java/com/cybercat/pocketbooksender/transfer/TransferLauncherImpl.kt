package com.cybercat.pocketbooksender.transfer

import android.content.Context
import androidx.core.content.ContextCompat
import com.cybercat.pocketbooksender.data.transfer.TransferLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferLauncherImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : TransferLauncher {
    override fun startTransfer(requestId: String) {
        ContextCompat.startForegroundService(
            context,
            TransferForegroundService.createIntent(context, requestId)
        )
    }
}

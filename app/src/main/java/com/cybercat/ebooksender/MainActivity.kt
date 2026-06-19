package com.cybercat.ebooksender

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cybercat.ebooksender.data.opds.OpdsRepository
import com.cybercat.ebooksender.data.update.AppUpdateCheckTrigger
import com.cybercat.ebooksender.data.update.AppUpdateManager
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.localization.LocalizationManager
import com.cybercat.ebooksender.ui.EBookSenderApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var opdsRepository: OpdsRepository

    @Inject
    lateinit var localizationManager: LocalizationManager

    @Inject
    lateinit var appUpdateManager: AppUpdateManager

    private var sharedUris by mutableStateOf<List<Uri>>(emptyList())
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedUris = extractSharedUris(intent)
        if (savedInstanceState == null) {
            requestNotificationsIfNeeded()
            appUpdateManager.checkForUpdates(AppUpdateCheckTrigger.AppOpen)
        }

        setContent {
            val currentStrings by localizationManager.currentStrings.collectAsStateWithLifecycle()
            val appUpdateState by appUpdateManager.state.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalStrings provides currentStrings) {
                EBookSenderApp(
                    sharedUris = sharedUris,
                    onSharedUrisConsumed = { sharedUris = emptyList() },
                    appUpdateState = appUpdateState,
                    onInstallUpdate = appUpdateManager::installAvailableUpdate,
                    onCancelUpdateDownload = appUpdateManager::cancelUpdateDownload
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            com.cybercat.ebooksender.ui.BitmapCache.clear(this)
            opdsRepository.clearCache()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        opdsRepository.clearCache()
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUris = extractSharedUris(intent)
    }

    private fun extractSharedUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()

        return when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.streamUri())
            Intent.ACTION_SEND_MULTIPLE -> intent.streamUris()
            else -> emptyList()
        }
    }

    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    private fun Intent.streamUris(): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
}

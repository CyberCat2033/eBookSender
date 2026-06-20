package com.cybercat.ebooksender.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.cybercat.ebooksender.data.update.AvailableAppUpdate
import java.io.File

internal class AppUpdateInstallerLauncher(private val context: Context) {
    private val packageManager = context.packageManager
    var pendingPermissionUpdate: AvailableAppUpdate? = null
        private set

    fun canResumePendingInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        packageManager.canRequestPackageInstalls()

    fun consumePendingPermissionUpdate(): AvailableAppUpdate? {
        val update = pendingPermissionUpdate
        pendingPermissionUpdate = null
        return update
    }

    fun launchInstaller(update: AvailableAppUpdate, apk: File): AppUpdateInstallLaunchResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            pendingPermissionUpdate = update
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return runCatching { context.startActivity(settingsIntent) }
                .fold(
                    onSuccess = { AppUpdateInstallLaunchResult.PermissionRequired },
                    onFailure = {
                        pendingPermissionUpdate = null
                        AppUpdateInstallLaunchResult.InstallUnavailable
                    }
                )
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )

        @Suppress("DEPRECATION")
        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(installIntent) }
            .fold(
                onSuccess = { AppUpdateInstallLaunchResult.InstallerStarted },
                onFailure = { AppUpdateInstallLaunchResult.InstallUnavailable }
            )
    }
}

internal enum class AppUpdateInstallLaunchResult {
    InstallerStarted,
    PermissionRequired,
    InstallUnavailable
}

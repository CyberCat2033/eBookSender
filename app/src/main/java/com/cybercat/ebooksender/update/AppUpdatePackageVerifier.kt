package com.cybercat.ebooksender.update

import android.content.Context
import com.cybercat.ebooksender.data.update.AppUpdateErrorReason
import java.io.File

internal class AppUpdatePackageVerifier(private val context: Context) {
    fun verifyApkPackage(apk: File, currentVersionCode: Long) {
        val packageManager = context.packageManager
        val archiveInfo = packageManager.getPackageArchiveInfoCompat(apk.absolutePath)
            ?: throw AppUpdateException(AppUpdateErrorReason.InvalidManifest)
        if (archiveInfo.packageName != context.packageName) {
            throw AppUpdateException(AppUpdateErrorReason.InvalidManifest)
        }
        val archiveVersionCode = archiveInfo.longVersionCodeCompat()
        if (archiveVersionCode <= currentVersionCode) {
            throw AppUpdateException(AppUpdateErrorReason.InvalidManifest)
        }

        val installedInfo = packageManager.getPackageInfoCompat(context.packageName)
        val installedSignatures = installedInfo.signingCertificateBytes()
        val archiveSignatures = archiveInfo.signingCertificateBytes()
        if (installedSignatures.isEmpty() || installedSignatures != archiveSignatures) {
            throw AppUpdateException(AppUpdateErrorReason.SignatureMismatch)
        }
    }
}

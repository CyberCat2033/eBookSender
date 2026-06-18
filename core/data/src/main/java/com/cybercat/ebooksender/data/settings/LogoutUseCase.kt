package com.cybercat.ebooksender.data.settings

import android.webkit.CookieManager
import com.cybercat.ebooksender.data.manga.MangaRepository
import com.cybercat.ebooksender.data.opds.OpdsRepository
import com.cybercat.ebooksender.transfer.ConnectionManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LogoutUseCase @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val opdsRepository: OpdsRepository,
    private val mangaRepository: MangaRepository
) {
    suspend fun hasLogoutTargets(): Boolean = withContext(Dispatchers.IO) {
        val deviceConnected = connectionManager.connectedDevice.value != null
        val hasOpdsCredentials = runCatching {
            opdsRepository.hasSavedCredentials()
        }.getOrDefault(false)
        val hasMangaSavedSeries = runCatching {
            mangaRepository.hasSavedSeries()
        }.getOrDefault(false)
        val hasCookies = runCatching {
            CookieManager.getInstance().hasCookies()
        }.getOrDefault(false)
        
        deviceConnected || hasOpdsCredentials || hasMangaSavedSeries || hasCookies
    }

    suspend fun logoutAll(): Boolean = withContext(Dispatchers.IO) {
        val deviceConnected = connectionManager.connectedDevice.value != null
        val clearedOpds = runCatching { opdsRepository.logoutAll() }.getOrDefault(false)
        val clearedManga = runCatching {
            mangaRepository.clearSavedSeries()
        }.getOrDefault(false)
        val hasCookies = runCatching {
            CookieManager.getInstance().hasCookies()
        }.getOrDefault(false)

        if (hasCookies) {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
        }

        if (deviceConnected) {
            connectionManager.disconnect()
        }

        deviceConnected || clearedOpds || clearedManga || hasCookies
    }
}

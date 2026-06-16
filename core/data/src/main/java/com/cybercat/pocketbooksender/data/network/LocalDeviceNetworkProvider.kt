package com.cybercat.pocketbooksender.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.cybercat.pocketbooksender.data.settings.SettingsRepository
import com.cybercat.pocketbooksender.network.LocalNetworkBypassUnavailableException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.Socket
import java.net.URL
import java.net.URLConnection
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.SocketFactory
import kotlinx.coroutines.flow.first

@Singleton
class LocalDeviceNetworkProvider @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    suspend fun openConnection(url: URL): URLConnection {
        val network = selectedNetwork()
        return network?.openConnection(url) ?: url.openConnection()
    }

    suspend fun socketFactory(): SocketFactory? =
        selectedNetwork()?.socketFactory

    private suspend fun selectedNetwork(): Network? {
        if (!settingsRepository.settings.first().bypassVpnForLocalConnections) {
            return null
        }
        val network = findDirectLocalNetwork() ?: return null
        return if (network.canBindSocket()) {
            network
        } else {
            throw LocalNetworkBypassUnavailableException()
        }
    }

    @Suppress("DEPRECATION")
    private fun findDirectLocalNetwork(): Network? {
        connectivityManager.activeNetwork
            ?.takeIf(::isDirectLocalNetwork)
            ?.let { return it }

        return connectivityManager.allNetworks
            .asSequence()
            .mapNotNull { network ->
                val score = connectivityManager.getNetworkCapabilities(network)
                    ?.directLocalNetworkScore()
                    ?: return@mapNotNull null
                Candidate(network, score)
            }
            .maxByOrNull(Candidate::score)
            ?.network
    }

    private fun isDirectLocalNetwork(network: Network): Boolean =
        connectivityManager.getNetworkCapabilities(network)
            ?.directLocalNetworkScore() != null

    private fun Network.canBindSocket(): Boolean =
        try {
            Socket().use(::bindSocket)
            true
        } catch (_: IOException) {
            false
        } catch (_: RuntimeException) {
            false
        }

    private fun NetworkCapabilities.directLocalNetworkScore(): Int? {
        if (hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return null
        return when {
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 30
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 20
            else -> null
        }?.let { transportScore ->
            if (hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                transportScore + 1
            } else {
                transportScore
            }
        }
    }

    private data class Candidate(
        val network: Network,
        val score: Int,
    )
}

package com.cybercat.ebooksender.data.opds

import com.cybercat.ebooksender.data.database.dao.OpdsSourceDao
import java.net.URL
import javax.inject.Inject

class OpdsCredentialsProviderImpl @Inject constructor(
    private val sourceDao: OpdsSourceDao,
    private val credentialsStore: OpdsSecureCredentialsStore
) : OpdsCredentialsProvider {
    override suspend fun getCredentialsForUrl(urlStr: String): Pair<String, String>? {
        val requestHost = runCatching { URL(urlStr).host.lowercase() }.getOrNull() ?: return null
        val sourcesList = sourceDao.getAllSources()
        for (source in sourcesList) {
            if (!source.enabled) continue

            val sourceHost = runCatching { URL(source.url).host.lowercase() }.getOrNull()
            if (sourceHost != null && sourceHost == requestHost) {
                val credentials = credentialsStore.read(source.id)
                val username = credentials?.username
                val password = credentials?.password
                if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                    return Pair(username, password)
                }
            }
        }
        return null
    }
}

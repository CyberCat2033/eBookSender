package com.cybercat.pocketbooksender.data.opds

import com.cybercat.pocketbooksender.data.database.dao.OpdsSourceDao
import java.net.URL
import javax.inject.Inject

class OpdsCredentialsProviderImpl @Inject constructor(private val sourceDao: OpdsSourceDao) :
    OpdsCredentialsProvider {
    override suspend fun getCredentialsForUrl(urlStr: String): Pair<String, String>? {
        val requestHost = runCatching { URL(urlStr).host.lowercase() }.getOrNull() ?: return null
        val sourcesList = sourceDao.getAllSources()
        for (source in sourcesList) {
            val sourceHost = runCatching { URL(source.url).host.lowercase() }.getOrNull()
            if (sourceHost != null && sourceHost == requestHost) {
                val username = source.username
                val password = source.password
                if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                    return Pair(username, password)
                }
            }
        }
        return null
    }
}

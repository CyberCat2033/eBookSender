package com.cybercat.ebooksender.data.opds

import com.cybercat.ebooksender.data.database.dao.OpdsSourceDao
import com.cybercat.ebooksender.util.UrlHostMatcher
import javax.inject.Inject

class OpdsCredentialsProviderImpl @Inject constructor(
    private val sourceDao: OpdsSourceDao,
    private val credentialsStore: OpdsSecureCredentialsStore
) : OpdsCredentialsProvider {
    override suspend fun getCredentialsForUrl(urlStr: String): Pair<String, String>? {
        UrlHostMatcher.normalizedHost(urlStr) ?: return null
        val sourcesList = sourceDao.getAllSources()
        for (source in sourcesList) {
            if (!source.enabled) continue

            if (UrlHostMatcher.hostsMatch(urlStr, source.url)) {
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

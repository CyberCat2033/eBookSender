package com.cybercat.ebooksender.data.opds

interface OpdsCredentialsProvider {
    suspend fun getCredentialsForUrl(urlStr: String): Pair<String, String>?
}

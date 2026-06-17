package com.cybercat.pocketbooksender.data.opds

interface OpdsCredentialsProvider {
    suspend fun getCredentialsForUrl(urlStr: String): Pair<String, String>?
}

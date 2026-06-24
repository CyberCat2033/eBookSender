package com.cybercat.ebooksender.data.manga

interface MangaLibSessionSecretStore {
    suspend fun readCookieHeader(): String?
    suspend fun saveCookieHeader(cookieHeader: String)
    suspend fun clear()
}

package com.cybercat.ebooksender.data.manga

import com.cybercat.ebooksender.data.security.EncryptedSecretStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaLibEncryptedSessionSecretStore @Inject constructor(
    private val encryptedSecretStore: EncryptedSecretStore
) : MangaLibSessionSecretStore {
    override suspend fun readCookieHeader(): String? =
        encryptedSecretStore.read(NAMESPACE, OWNER_ID, COOKIE_HEADER_NAME)
            ?.decodeToString()
            ?.takeIf { it.isNotBlank() }

    override suspend fun saveCookieHeader(cookieHeader: String) {
        encryptedSecretStore.save(
            namespace = NAMESPACE,
            ownerId = OWNER_ID,
            name = COOKIE_HEADER_NAME,
            value = cookieHeader.encodeToByteArray()
        )
    }

    override suspend fun clear() {
        encryptedSecretStore.deleteOwner(NAMESPACE, OWNER_ID)
    }

    private companion object {
        const val NAMESPACE = "manga_source_session"
        const val OWNER_ID = "mangalib"
        const val COOKIE_HEADER_NAME = "cookie_header"
    }
}

package com.cybercat.ebooksender.data.security

import com.cybercat.ebooksender.data.database.dao.EncryptedSecretDao
import com.cybercat.ebooksender.data.database.entity.EncryptedSecretEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedSecretStoreTest {
    private val dao = FakeEncryptedSecretDao()
    private val cipher = FakeEncryptedSecretCipher()
    private val store = EncryptedSecretStore(dao, cipher)

    @Test
    fun saveAndReadSecret() = runBlocking {
        store.save("session", "mangalib", "cookie", "secret".encodeToByteArray())

        assertArrayEquals(
            "secret".encodeToByteArray(),
            store.read("session", "mangalib", "cookie")
        )
        assertTrue(store.hasAny("session"))
    }

    @Test
    fun overwriteSecret() = runBlocking {
        store.save("session", "mangalib", "cookie", "old".encodeToByteArray())
        store.save("session", "mangalib", "cookie", "new".encodeToByteArray())

        assertArrayEquals(
            "new".encodeToByteArray(),
            store.read("session", "mangalib", "cookie")
        )
    }

    @Test
    fun deleteSecretAndOwner() = runBlocking {
        store.save("session", "mangalib", "cookie", "secret".encodeToByteArray())
        store.save("session", "other", "cookie", "other".encodeToByteArray())

        store.delete("session", "mangalib", "cookie")
        assertNull(store.read("session", "mangalib", "cookie"))
        assertTrue(store.hasAny("session"))

        store.deleteOwner("session", "other")
        assertFalse(store.hasAny("session"))
    }

    @Test
    fun decryptFailureDeletesCorruptedSecret() = runBlocking {
        store.save("session", "mangalib", "cookie", "secret".encodeToByteArray())
        dao.corrupt("session", "mangalib", "cookie")

        assertNull(store.read("session", "mangalib", "cookie"))
        assertFalse(store.hasAny("session"))
    }

    private class FakeEncryptedSecretCipher : EncryptedSecretCipher {
        override fun encrypt(plaintext: ByteArray, aad: ByteArray): EncryptedSecretPayload {
            val ciphertext = aad + byteArrayOf(SEPARATOR) + plaintext.reversedArray()
            return EncryptedSecretPayload(ciphertext = ciphertext, iv = byteArrayOf(1, 2, 3))
        }

        override fun decrypt(ciphertext: ByteArray, iv: ByteArray, aad: ByteArray): ByteArray {
            check(iv.contentEquals(byteArrayOf(1, 2, 3)))
            val expectedPrefix = aad + byteArrayOf(SEPARATOR)
            check(ciphertext.size >= expectedPrefix.size)
            check(ciphertext.copyOfRange(0, expectedPrefix.size).contentEquals(expectedPrefix))
            return ciphertext.copyOfRange(expectedPrefix.size, ciphertext.size).reversedArray()
        }

        private companion object {
            val SEPARATOR = 0.toByte()
        }
    }

    private class FakeEncryptedSecretDao : EncryptedSecretDao {
        private val items = mutableMapOf<Key, EncryptedSecretEntity>()

        override suspend fun get(
            namespace: String,
            ownerId: String,
            name: String
        ): EncryptedSecretEntity? = items[Key(namespace, ownerId, name)]

        override suspend fun upsert(secret: EncryptedSecretEntity) {
            items[Key(secret.namespace, secret.ownerId, secret.name)] = secret
        }

        override suspend fun delete(namespace: String, ownerId: String, name: String) {
            items.remove(Key(namespace, ownerId, name))
        }

        override suspend fun deleteOwner(namespace: String, ownerId: String) {
            items.keys.filter { key ->
                key.namespace == namespace && key.ownerId == ownerId
            }.forEach(items::remove)
        }

        override suspend fun hasAny(namespace: String): Boolean =
            items.keys.any { key -> key.namespace == namespace }

        fun corrupt(namespace: String, ownerId: String, name: String) {
            val key = Key(namespace, ownerId, name)
            val item = items.getValue(key)
            items[key] = item.copy(ciphertext = "corrupted".encodeToByteArray())
        }
    }

    private data class Key(val namespace: String, val ownerId: String, val name: String)
}

package com.cybercat.ebooksender.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.cybercat.ebooksender.data.database.dao.EncryptedSecretDao
import com.cybercat.ebooksender.data.database.entity.EncryptedSecretEntity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedSecretStore @Inject constructor(
    private val dao: EncryptedSecretDao,
    private val cipher: EncryptedSecretCipher
) {
    suspend fun read(namespace: String, ownerId: String, name: String): ByteArray? {
        val entity = dao.get(namespace, ownerId, name) ?: return null
        return runCatching {
            cipher.decrypt(
                ciphertext = entity.ciphertext,
                iv = entity.iv,
                aad = aad(namespace, ownerId, name)
            )
        }.getOrElse {
            dao.delete(namespace, ownerId, name)
            null
        }
    }

    suspend fun save(namespace: String, ownerId: String, name: String, value: ByteArray) {
        val encrypted = cipher.encrypt(value, aad(namespace, ownerId, name))
        dao.upsert(
            EncryptedSecretEntity(
                namespace = namespace,
                ownerId = ownerId,
                name = name,
                ciphertext = encrypted.ciphertext,
                iv = encrypted.iv,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(namespace: String, ownerId: String, name: String) {
        dao.delete(namespace, ownerId, name)
    }

    suspend fun deleteOwner(namespace: String, ownerId: String) {
        dao.deleteOwner(namespace, ownerId)
    }

    suspend fun hasAny(namespace: String): Boolean = dao.hasAny(namespace)

    private fun aad(namespace: String, ownerId: String, name: String): ByteArray =
        "$namespace:$ownerId:$name".encodeToByteArray()
}

interface EncryptedSecretCipher {
    fun encrypt(plaintext: ByteArray, aad: ByteArray): EncryptedSecretPayload
    fun decrypt(ciphertext: ByteArray, iv: ByteArray, aad: ByteArray): ByteArray
}

data class EncryptedSecretPayload(val ciphertext: ByteArray, val iv: ByteArray)

@Singleton
class AndroidKeystoreEncryptedSecretCipher @Inject constructor() : EncryptedSecretCipher {
    override fun encrypt(plaintext: ByteArray, aad: ByteArray): EncryptedSecretPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(aad)
        return EncryptedSecretPayload(
            ciphertext = cipher.doFinal(plaintext),
            iv = cipher.iv
        )
    }

    override fun decrypt(ciphertext: ByteArray, iv: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ebook_sender_encrypted_secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}

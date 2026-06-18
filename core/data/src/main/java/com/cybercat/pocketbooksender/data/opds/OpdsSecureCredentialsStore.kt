package com.cybercat.pocketbooksender.data.opds

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpdsSecureCredentialsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun read(sourceId: String): OpdsStoredCredentials? {
        val key = keyFor(sourceId)
        val encoded = prefs.getString(key, null) ?: return null
        val decrypted =
            runCatching { decrypt(encoded) }
                .getOrElse {
                    prefs.edit().remove(key).commit()
                    return null
                }
                ?: run {
                    prefs.edit().remove(key).commit()
                    return null
                }
        val credentials = decodeCredentials(decrypted)
        if (credentials == null) {
            prefs.edit().remove(key).commit()
        }
        return credentials
    }

    fun save(sourceId: String, username: String?, password: String?) {
        val normalizedUsername = username?.takeIf(String::isNotBlank)
        val normalizedPassword = password?.takeIf(String::isNotBlank)
        val key = keyFor(sourceId)

        if (normalizedUsername == null && normalizedPassword == null) {
            prefs.edit().remove(key).commit()
            return
        }

        val encoded = encrypt(
            encodeCredentials(
                OpdsStoredCredentials(
                    username = normalizedUsername,
                    password = normalizedPassword
                )
            )
        )
        check(prefs.edit().putString(key, encoded).commit()) {
            "Failed to persist OPDS credentials"
        }
    }

    fun remove(sourceId: String): Boolean = prefs.edit().remove(keyFor(sourceId)).commit()

    fun hasAny(): Boolean = prefs.all.isNotEmpty()

    fun clearAll(): Boolean {
        if (prefs.all.isEmpty()) return false
        return prefs.edit().clear().commit()
    }

    private fun encodeCredentials(credentials: OpdsStoredCredentials): ByteArray =
        ByteArrayOutputStream().use { byteStream ->
            DataOutputStream(byteStream).use { output ->
                output.writeBoolean(credentials.username != null)
                if (credentials.username != null) {
                    output.writeUTF(credentials.username)
                }
                output.writeBoolean(credentials.password != null)
                if (credentials.password != null) {
                    output.writeUTF(credentials.password)
                }
            }
            byteStream.toByteArray()
        }

    private fun decodeCredentials(payload: ByteArray): OpdsStoredCredentials? = runCatching {
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            val username = input.readBoolean().takeIf { it }?.let { input.readUTF() }
            val password = input.readBoolean().takeIf { it }?.let { input.readUTF() }
            if (input.available() != 0) {
                null
            } else if (username == null && password == null) {
                null
            } else {
                OpdsStoredCredentials(username = username, password = password)
            }
        }
    }.getOrNull()

    private fun encrypt(payload: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(payload)
        val combined = cipher.iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): ByteArray? {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        if (combined.size <= IV_LENGTH_BYTES) return null

        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val encrypted = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        return cipher.doFinal(encrypted)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            return existing
        }

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

    private fun keyFor(sourceId: String): String = "source:$sourceId"

    private companion object {
        const val PREFERENCES_NAME = "opds_secure_credentials"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "opds_credentials_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}

data class OpdsStoredCredentials(val username: String?, val password: String?)

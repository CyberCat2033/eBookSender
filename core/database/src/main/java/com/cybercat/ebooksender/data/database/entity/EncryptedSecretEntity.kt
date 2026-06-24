package com.cybercat.ebooksender.data.database.entity

import androidx.room.Entity

@Entity(
    tableName = "encrypted_secrets",
    primaryKeys = ["namespace", "ownerId", "name"]
)
data class EncryptedSecretEntity(
    val namespace: String,
    val ownerId: String,
    val name: String,
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val updatedAtMillis: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedSecretEntity) return false

        return namespace == other.namespace &&
            ownerId == other.ownerId &&
            name == other.name &&
            ciphertext.contentEquals(other.ciphertext) &&
            iv.contentEquals(other.iv) &&
            updatedAtMillis == other.updatedAtMillis
    }

    override fun hashCode(): Int {
        var result = namespace.hashCode()
        result = 31 * result + ownerId.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + updatedAtMillis.hashCode()
        return result
    }
}

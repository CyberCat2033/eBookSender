package com.cybercat.ebooksender.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cybercat.ebooksender.data.database.entity.EncryptedSecretEntity

@Dao
interface EncryptedSecretDao {
    @Query(
        """
        SELECT * FROM encrypted_secrets
        WHERE namespace = :namespace AND ownerId = :ownerId AND name = :name
        """
    )
    suspend fun get(namespace: String, ownerId: String, name: String): EncryptedSecretEntity?

    @Upsert
    suspend fun upsert(secret: EncryptedSecretEntity)

    @Query(
        """
        DELETE FROM encrypted_secrets
        WHERE namespace = :namespace AND ownerId = :ownerId AND name = :name
        """
    )
    suspend fun delete(namespace: String, ownerId: String, name: String)

    @Query("DELETE FROM encrypted_secrets WHERE namespace = :namespace AND ownerId = :ownerId")
    suspend fun deleteOwner(namespace: String, ownerId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM encrypted_secrets WHERE namespace = :namespace)")
    suspend fun hasAny(namespace: String): Boolean
}

package com.cybercat.ebooksender.di

import com.cybercat.ebooksender.data.security.AndroidKeystoreEncryptedSecretCipher
import com.cybercat.ebooksender.data.security.EncryptedSecretCipher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds
    @Singleton
    abstract fun bindEncryptedSecretCipher(
        impl: AndroidKeystoreEncryptedSecretCipher
    ): EncryptedSecretCipher
}

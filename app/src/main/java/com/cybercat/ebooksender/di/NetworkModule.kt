package com.cybercat.ebooksender.di

import com.cybercat.ebooksender.data.ftp.CommonsNetFtpGateway
import com.cybercat.ebooksender.data.ftp.FtpGateway
import com.cybercat.ebooksender.data.opds.OpdsCredentialsProvider
import com.cybercat.ebooksender.data.opds.OpdsCredentialsProviderImpl
import com.cybercat.ebooksender.metadata.LocalMetadataExtractor
import com.cybercat.ebooksender.metadata.MetadataExtractor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds
    @Singleton
    abstract fun bindFtpGateway(impl: CommonsNetFtpGateway): FtpGateway

    @Binds
    @Singleton
    abstract fun bindMetadataExtractor(impl: LocalMetadataExtractor): MetadataExtractor

    @Binds
    @Singleton
    abstract fun bindOpdsCredentialsProvider(
        impl: OpdsCredentialsProviderImpl
    ): OpdsCredentialsProvider
}

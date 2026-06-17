package com.cybercat.pocketbooksender.di

import com.cybercat.pocketbooksender.data.ftp.CommonsNetFtpGateway
import com.cybercat.pocketbooksender.data.ftp.FtpGateway
import com.cybercat.pocketbooksender.data.opds.OpdsCredentialsProvider
import com.cybercat.pocketbooksender.data.opds.OpdsCredentialsProviderImpl
import com.cybercat.pocketbooksender.metadata.LocalMetadataExtractor
import com.cybercat.pocketbooksender.metadata.MetadataExtractor
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

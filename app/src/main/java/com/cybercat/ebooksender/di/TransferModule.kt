package com.cybercat.ebooksender.di

import com.cybercat.ebooksender.data.transfer.TransferLauncher
import com.cybercat.ebooksender.data.transfer.UploadQueueManager
import com.cybercat.ebooksender.data.manga.MangaDownloadLauncher
import com.cybercat.ebooksender.manga.MangaDownloadLauncherImpl
import com.cybercat.ebooksender.transfer.TransferLauncherImpl
import com.cybercat.ebooksender.transfer.UploadQueueManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TransferModule {

    @Binds
    @Singleton
    abstract fun bindUploadQueueManager(
        impl: UploadQueueManagerImpl
    ): UploadQueueManager

    @Binds
    @Singleton
    abstract fun bindTransferLauncher(
        impl: TransferLauncherImpl
    ): TransferLauncher

    @Binds
    @Singleton
    abstract fun bindMangaDownloadLauncher(
        impl: MangaDownloadLauncherImpl
    ): MangaDownloadLauncher
}

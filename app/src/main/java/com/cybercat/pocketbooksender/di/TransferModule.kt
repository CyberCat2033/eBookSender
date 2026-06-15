package com.cybercat.pocketbooksender.di

import com.cybercat.pocketbooksender.data.transfer.TransferLauncher
import com.cybercat.pocketbooksender.data.transfer.UploadQueueManager
import com.cybercat.pocketbooksender.transfer.TransferLauncherImpl
import com.cybercat.pocketbooksender.transfer.UploadQueueManagerImpl
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
}


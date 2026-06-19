package com.cybercat.ebooksender.di

import com.cybercat.ebooksender.data.update.AppUpdateManager
import com.cybercat.ebooksender.update.AppUpdateManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateModule {
    @Binds
    @Singleton
    abstract fun bindAppUpdateManager(impl: AppUpdateManagerImpl): AppUpdateManager
}

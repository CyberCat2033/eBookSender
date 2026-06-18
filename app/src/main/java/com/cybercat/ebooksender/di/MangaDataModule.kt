package com.cybercat.ebooksender.di

import com.cybercat.ebooksender.data.manga.MangaRepository
import com.cybercat.ebooksender.data.manga.MangaSeriesPageLoader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MangaDataModule {
    @Binds
    @Singleton
    abstract fun bindMangaSeriesPageLoader(impl: MangaRepository): MangaSeriesPageLoader
}

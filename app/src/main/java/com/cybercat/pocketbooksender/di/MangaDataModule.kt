package com.cybercat.pocketbooksender.di

import com.cybercat.pocketbooksender.data.manga.MangaRepository
import com.cybercat.pocketbooksender.data.manga.MangaSeriesPageLoader
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

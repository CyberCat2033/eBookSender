package com.cybercat.ebooksender.di

import com.cybercat.ebooksender.data.manga.ComxMangaAdapter
import com.cybercat.ebooksender.data.manga.HtmlMangaSourceAdapter
import com.cybercat.ebooksender.data.manga.MangaLibEncryptedSessionSecretStore
import com.cybercat.ebooksender.data.manga.MangaLibMangaAdapter
import com.cybercat.ebooksender.data.manga.MangaLibSessionSecretStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Collects every installed manga source adapter into a Hilt [Set] of [HtmlMangaSourceAdapter].
 *
 * Each concrete adapter declares itself here with `@Binds @IntoSet`. [MangaRepository] then
 * receives the whole set and dispatches by `sourceId`, so adding a new source never requires
 * touching the repository wiring — only a single new adapter class plus one line here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MangaSourceModule {
    @Binds
    @IntoSet
    abstract fun bindComxAdapter(impl: ComxMangaAdapter): HtmlMangaSourceAdapter

    @Binds
    @IntoSet
    abstract fun bindMangaLibAdapter(impl: MangaLibMangaAdapter): HtmlMangaSourceAdapter

    @Binds
    abstract fun bindMangaLibSessionSecretStore(
        impl: MangaLibEncryptedSessionSecretStore
    ): MangaLibSessionSecretStore
}

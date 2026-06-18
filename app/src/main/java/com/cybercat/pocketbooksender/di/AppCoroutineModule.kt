package com.cybercat.pocketbooksender.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppCoroutineModule {
    @Provides
    @Singleton
    fun provideApplicationCoroutineExceptionHandler(): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            if (throwable is CancellationException) return@CoroutineExceptionHandler
            Log.e(TAG, "Unhandled application coroutine failure", throwable)
        }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(exceptionHandler: CoroutineExceptionHandler): CoroutineScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate + exceptionHandler
        )

    private const val TAG = "AppCoroutineScope"
}

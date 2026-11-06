package com.goomistudio.postmory.di

import android.database.sqlite.SQLiteFullException
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.IOException
import javax.inject.Qualifier
import javax.inject.Singleton

/** Scope for small writes that must finish even if the screen that started them goes away. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {
    @Provides
    @Singleton
    @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + FullDiskHandler)

    /**
     * A background write (caption, title, settings) that hits a full disk is dropped with a log
     * line instead of closing the app; any other failure still crashes, so bugs stay visible.
     */
    private val FullDiskHandler = CoroutineExceptionHandler { _, e ->
        if (e is SQLiteFullException || e is IOException) {
            Log.w("AppScope", "Background write failed (phone storage full?)", e)
        } else {
            throw e
        }
    }
}

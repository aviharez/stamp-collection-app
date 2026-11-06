package com.goomistudio.postmory.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.goomistudio.postmory.data.db.AlbumDao
import com.goomistudio.postmory.data.db.ScrapPageDao
import com.goomistudio.postmory.data.db.StampDao
import com.goomistudio.postmory.data.db.StampDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): StampDatabase =
        Room.databaseBuilder<StampDatabase>(context, StampDatabase.NAME)
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(StampDatabase.Migration1To2, StampDatabase.Migration2To3, StampDatabase.Migration3To4)
            .build()

    @Provides fun albumDao(db: StampDatabase): AlbumDao = db.albumDao()

    @Provides fun stampDao(db: StampDatabase): StampDao = db.stampDao()

    @Provides fun scrapPageDao(db: StampDatabase): ScrapPageDao = db.scrapPageDao()

    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    /** System clock in the device's current zone; swapped for a fixed clock in tests. */
    @Provides fun clock(): Clock = Clock.systemDefaultZone()
}

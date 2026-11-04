package com.aviharez.stamp.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Small user settings in DataStore (CLAUDE.md §2). */
@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {

    /** Front camera: save the selfie mirrored, as the preview shows it. */
    val mirrorSelfie: Flow<Boolean> =
        store.data.map { it[MirrorSelfie] ?: true }.distinctUntilChanged()

    suspend fun setMirrorSelfie(on: Boolean) {
        store.edit { it[MirrorSelfie] = on }
    }

    /** Album new stamps go into: the one the user last stuck a stamp in. */
    suspend fun lastAlbumId(): Long? = store.data.first()[LastAlbumId]

    suspend fun setLastAlbumId(id: Long) {
        store.edit { it[LastAlbumId] = id }
    }

    private companion object {
        val MirrorSelfie = booleanPreferencesKey("mirror_selfie")
        val LastAlbumId = longPreferencesKey("last_album_id")
    }
}

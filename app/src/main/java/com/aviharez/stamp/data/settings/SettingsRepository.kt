package com.aviharez.stamp.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    /** The one-time postmark location ask has been answered (either way). */
    suspend fun locationAsked(): Boolean = store.data.first()[LocationAsked] ?: false

    suspend fun setLocationAsked() {
        store.edit { it[LocationAsked] = true }
    }

    /** The first-run welcome has been answered (or skipped because stamps already exist). */
    suspend fun onboarded(): Boolean = store.data.first()[Onboarded] ?: false

    suspend fun setOnboarded() {
        store.edit { it[Onboarded] = true }
    }

    /** When the last backup file was written (epoch millis), null = never. */
    val lastBackupAt: Flow<Long?> = store.data.map { it[LastBackupAt] }.distinctUntilChanged()

    suspend fun setLastBackupAt(millis: Long) {
        store.edit { it[LastBackupAt] = millis }
    }

    /** Evening streak reminder: off until the user turns it on on Profile. */
    val reminder: Flow<ReminderSetting> = store.data
        .map { ReminderSetting(on = it[ReminderOn] ?: false, minuteOfDay = it[ReminderMinute] ?: ReminderSetting.DefaultMinute) }
        .distinctUntilChanged()

    suspend fun setReminderOn(on: Boolean) {
        store.edit { it[ReminderOn] = on }
    }

    suspend fun setReminderMinute(minuteOfDay: Int) {
        store.edit { it[ReminderMinute] = minuteOfDay.coerceIn(0, ReminderSetting.MinutesPerDay - 1) }
    }

    private companion object {
        val ReminderOn = booleanPreferencesKey("reminder_on")
        val ReminderMinute = intPreferencesKey("reminder_minute")
        val MirrorSelfie = booleanPreferencesKey("mirror_selfie")
        val LastAlbumId = longPreferencesKey("last_album_id")
        val LocationAsked = booleanPreferencesKey("location_asked")
        val LastBackupAt = longPreferencesKey("last_backup_at")
        val Onboarded = booleanPreferencesKey("onboarded")
    }
}

/** When the streak reminder fires, as minutes after local midnight. */
data class ReminderSetting(val on: Boolean, val minuteOfDay: Int) {
    companion object {
        const val MinutesPerDay = 24 * 60
        /** 20:00: late enough that most days are stamped, early enough to still go out. */
        const val DefaultMinute = 20 * 60
    }
}

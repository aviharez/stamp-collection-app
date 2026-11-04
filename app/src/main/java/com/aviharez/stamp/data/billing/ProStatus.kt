package com.aviharez.stamp.data.billing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** What Pro unlocks; the free tier's limits live here so screens never hardcode them. */
object ProRules {
    /** Free users can keep this many albums (more with Pro). CLAUDE.md §4. */
    const val FreeAlbumLimit = 3
}

/**
 * Whether the user has Pro (no watermark, unlimited albums, …). Cached in DataStore so it is
 * known offline and at start-up; [BillingRepository] is the source of truth and writes it.
 * Debug builds can also switch Pro on by hand (Profile), kept apart so billing never clears it.
 */
@Singleton
class ProStatus @Inject constructor(private val store: DataStore<Preferences>) {

    val isPro: Flow<Boolean> =
        store.data.map { (it[IsPro] ?: false) || (it[DebugPro] ?: false) }.distinctUntilChanged()

    val debugPro: Flow<Boolean> = store.data.map { it[DebugPro] ?: false }.distinctUntilChanged()

    suspend fun isProNow(): Boolean = isPro.first()

    /** Written by billing only. */
    suspend fun setPurchased(pro: Boolean) {
        store.edit { it[IsPro] = pro }
    }

    /** Debug builds only (the switch is not shown in release). */
    suspend fun setDebugPro(pro: Boolean) {
        store.edit { it[DebugPro] = pro }
    }

    private companion object {
        val IsPro = booleanPreferencesKey("is_pro")
        val DebugPro = booleanPreferencesKey("debug_pro")
    }
}

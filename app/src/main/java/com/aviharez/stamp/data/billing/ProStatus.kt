package com.aviharez.stamp.data.billing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the user has Pro (no watermark, unlimited albums, …). Cached in DataStore so it is
 * known offline and at start-up; Play Billing (milestone 3) is the source of truth and writes it.
 */
@Singleton
class ProStatus @Inject constructor(private val store: DataStore<Preferences>) {

    val isPro: Flow<Boolean> = store.data.map { it[IsPro] ?: false }.distinctUntilChanged()

    suspend fun setPro(pro: Boolean) {
        store.edit { it[IsPro] = pro }
    }

    private companion object {
        val IsPro = booleanPreferencesKey("is_pro")
    }
}

package com.aviharez.stamp.data.analytics

import android.content.Context
import android.os.Bundle
import com.aviharez.stamp.data.settings.SettingsRepository
import com.aviharez.stamp.di.AppScope
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What a finished export was. */
enum class ExportKind { Stamp, Postcard, AlbumPage, Map, Stickers }

/** How it left the app. */
enum class ExportAction { Share, Save, Print }

/**
 * Crash reports (Crashlytics) and anonymous usage counts (Analytics), both following the Profile switch
 * "Share anonymous usage stats" (on by default). Only what the app did is counted ("a stamp was made
 * with the camera"), never photos, titles, words, places or stamp ids, and never the advertising ID
 * (AndroidManifest.xml turns it and every ad signal off).
 *
 * Without app/google-services.json Firebase isn't started and everything here does nothing.
 */
@Singleton
class UsageStats @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    @param:AppScope private val scope: CoroutineScope,
) {
    private val firebase: Boolean by lazy { FirebaseApp.getApps(context).isNotEmpty() }
    private val analytics: FirebaseAnalytics by lazy { FirebaseAnalytics.getInstance(context) }

    /** Follows the Profile switch (both SDKs start collecting only once it is read). */
    fun start() {
        if (!firebase) return
        scope.launch { settings.usageStats.collect(::apply) }
    }

    private fun apply(on: Boolean) {
        val granted = if (on) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED
        analytics.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to granted,
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
            ),
        )
        analytics.setAnalyticsCollectionEnabled(on)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(on)
    }

    fun stampMade(source: String, shape: String, withPlace: Boolean) =
        log(StampMade, Source to source, Shape to shape, WithPlace to withPlace)

    fun exported(kind: ExportKind, action: ExportAction, look: String? = null) =
        log(Exported, Kind to kind.name, Action to action.name, Look to look)

    fun paywallShown(reason: String) = log(PaywallShown, Reason to reason)

    fun adFinished(result: String) = log(AdFinished, Result to result)

    fun albumCreated() = log(AlbumCreated)

    fun backup(restoring: Boolean, ok: Boolean) = log(if (restoring) RestoreDone else BackupDone, Ok to ok)

    private fun log(event: String, vararg params: Pair<String, Any?>) {
        if (!firebase) return
        val bundle = Bundle()
        for ((key, value) in params) {
            when (value) {
                is String -> bundle.putString(key, value.lowercase())
                is Boolean -> bundle.putLong(key, if (value) 1L else 0L)
                null -> Unit
                else -> bundle.putString(key, value.toString())
            }
        }
        analytics.logEvent(event, bundle)
    }

    // Firebase names: snake_case, ≤ 40 characters.
    private companion object {
        const val StampMade = "stamp_made"
        const val Exported = "stamp_export"
        const val PaywallShown = "paywall_shown"
        const val AdFinished = "rewarded_ad"
        const val AlbumCreated = "album_created"
        const val BackupDone = "backup_made"
        const val RestoreDone = "backup_restored"
        const val Source = "source"
        const val Shape = "shape"
        const val WithPlace = "with_place"
        const val Kind = "kind"
        const val Action = "action"
        const val Look = "look"
        const val Reason = "reason"
        const val Result = "result"
        const val Ok = "ok"
    }
}

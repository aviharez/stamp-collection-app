package com.aviharez.stamp.data.ads

import android.app.Activity
import android.content.Context
import com.aviharez.stamp.R
import com.aviharez.stamp.di.AppScope
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** How a rewarded ad ended. Only [Rewarded] grants anything. */
enum class AdResult { Rewarded, Skipped, Unavailable }

/** UI state of a "… · short ad" button. */
enum class AdOffer { Idle, Loading, Unavailable }

/**
 * Rewarded ads, only ever started by the user tapping a "short ad" button (CLAUDE.md §4).
 * Nothing ad-related runs at launch: the consent form (EEA/UK/US states) is asked the first time
 * the user taps such a button, and the SDK starts only after that.
 */
@Singleton
class RewardedAds @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:AppScope private val scope: CoroutineScope,
) {
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)
    private val sdk = Mutex()
    private var started = false
    private var ready: RewardedAd? = null
    private var readyAt = 0L

    private val _privacyOptionsRequired = MutableStateFlow(isPrivacyOptionsRequired())
    /** True where the law asks for an "ad privacy choices" entry point (shown on Profile). */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /** Loads an ad in the background while the user looks at an offer, only if consent is already settled. */
    fun preload() {
        if (!consent.canRequestAds()) return
        scope.launch { runCatching { ensureReady() } }
    }

    /** Consent (if needed) → load → show. Suspends until the ad is closed. */
    suspend fun show(activity: Activity): AdResult = withContext(Dispatchers.Main) {
        if (!gatherConsent(activity)) return@withContext AdResult.Unavailable
        val ad = withTimeoutOrNull(LoadTimeoutMs) { ensureReady() } ?: return@withContext AdResult.Unavailable
        ready = null
        val result = present(activity, ad)
        preload() // next one ready if they want another
        result
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _privacyOptionsRequired.value = isPrivacyOptionsRequired() }
    }

    private suspend fun gatherConsent(activity: Activity): Boolean = suspendCancellableCoroutine { cont ->
        val done = {
            _privacyOptionsRequired.value = isPrivacyOptionsRequired()
            if (cont.isActive) cont.resume(consent.canRequestAds())
        }
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { done() } },
            { done() }, // offline etc.: fall back to consent from an earlier session, if any
        )
    }

    /** The cached ad if still fresh, else a new one. Main thread (SDK requirement). */
    private suspend fun ensureReady(): RewardedAd? = withContext(Dispatchers.Main) {
        sdk.withLock {
            if (!started) {
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder()
                        .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
                        .build(),
                )
                withContext(Dispatchers.IO) { suspendCancellableCoroutine { c -> MobileAds.initialize(context) { c.resume(Unit) } } }
                started = true
            }
            ready?.takeIf { System.currentTimeMillis() - readyAt < MaxAgeMs }
                ?: load()?.also { ready = it; readyAt = System.currentTimeMillis() }
        }
    }

    private suspend fun load(): RewardedAd? = suspendCancellableCoroutine { cont ->
        RewardedAd.load(
            context,
            context.getString(R.string.admob_rewarded_unit),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (cont.isActive) cont.resume(ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (cont.isActive) cont.resume(null)
                }
            },
        )
    }

    private suspend fun present(activity: Activity, ad: RewardedAd): AdResult = suspendCancellableCoroutine { cont ->
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                if (cont.isActive) cont.resume(if (earned) AdResult.Rewarded else AdResult.Skipped)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                if (cont.isActive) cont.resume(AdResult.Unavailable)
            }
        }
        ad.show(activity) { earned = true }
    }

    private fun isPrivacyOptionsRequired() =
        consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    private companion object {
        const val LoadTimeoutMs = 12_000L
        /** A loaded rewarded ad expires after an hour; refresh well before that. */
        const val MaxAgeMs = 45 * 60 * 1000L
    }
}

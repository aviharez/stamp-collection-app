package com.aviharez.stamp.ui.screens.paywall

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aviharez.stamp.data.ads.AdOffer
import com.aviharez.stamp.data.ads.AdResult
import com.aviharez.stamp.data.ads.RewardedAds
import com.aviharez.stamp.data.billing.BillingRepository
import com.aviharez.stamp.data.billing.ProStatus
import com.aviharez.stamp.data.billing.OffersState
import com.aviharez.stamp.data.billing.ProPlan
import com.aviharez.stamp.data.billing.PurchaseOutcome
import com.aviharez.stamp.ui.navigation.PaywallRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Why the paywall opened: decides the overline and which perk is lit up. */
enum class PaywallReason { Profile, Albums, Watermark, Widget, Filters, Postcards, Scrapbook }

enum class PaywallMessage { Pending, Failed, NothingToRestore, RestoreFailed }

/** Why the paywall closes by itself. */
enum class PaywallExit { ProOn, BonusAlbum }

data class PaywallUiState(
    val reason: PaywallReason = PaywallReason.Profile,
    val offers: OffersState = OffersState.Loading,
    val selected: ProPlan = ProPlan.Lifetime,
    val busy: Boolean = false,
    val message: PaywallMessage? = null,
    /** "+1 album · short ad" (only offered when the album limit opened the paywall). */
    val ad: AdOffer = AdOffer.Idle,
) {
    val selectedOffer get() = (offers as? OffersState.Ready)?.offers?.get(selected)
}

@HiltViewModel
class PaywallViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val billing: BillingRepository,
    private val ads: RewardedAds,
    private val proStatus: ProStatus,
) : ViewModel() {

    private val reason = runCatching { PaywallReason.valueOf(savedState.toRoute<PaywallRoute>().reason) }
        .getOrDefault(PaywallReason.Profile)
    private val local = MutableStateFlow(PaywallUiState(reason = reason))

    val state: StateFlow<PaywallUiState> = combine(local, billing.offers) { l, offers -> l.copy(offers = offers) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaywallUiState(reason = reason))

    private val _close = Channel<PaywallExit>(Channel.CONFLATED)
    /** Pro is now on (bought or restored), or an album was earned: leave the paywall. */
    val close: Flow<PaywallExit> = _close.receiveAsFlow()

    init {
        loadOffers()
        if (reason == PaywallReason.Albums) ads.preload()
        viewModelScope.launch {
            billing.outcomes.collect { outcome ->
                local.update {
                    it.copy(
                        busy = false,
                        message = when (outcome) {
                            PurchaseOutcome.Pending -> PaywallMessage.Pending
                            PurchaseOutcome.Failed -> PaywallMessage.Failed
                            else -> null
                        },
                    )
                }
                if (outcome == PurchaseOutcome.Purchased) _close.send(PaywallExit.ProOn)
            }
        }
    }

    fun loadOffers() {
        viewModelScope.launch { billing.loadOffers() }
    }

    fun select(plan: ProPlan) {
        local.update { it.copy(selected = plan, message = null) }
    }

    fun buy(activity: Activity) {
        val offer = state.value.selectedOffer ?: return
        if (local.value.busy) return
        local.update { it.copy(busy = true, message = null) }
        if (!billing.launch(activity, offer)) {
            local.update { it.copy(busy = false, message = PaywallMessage.Failed) }
        }
    }

    fun restore() {
        if (local.value.busy) return
        local.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val owned = billing.refreshPurchases()
            local.update {
                it.copy(
                    busy = false,
                    message = when (owned) {
                        true -> null
                        false -> PaywallMessage.NothingToRestore
                        null -> PaywallMessage.RestoreFailed
                    },
                )
            }
            if (owned == true) _close.send(PaywallExit.ProOn)
        }
    }

    fun watchAdForAlbum(activity: Activity) {
        if (local.value.ad == AdOffer.Loading || local.value.busy) return
        local.update { it.copy(ad = AdOffer.Loading, message = null) }
        viewModelScope.launch {
            val result = ads.show(activity)
            if (result == AdResult.Rewarded) proStatus.addBonusAlbum()
            local.update { it.copy(ad = if (result == AdResult.Unavailable) AdOffer.Unavailable else AdOffer.Idle) }
            if (result == AdResult.Rewarded) _close.send(PaywallExit.BonusAlbum)
        }
    }
}

package com.goomistudio.postmory.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.goomistudio.postmory.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** The two ways to buy Pro. Product ids must match Play Console. Prices always come from Play. */
enum class ProPlan(val productId: String, val productType: String) {
    Lifetime("pro_lifetime", ProductType.INAPP),
    Yearly("pro_yearly", ProductType.SUBS),
}

/** A plan as Google Play sells it to this user (price already localised). */
data class PlanOffer(
    val plan: ProPlan,
    val formattedPrice: String,
    internal val details: ProductDetails,
    internal val offerToken: String?,
)

sealed interface OffersState {
    data object Loading : OffersState
    data class Ready(val offers: Map<ProPlan, PlanOffer>) : OffersState
    /** Play is missing, offline, or the products are not set up. */
    data object Unavailable : OffersState
}

sealed interface PurchaseOutcome {
    data object Purchased : PurchaseOutcome
    /** Paid with a slow method (cash, bank); Pro turns on when Play confirms it. */
    data object Pending : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data object Failed : PurchaseOutcome
}

/**
 * Google Play Billing for Pro: loads the offers, runs the purchase flow, acknowledges purchases
 * and keeps [ProStatus] in step with what Play says this account owns (restore = refresh).
 */
@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val proStatus: ProStatus,
    @param:AppScope private val scope: CoroutineScope,
) : PurchasesUpdatedListener {

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()
    private val connectLock = Mutex()

    private val _offers = MutableStateFlow<OffersState>(OffersState.Loading)
    val offers: StateFlow<OffersState> = _offers.asStateFlow()

    private val _outcomes = MutableSharedFlow<PurchaseOutcome>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val outcomes: SharedFlow<PurchaseOutcome> = _outcomes.asSharedFlow()

    /** App start: bring the cached Pro flag in line with Play (refunds, expired subscriptions). */
    fun start() {
        scope.launch { refreshPurchases() }
    }

    suspend fun loadOffers() {
        if (_offers.value is OffersState.Ready) return
        _offers.value = OffersState.Loading
        if (!connect()) {
            _offers.value = OffersState.Unavailable
            return
        }
        val offers = buildMap {
            for (plan in ProPlan.entries) {
                val details = queryDetails(plan) ?: continue
                toOffer(plan, details)?.let { put(plan, it) }
            }
        }
        _offers.value = if (offers.isEmpty()) OffersState.Unavailable else OffersState.Ready(offers)
    }

    /** Opens Play's purchase sheet. Main thread. Returns false if it could not be shown. */
    fun launch(activity: Activity, offer: PlanOffer): Boolean {
        val params = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(offer.details)
            .apply { offer.offerToken?.let { setOfferToken(it) } }
            .build()
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build(),
        )
        return result.responseCode == BillingResponseCode.OK
    }

    /**
     * Asks Play what this account owns and updates [ProStatus]. Returns whether Pro is owned, or
     * null when Play could not be reached (the cached flag is then left alone).
     */
    suspend fun refreshPurchases(): Boolean? {
        if (!connect()) return null
        val owned = mutableListOf<Purchase>()
        for (type in listOf(ProductType.INAPP, ProductType.SUBS)) {
            val (result, purchases) = suspendCancellableCoroutine { cont ->
                client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { r, list ->
                    cont.resume(r to list)
                }
            }
            if (result.responseCode != BillingResponseCode.OK) return null
            owned += purchases
        }
        val pro = owned.filter { it.isPro() && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        pro.forEach { acknowledge(it) }
        proStatus.setPurchased(pro.isNotEmpty())
        return pro.isNotEmpty()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        scope.launch {
            when (result.responseCode) {
                BillingResponseCode.OK -> {
                    val ours = purchases.orEmpty().filter { it.isPro() }
                    when {
                        ours.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } -> {
                            ours.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { acknowledge(it) }
                            proStatus.setPurchased(true)
                            _outcomes.emit(PurchaseOutcome.Purchased)
                        }
                        ours.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> _outcomes.emit(PurchaseOutcome.Pending)
                        else -> _outcomes.emit(PurchaseOutcome.Failed)
                    }
                }
                BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    refreshPurchases()
                    _outcomes.emit(PurchaseOutcome.Purchased)
                }
                BillingResponseCode.USER_CANCELED -> _outcomes.emit(PurchaseOutcome.Cancelled)
                else -> _outcomes.emit(PurchaseOutcome.Failed)
            }
        }
    }

    private suspend fun connect(): Boolean = connectLock.withLock {
        if (client.isReady) return@withLock true
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    private suspend fun queryDetails(plan: ProPlan): ProductDetails? = suspendCancellableCoroutine { cont ->
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(plan.productId)
                        .setProductType(plan.productType)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            val found = if (result.responseCode == BillingResponseCode.OK) details.productDetailsList.firstOrNull() else null
            cont.resume(found)
        }
    }

    private fun toOffer(plan: ProPlan, details: ProductDetails): PlanOffer? = when (plan.productType) {
        ProductType.SUBS -> {
            // The base plan (no offer id): the plain yearly price, no trial (decided 2026-11-04).
            val offer = details.subscriptionOfferDetails?.let { list -> list.firstOrNull { it.offerId == null } ?: list.firstOrNull() }
            val price = offer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
            if (offer != null && price != null) PlanOffer(plan, price, details, offer.offerToken) else null
        }
        else -> {
            val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull()
            offer?.let { PlanOffer(plan, it.formattedPrice, details, it.offerToken) }
        }
    }

    private suspend fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        suspendCancellableCoroutine { cont ->
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            ) { cont.resume(Unit) }
        }
    }

    private fun Purchase.isPro(): Boolean = products.any { id -> ProPlan.entries.any { it.productId == id } }
}

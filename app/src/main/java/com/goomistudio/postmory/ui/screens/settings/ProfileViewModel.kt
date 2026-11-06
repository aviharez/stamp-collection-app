package com.goomistudio.postmory.ui.screens.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goomistudio.postmory.data.billing.BillingRepository
import com.goomistudio.postmory.data.billing.ProStatus
import com.goomistudio.postmory.data.repo.AlbumRepository
import com.goomistudio.postmory.data.repo.StampRepository
import com.goomistudio.postmory.data.settings.SettingsRepository
import com.goomistudio.postmory.di.AppScope
import com.goomistudio.postmory.domain.model.Stamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The passport card: what this phone's album adds up to. */
@Immutable
data class Passport(
    val stamps: Int,
    val albums: Int,
    val places: Int,
    val bestStreak: Int,
    /** First stamped day; null with no stamps. */
    val since: LocalDate?,
    val newest: Stamp?,
)

enum class RestoreMessage { Working, NothingToRestore, Failed }

@Immutable
data class ProfileUiState(
    /** Null until the database answers (nothing flashes "0 stamps"). */
    val passport: Passport? = null,
    val isPro: Boolean = false,
    val mirrorSelfie: Boolean = true,
    /** The user wants places on postmarks (it still needs location permission to happen). */
    val postmarkPlace: Boolean = true,
    /** Crash reports + anonymous usage counts (UsageStats). */
    val usageStats: Boolean = true,
    val restore: RestoreMessage? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    stamps: StampRepository,
    albums: AlbumRepository,
    proStatus: ProStatus,
    private val settings: SettingsRepository,
    private val billing: BillingRepository,
    @param:AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val restore = MutableStateFlow<RestoreMessage?>(null)

    private val passport = combine(
        stamps.observeTotals(),
        albums.observeCount(),
        stamps.observeStreak(),
        stamps.observeFirstDay(),
        stamps.observeRecent(1),
    ) { totals, albumCount, streak, first, recent ->
        Passport(
            stamps = totals.stamps,
            albums = albumCount,
            places = totals.places,
            bestStreak = streak.best,
            since = first,
            newest = recent.firstOrNull(),
        )
    }

    private val switches = combine(settings.mirrorSelfie, settings.postmarkPlace, settings.usageStats) { mirror, place, usage ->
        ProfileUiState(mirrorSelfie = mirror, postmarkPlace = place, usageStats = usage)
    }

    val state: StateFlow<ProfileUiState> = combine(
        passport,
        proStatus.isPro,
        switches,
        restore,
    ) { passport, pro, switches, restore ->
        switches.copy(passport = passport, isPro = pro, restore = if (pro) null else restore)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    // App scope: a setting tapped just before leaving Profile is still written.
    fun setMirrorSelfie(on: Boolean) {
        appScope.launch { settings.setMirrorSelfie(on) }
    }

    fun setPostmarkPlace(on: Boolean) {
        appScope.launch { settings.setPostmarkPlace(on) }
    }

    fun setUsageStats(on: Boolean) {
        appScope.launch { settings.setUsageStats(on) }
    }

    /** The system location dialog was answered from Profile: the camera won't ask again. */
    fun onLocationAnswered(granted: Boolean) {
        appScope.launch {
            settings.setLocationAsked()
            if (granted) settings.setPostmarkPlace(true)
        }
    }

    /** "Already bought it? Restore": asks Play again; Pro arriving swaps the card for the postmark. */
    fun restorePurchases() {
        if (restore.value == RestoreMessage.Working) return
        restore.value = RestoreMessage.Working
        viewModelScope.launch {
            val owned = billing.refreshPurchases()
            restore.update {
                when (owned) {
                    true -> null
                    false -> RestoreMessage.NothingToRestore
                    null -> RestoreMessage.Failed
                }
            }
        }
    }
}

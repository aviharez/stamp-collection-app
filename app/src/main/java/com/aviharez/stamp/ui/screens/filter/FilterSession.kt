package com.aviharez.stamp.ui.screens.filter

import android.app.Activity
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aviharez.stamp.data.ads.AdOffer
import com.aviharez.stamp.data.ads.AdResult
import com.aviharez.stamp.data.ads.RewardedAds
import com.aviharez.stamp.data.billing.EdgeAccess
import com.aviharez.stamp.data.billing.LookAccess
import com.aviharez.stamp.data.image.StampFilterer
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.domain.model.StampFilter
import com.aviharez.stamp.domain.model.StampLook
import com.aviharez.stamp.ui.screens.paywall.PaywallReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** What the look picker (filters and edges) shows for one stamp. */
@Immutable
data class FilterUi(
    /** False for stamps made before filters (no crop saved) or whose original is gone. */
    val available: Boolean = false,
    val thumbnail: ImageBitmap? = null,
    /** The look the user last picked: saved, being saved, or a Pro look being tried on. */
    val selected: StampLook = StampLook(),
    /** The look the stamp has in the album. */
    val saved: StampLook = StampLook(),
    /** A free user trying on a Pro look: that version of the stamp (never saved). */
    val preview: File? = null,
    /** A new version is being drawn. */
    val busy: Boolean = false,
    /** A Pro look's try-on picture is being drawn. */
    val previewing: Boolean = false,
    val access: LookAccess = LookAccess(),
    /** "24h · short ad" for the Pro edge being tried on. */
    val ad: AdOffer = AdOffer.Idle,
) {
    /** The picked look is still being drawn (saved or tried on): the picker shows it working. */
    val working: Boolean get() = busy || previewing

    /** A Pro look is on show but not kept: the screen says so and offers Pro. */
    val tryingPro: Boolean get() = selected != saved && !access.canUse(selected) && !busy

    private val filterLocked: Boolean get() = !selected.filter.free && !access.isPro

    /** Only the edge stands between the user and this look, so a short ad can unlock it for a day. */
    val edgeAdOffered: Boolean get() = tryingPro && !filterLocked

    /** What "Keep … with Pro" names, and why the paywall opens. */
    val keepReason: PaywallReason get() = if (filterLocked) PaywallReason.Filters else PaywallReason.Edges

    /** The edge the stamp is drawn with on screen right now (a try-on included). */
    val shownEdge: PerforationStyle get() = if (preview != null) selected.edge else saved.edge
}

/**
 * Look picking (filter + edge) for one stamp, shared by Stamped! and the stamp page. A look the
 * user may use (free, Pro, or an edge unlocked for a day) is saved as soon as it is tapped; any
 * other is drawn into a preview only. Getting Pro or watching the edge's short ad while trying one
 * on saves it. A saved edge becomes the edge new stamps start with.
 *
 * @param scope the screen's scope (thumbnail, previews).
 * @param appScope saves finish even if the screen closes.
 */
class FilterSession(
    private val filterer: StampFilterer,
    private val edges: EdgeAccess,
    private val ads: RewardedAds,
    private val scope: CoroutineScope,
    private val appScope: CoroutineScope,
    stamp: Flow<Stamp?>,
) {
    private val _state = MutableStateFlow(FilterUi())
    val state: StateFlow<FilterUi> = _state.asStateFlow()

    private var current: Stamp? = null
    private var job: Job? = null

    init {
        scope.launch {
            var thumbSpec: String? = null
            stamp.filterNotNull().distinctUntilChangedBy { it.imagePath }.collect { s ->
                val first = current == null
                current = s
                val saved = StampLook(s.filter, s.perforation)
                _state.update {
                    it.copy(
                        available = filterer.canFilter(s),
                        saved = saved,
                        selected = if (first || !it.busy && it.preview == null) saved else it.selected,
                    )
                }
                // Again after a reframe (new crop), not after a look change.
                if (s.renderSpec != thumbSpec && filterer.canFilter(s)) {
                    thumbSpec = s.renderSpec
                    val thumb = filterer.thumbnail(s)?.asImageBitmap()
                    _state.update { it.copy(thumbnail = thumb, available = thumb != null) }
                }
            }
        }
        scope.launch {
            edges.access.collect { access -> _state.update { it.copy(access = access) } }
        }
        scope.launch {
            state.collect { ui ->
                // Got Pro (paywall and back) or a day of this edge (ad) while trying a look on: keep it.
                if (ui.selected != ui.saved && ui.preview != null && !ui.busy && ui.access.canUse(ui.selected)) pick(ui.selected)
            }
        }
    }

    /** The stamp is being thrown away: stop drawing it. */
    fun cancel() {
        job?.cancel()
    }

    /** Drops a try-on (tray closed): back to the saved look. A save in progress still finishes. */
    fun revert() {
        val ui = _state.value
        if (ui.busy || ui.selected == ui.saved) return
        job?.cancel()
        _state.update { it.copy(selected = it.saved, preview = null, previewing = false) }
    }

    fun pickFilter(filter: StampFilter) = pick(_state.value.selected.copy(filter = filter))

    fun pickEdge(edge: PerforationStyle) = pick(_state.value.selected.copy(edge = edge))

    /** Free user is looking at the edge offer: get an ad ready in case they want it. */
    fun preloadAd() = ads.preload()

    /** "24h · short ad": the edge on show is unlocked for a day once the ad is watched (then saved, see init). */
    fun watchEdgeAd(activity: Activity) {
        val ui = _state.value
        if (ui.ad == AdOffer.Loading || !ui.edgeAdOffered) return
        val edge = ui.selected.edge
        _state.update { it.copy(ad = AdOffer.Loading) }
        appScope.launch {
            val result = ads.show(activity)
            if (result == AdResult.Rewarded) edges.startTrial(edge)
            _state.update { it.copy(ad = if (result == AdResult.Unavailable) AdOffer.Unavailable else AdOffer.Idle) }
        }
    }

    private fun pick(look: StampLook) {
        val stamp = current ?: return
        job?.cancel()
        val ui = _state.value
        if (!ui.access.canUse(look)) {
            _state.update { it.copy(selected = look, busy = false, previewing = true, ad = if (it.ad == AdOffer.Loading) it.ad else AdOffer.Idle) }
            job = scope.launch {
                val file = filterer.preview(stamp, look)
                _state.update {
                    if (file == null) it.copy(selected = it.saved, preview = null, previewing = false) else it.copy(preview = file, previewing = false)
                }
            }
            return
        }
        if (look == ui.saved) {
            _state.update { it.copy(selected = look, preview = null, busy = false, previewing = false) }
            return
        }
        _state.update { it.copy(selected = look, busy = true, previewing = false) }
        job = appScope.launch {
            val ok = filterer.apply(current ?: stamp, look)
            if (ok && look.edge != ui.saved.edge) edges.setLastEdge(look.edge)
            _state.update {
                if (ok) it.copy(saved = look, preview = null, busy = false) else it.copy(selected = it.saved, preview = null, busy = false)
            }
        }
    }
}

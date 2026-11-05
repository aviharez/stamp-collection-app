package com.aviharez.stamp.ui.screens.filter

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.aviharez.stamp.data.image.StampFilterer
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.domain.model.StampFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** What the filter picker shows for one stamp. */
@Immutable
data class FilterUi(
    /** False for stamps made before filters (no crop saved) or whose original is gone. */
    val available: Boolean = false,
    val thumbnail: ImageBitmap? = null,
    /** The filter the user last tapped: saved, being saved, or a Pro filter being tried on. */
    val selected: StampFilter = StampFilter.Original,
    /** The filter the stamp has in the album. */
    val saved: StampFilter = StampFilter.Original,
    /** A free user trying on a Pro filter: that version of the stamp (never saved). */
    val preview: File? = null,
    /** A new version is being drawn. */
    val busy: Boolean = false,
) {
    /** A Pro filter is on show but not kept: the screen says so and offers Pro. */
    val tryingPro: Boolean get() = selected != saved && !selected.free && !busy
}

/**
 * Filter picking for one stamp, shared by Stamped! and the stamp page. A free filter (or any
 * filter for Pro) is saved as soon as it is tapped; a Pro filter for a free user is drawn into a
 * preview only. Getting Pro while trying one on saves it.
 *
 * @param scope the screen's scope (thumbnail, previews).
 * @param appScope saves finish even if the screen closes.
 */
class FilterSession(
    private val filterer: StampFilterer,
    private val scope: CoroutineScope,
    private val appScope: CoroutineScope,
    stamp: Flow<Stamp?>,
    isPro: Flow<Boolean>,
) {
    private val _state = MutableStateFlow(FilterUi())
    val state: StateFlow<FilterUi> = _state.asStateFlow()

    private var current: Stamp? = null
    private var pro = false
    private var job: Job? = null

    init {
        scope.launch {
            var thumbSpec: String? = null
            stamp.filterNotNull().distinctUntilChangedBy { it.imagePath }.collect { s ->
                val first = current == null
                current = s
                _state.update {
                    it.copy(
                        available = filterer.canFilter(s),
                        saved = s.filter,
                        selected = if (first || !it.busy && it.preview == null) s.filter else it.selected,
                    )
                }
                // Again after a reframe (new crop), not after a filter change.
                if (s.renderSpec != thumbSpec && filterer.canFilter(s)) {
                    thumbSpec = s.renderSpec
                    val thumb = filterer.thumbnail(s)?.asImageBitmap()
                    _state.update { it.copy(thumbnail = thumb, available = thumb != null) }
                }
            }
        }
        scope.launch {
            combine(isPro, state) { p, ui -> p to ui }.collect { (p, ui) ->
                pro = p
                // Bought Pro while trying a filter on (paywall and back): keep it.
                if (p && ui.selected != ui.saved && ui.preview != null && !ui.busy) pick(ui.selected)
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
        _state.update { it.copy(selected = it.saved, preview = null) }
    }

    fun pick(filter: StampFilter) {
        val stamp = current ?: return
        job?.cancel()
        if (!pro && !filter.free) {
            _state.update { it.copy(selected = filter, busy = false) }
            job = scope.launch {
                val file = filterer.preview(stamp, filter)
                _state.update { if (file == null) it.copy(selected = it.saved, preview = null) else it.copy(preview = file) }
            }
            return
        }
        if (filter == _state.value.saved) {
            _state.update { it.copy(selected = filter, preview = null, busy = false) }
            return
        }
        _state.update { it.copy(selected = filter, busy = true) }
        job = appScope.launch {
            val ok = filterer.apply(current ?: stamp, filter)
            _state.update {
                if (ok) it.copy(saved = filter, preview = null, busy = false) else it.copy(selected = it.saved, preview = null, busy = false)
            }
        }
    }
}

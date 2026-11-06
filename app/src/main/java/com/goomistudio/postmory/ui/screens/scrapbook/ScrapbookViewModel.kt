package com.goomistudio.postmory.ui.screens.scrapbook

import android.content.Context
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.goomistudio.postmory.R
import com.goomistudio.postmory.data.billing.ProStatus
import com.goomistudio.postmory.data.repo.AlbumRepository
import com.goomistudio.postmory.data.repo.ScrapCodec
import com.goomistudio.postmory.data.repo.ScrapbookRepository
import com.goomistudio.postmory.data.repo.StampRepository
import com.goomistudio.postmory.domain.model.Album
import com.goomistudio.postmory.domain.model.Place
import com.goomistudio.postmory.domain.model.ScrapItem
import com.goomistudio.postmory.domain.model.ScrapKind
import com.goomistudio.postmory.domain.model.ScrapPage
import com.goomistudio.postmory.domain.model.ScrapRules
import com.goomistudio.postmory.domain.model.ScrapSticker
import com.goomistudio.postmory.domain.model.Stamp
import com.goomistudio.postmory.ui.components.postmarkLines
import com.goomistudio.postmory.ui.navigation.ScrapbookRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.hypot
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the editor is doing with the page right now. */
data class ScrapDraft(
    val items: List<ScrapItem> = emptyList(),
    val selectedId: String? = null,
    /** The layout a new page started from (the Layouts tab shows it picked); null once loaded from the DB. */
    val layout: ScrapLayout? = null,
    /** Changed since opened (closing asks first). */
    val dirty: Boolean = false,
    /** The note or title being typed into. */
    val writingId: String? = null,
)

data class ScrapbookUiState(
    val loaded: Boolean = false,
    val album: Album? = null,
    /** The album's stamps, newest first (the Stamps tray). */
    val albumStamps: List<Stamp> = emptyList(),
    /** Every stamp the page can draw, by id. */
    val stamps: Map<String, Stamp> = emptyMap(),
    val draft: ScrapDraft = ScrapDraft(),
    val isNew: Boolean = true,
    val isPro: Boolean = false,
) {
    val selected: ScrapItem? get() = draft.items.firstOrNull { it.id == draft.selectedId }
    val writing: ScrapItem? get() = draft.items.firstOrNull { it.id == draft.writingId }
    val full: Boolean get() = draft.items.size >= ScrapRules.MaxItems
}

sealed interface ScrapbookEvent {
    data class Saved(val pageId: String) : ScrapbookEvent
    data object Deleted : ScrapbookEvent
    data object NeedPro : ScrapbookEvent
}

/**
 * The scrapbook page editor (CLAUDE.md §3 V1.1). A new page starts from a layout filled with the
 * album's newest stamps; then any piece can be moved, sized, turned, brought to the front or
 * removed, and stamps, stickers and writing can be added. Saving is Pro; free users can build the
 * whole page first, and if they get Pro from the paywall it saves right away.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScrapbookViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    albums: AlbumRepository,
    private val stamps: StampRepository,
    private val pages: ScrapbookRepository,
    private val pro: ProStatus,
    private val clock: Clock,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val route = savedState.toRoute<ScrapbookRoute>()
    val albumId = route.albumId
    private val isNew = route.pageId == null
    private val pageId: String = route.pageId ?: savedState[NewIdKey] ?: newScrapId().also { savedState[NewIdKey] = it }
    private var createdAt: Instant? = null

    private val draft = MutableStateFlow<ScrapDraft?>(null)
    private val albumStamps = stamps.observeByAlbum(albumId)
    private var pendingSave = false
    private val _events = Channel<ScrapbookEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Stamps the page holds from other albums (moved there after the page was made). */
    private val pageStamps = draft.filterNotNull()
        .map { d -> d.items.mapNotNullTo(HashSet()) { it.ref.takeIf { _ -> it.kind == ScrapKind.Stamp } } }
        .distinctUntilChanged()
        .flatMapLatest { stamps.observeByIds(it) }

    val state: StateFlow<ScrapbookUiState> = combine(
        albums.observe(albumId),
        albumStamps,
        pageStamps,
        draft.filterNotNull(),
        pro.isPro,
    ) { album, inAlbum, onPage, d, isPro ->
        ScrapbookUiState(
            loaded = true,
            album = album,
            albumStamps = inAlbum,
            stamps = (onPage + inAlbum).associateBy { it.id },
            draft = d,
            isNew = isNew,
            isPro = isPro,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScrapbookUiState())

    init {
        savedState.setSavedStateProvider(DraftKey) { draft.value?.toBundle(createdAt) ?: Bundle() }
        viewModelScope.launch { load() }
        // Came back from the paywall with Pro: keep the page they made.
        viewModelScope.launch {
            pro.isPro.collect { isPro -> if (isPro && pendingSave) save() }
        }
    }

    private suspend fun load() {
        val restored = savedState.get<Bundle>(DraftKey)?.takeIf { it.containsKey(ItemsKey) }
        if (restored != null) {
            createdAt = restored.getLong(CreatedKey, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
            draft.value = ScrapDraft(
                items = ScrapCodec.decode(restored.getString(ItemsKey).orEmpty()),
                layout = restored.getString(LayoutKey)?.let { n -> ScrapLayout.entries.firstOrNull { it.name == n } },
                dirty = restored.getBoolean(DirtyKey),
            )
            return
        }
        val existing = route.pageId?.let { pages.get(it) }
        if (existing != null) {
            createdAt = existing.createdAt
            draft.value = ScrapDraft(items = existing.items)
        } else {
            val newest = albumStamps.first()
            val layout = ScrapLayout.defaultFor(newest.size)
            draft.value = ScrapDraft(items = layout.build(layoutInput(newest)), layout = layout)
        }
    }

    fun applyLayout(layout: ScrapLayout) {
        val current = state.value
        draft.update { it?.copy(items = layout.build(layoutInput(current.albumStamps)), layout = layout, selectedId = null, writingId = null, dirty = true) }
    }

    fun select(id: String?) = draft.update { d -> d?.copy(selectedId = id, writingId = d.writingId.takeIf { id == it }) }

    /** A drag / pinch / twist step: [dx], [dy] as fractions of the page. */
    fun transform(id: String, dx: Float, dy: Float, zoom: Float, rotation: Float) = edit(id) {
        it.copy(
            x = ScrapRules.clampPosition(it.x + dx),
            y = ScrapRules.clampPosition(it.y + dy),
            scale = ScrapRules.clampScale(it.scale * zoom),
            rotation = (it.rotation + rotation) % FullTurn,
        )
    }

    fun resize(id: String, factor: Float) = transform(id, 0f, 0f, factor, 0f)

    fun turn(id: String) = transform(id, 0f, 0f, 1f, TurnStep)

    fun toFront(id: String) = draft.update { d ->
        val item = d?.items?.firstOrNull { it.id == id } ?: return@update d
        d.copy(items = d.items - item + item, dirty = true)
    }

    fun remove(id: String) = draft.update { d ->
        d?.copy(items = d.items.filterNot { it.id == id }, selectedId = null, writingId = null, dirty = true)
    }

    fun write(id: String) = draft.update { it?.copy(selectedId = id, writingId = id) }

    fun setText(id: String, text: String) = edit(id) {
        val limit = if (it.kind == ScrapKind.Title) ScrapRules.MaxTitleLength else ScrapRules.MaxNoteLength
        it.copy(text = text.take(limit))
    }

    fun doneWriting() = draft.update { it?.copy(writingId = null) }

    fun addStamp(stamp: Stamp) = add(ScrapItem(newScrapId(), ScrapKind.Stamp, ref = stamp.id, x = 0f, y = 0f))

    fun addSticker(sticker: ScrapSticker) {
        val text = when (sticker) {
            ScrapSticker.WaxSeal -> sealLetter(state.value.album)
            ScrapSticker.Ticket -> today(R.string.pattern_postmark_day)
            ScrapSticker.Postmark -> postmark(state.value.albumStamps.firstOrNull())
            else -> ""
        }
        add(ScrapItem(newScrapId(), ScrapKind.Sticker, ref = sticker.name, text = text, x = 0f, y = 0f))
    }

    fun addWriting(kind: ScrapKind) {
        val id = newScrapId()
        add(ScrapItem(id, kind, x = 0f, y = 0f))
        write(id)
    }

    /** New pieces land on the emptiest spot of the page (furthest from every piece), tilted, on top. */
    private fun add(item: ScrapItem) = draft.update { d ->
        if (d == null || d.items.size >= ScrapRules.MaxItems) return@update d
        val r = Random(clock.millis())
        val spot = AddSpots.maxBy { (x, y) ->
            // Page is 3 : 4, so a step down counts for more than a step across.
            d.items.minOfOrNull { hypot(it.x - x, (it.y - y) / ScrapRules.Aspect) } ?: Float.MAX_VALUE
        }
        val placed = item.copy(
            x = spot.first + r.nextFloat() * AddJitter - AddJitter / 2,
            y = spot.second + r.nextFloat() * AddJitter - AddJitter / 2,
            rotation = r.nextFloat() * AddTilt * 2 - AddTilt,
        )
        d.copy(items = d.items + placed, selectedId = placed.id, writingId = null, dirty = true)
    }

    fun save() {
        val d = draft.value ?: return
        viewModelScope.launch {
            if (!pro.isProNow()) {
                pendingSave = true
                _events.send(ScrapbookEvent.NeedPro)
                return@launch
            }
            pendingSave = false
            val now = clock.instant()
            val id = pages.save(ScrapPage(pageId, albumId, createdAt ?: now, now, d.items))
            createdAt = createdAt ?: now
            draft.update { it?.copy(dirty = false) }
            _events.send(ScrapbookEvent.Saved(id))
        }
    }

    /** Leaving the paywall without Pro: don't save behind their back later. */
    fun cancelPendingSave() {
        pendingSave = false
    }

    fun delete() {
        viewModelScope.launch {
            pages.delete(pageId)
            _events.send(ScrapbookEvent.Deleted)
        }
    }

    private fun edit(id: String, change: (ScrapItem) -> ScrapItem) = draft.update { d ->
        d?.copy(items = d.items.map { if (it.id == id) change(it) else it }, dirty = true)
    }

    private fun layoutInput(newestFirst: List<Stamp>) = ScrapLayoutInput(
        stamps = newestFirst,
        albumName = state.value.album?.name.orEmpty(),
        postmark = postmark(newestFirst.firstOrNull()),
        sealLetter = sealLetter(state.value.album),
        ticketDate = today(R.string.pattern_postmark_day),
    )

    private fun postmark(stamp: Stamp?): String {
        val lines = postmarkLines(stamp?.place ?: Place.Unknown, stamp?.day ?: LocalDate.now(clock), context.resources, locale())
        return "${lines.top}\n${lines.bottom}"
    }

    private fun sealLetter(album: Album?): String =
        album?.name?.trim()?.firstOrNull { it.isLetterOrDigit() }?.uppercase(locale()) ?: DefaultSeal

    private fun today(pattern: Int): String =
        DateTimeFormatter.ofPattern(context.getString(pattern), locale()).format(LocalDate.now(clock)).uppercase(locale())

    private fun locale() = context.resources.configuration.locales[0]

    private fun ScrapDraft.toBundle(created: Instant?) = bundleOf(
        ItemsKey to ScrapCodec.encode(items),
        LayoutKey to layout?.name,
        DirtyKey to dirty,
        CreatedKey to (created?.toEpochMilli() ?: 0L),
    )

    private companion object {
        const val DraftKey = "scrap_draft"
        const val NewIdKey = "scrap_new_id"
        const val ItemsKey = "items"
        const val LayoutKey = "layout"
        const val DirtyKey = "dirty"
        const val CreatedKey = "created"
        const val FullTurn = 360f
        const val TurnStep = 15f
        /** Where a new piece may land: a 3 × 4 grid, middle first so an empty page fills from the centre. */
        val AddSpots: List<Pair<Float, Float>> = buildList {
            add(0.5f to 0.5f)
            for (y in listOf(0.2f, 0.4f, 0.6f, 0.8f)) for (x in listOf(0.25f, 0.5f, 0.75f)) add(x to y)
        }
        const val AddJitter = 0.04f
        const val AddTilt = 6f
        const val DefaultSeal = "S"
    }
}

package com.aviharez.stamp.ui.screens.scrapbook

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.Album
import com.aviharez.stamp.domain.model.AlbumCategory
import com.aviharez.stamp.domain.model.AlbumCoverColor
import com.aviharez.stamp.domain.model.ScrapItem
import com.aviharez.stamp.domain.model.ScrapKind
import com.aviharez.stamp.domain.model.ScrapRules
import com.aviharez.stamp.domain.model.ScrapSticker
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.ui.components.CircleIconButton
import com.aviharez.stamp.ui.components.GrainedSurface
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.PaperChip
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.components.PillStyle
import com.aviharez.stamp.ui.components.RenderedStamp
import com.aviharez.stamp.ui.components.ScrapStickerArt
import com.aviharez.stamp.ui.components.description
import com.aviharez.stamp.ui.components.label
import com.aviharez.stamp.ui.components.previewStamps
import com.aviharez.stamp.ui.screens.export.ScaledArt
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.tintedShadow
import java.io.File
import java.time.Instant
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Editor for one scrapbook page (new or existing). */
@Composable
fun ScrapbookScreen(
    onClose: () -> Unit,
    onSaved: (pageId: String) -> Unit,
    onDeleted: () -> Unit,
    onNeedPro: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScrapbookViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ScrapbookEvent.Saved -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSaved(event.pageId)
                }
                ScrapbookEvent.Deleted -> onDeleted()
                ScrapbookEvent.NeedPro -> onNeedPro()
            }
        }
    }
    ScrapbookContent(
        state = state,
        actions = remember(viewModel) { ScrapbookActions.of(viewModel) },
        onClose = onClose,
        modifier = modifier,
    )
}

/** Everything the editor can ask of its ViewModel (kept as one object so previews can pass no-ops). */
class ScrapbookActions(
    val select: (String?) -> Unit = {},
    val transform: (id: String, dx: Float, dy: Float, zoom: Float, rotation: Float) -> Unit = { _, _, _, _, _ -> },
    val resize: (String, Float) -> Unit = { _, _ -> },
    val turn: (String) -> Unit = {},
    val toFront: (String) -> Unit = {},
    val remove: (String) -> Unit = {},
    val write: (String) -> Unit = {},
    val setText: (String, String) -> Unit = { _, _ -> },
    val doneWriting: () -> Unit = {},
    val applyLayout: (ScrapLayout) -> Unit = {},
    val addStamp: (Stamp) -> Unit = {},
    val addSticker: (ScrapSticker) -> Unit = {},
    val addWriting: (ScrapKind) -> Unit = {},
    val save: () -> Unit = {},
    val delete: () -> Unit = {},
) {
    companion object {
        fun of(vm: ScrapbookViewModel) = ScrapbookActions(
            select = vm::select,
            transform = vm::transform,
            resize = vm::resize,
            turn = vm::turn,
            toFront = vm::toFront,
            remove = vm::remove,
            write = vm::write,
            setText = vm::setText,
            doneWriting = vm::doneWriting,
            applyLayout = vm::applyLayout,
            addStamp = vm::addStamp,
            addSticker = vm::addSticker,
            addWriting = vm::addWriting,
            save = vm::save,
            delete = vm::delete,
        )
    }
}

private enum class TrayTab(val label: Int) {
    Layouts(R.string.scrap_tab_layouts),
    Stamps(R.string.scrap_tab_stamps),
    Stickers(R.string.scrap_tab_stickers),
    Write(R.string.scrap_tab_write),
}

private enum class Confirm { Leave, Delete }

@Composable
fun ScrapbookContent(
    state: ScrapbookUiState,
    actions: ScrapbookActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    var confirm by rememberSaveable { mutableStateOf<Confirm?>(null) }
    val writing = state.writing

    fun close() {
        if (state.draft.dirty) confirm = Confirm.Leave else onClose()
    }
    BackHandler(enabled = writing != null || confirm != null || state.draft.dirty) {
        when {
            writing != null -> actions.doneWriting()
            confirm != null -> confirm = null
            else -> confirm = Confirm.Leave
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(c.paper)
            .systemBarsPadding()
            .imePadding()
            .padding(vertical = s.s),
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        Header(state, onClose = ::close, onDelete = { confirm = Confirm.Delete }, onSave = actions.save)

        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = s.screenFullBleed),
            contentAlignment = Alignment.Center,
        ) {
            if (state.loaded) {
                val w = min(maxWidth, maxHeight * ScrapRules.Aspect)
                EditorPage(state, actions, w)
            }
        }

        val selected = state.selected
        when {
            confirm != null -> ConfirmBar(
                question = if (confirm == Confirm.Leave) R.string.scrap_leave_question else R.string.scrap_delete_question,
                yes = if (confirm == Confirm.Leave) R.string.scrap_leave else R.string.scrap_delete,
                no = if (confirm == Confirm.Leave) R.string.scrap_keep_editing else R.string.scrap_keep_page,
                onYes = {
                    val what = confirm
                    confirm = null
                    if (what == Confirm.Leave) onClose() else actions.delete()
                },
                onNo = { confirm = null },
            )
            writing != null -> WriteBar(writing, onText = { actions.setText(writing.id, it) }, onDone = actions.doneWriting)
            else -> {
                ToolSlot(state, selected, actions)
                Tray(state, actions)
            }
        }
    }
}

@Composable
private fun Header(state: ScrapbookUiState, onClose: () -> Unit, onDelete: () -> Unit, onSave: () -> Unit) {
    val s = StampTheme.spacing
    val locale = LocalConfiguration.current.locales[0]
    Row(
        Modifier.fillMaxWidth().padding(horizontal = s.screenFullBleed),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.xs),
    ) {
        CircleIconButton(R.drawable.ic_close, stringResource(R.string.scrap_close_cd), onClose)
        Text(
            stringResource(if (state.isNew) R.string.scrap_title_new else R.string.scrap_title_edit).uppercase(locale),
            style = StampTheme.type.overline,
            color = StampTheme.colors.textMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (!state.isNew) CircleIconButton(R.drawable.ic_delete, stringResource(R.string.scrap_delete_cd), onDelete)
        CircleIconButton(R.drawable.ic_check, stringResource(R.string.scrap_save_cd), onSave, enabled = state.loaded)
    }
}

/**
 * The page being made, drawn at [width]. One finger on a piece drags it; two fingers pinch and
 * twist it (or the selected piece, if they land beside it); a tap on the paper lets go.
 */
@Composable
private fun EditorPage(state: ScrapbookUiState, actions: ScrapbookActions, width: Dp) {
    val c = StampTheme.colors
    val shape = StampTheme.shapes.card
    val sizes = remember { HashMap<String, IntSize>() }
    val items by rememberUpdatedState(state.draft.items)
    val selectedId by rememberUpdatedState(state.draft.selectedId)
    val currentActions by rememberUpdatedState(actions)
    val haptics = LocalHapticFeedback.current
    val editing = remember(state.draft.selectedId) { ScrapEditing(state.draft.selectedId) { id, size -> sizes[id] = size } }

    GrainedSurface(c.kraft, Modifier.tintedShadow(Elevation.Card, shape, c.shadowWarm), shape) {
        ScaledArt(
            width,
            ScrapRules.Aspect,
            ScrapDesignWidth,
            Modifier.pointerInput(Unit) {
                val slop = HitSlop.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pageW = size.width.toFloat()
                    val pageH = size.height.toFloat()
                    val hit = hitTest(down.position, items, sizes, pageW, pageH, slop)
                    if (hit != null && hit != selectedId) {
                        currentActions.select(hit)
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed == 0) break
                        val target = hit ?: selectedId.takeIf { pressed >= 2 }
                        if (target != null) {
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            val rotation = event.calculateRotation()
                            if (pan != Offset.Zero || zoom != 1f || rotation != 0f) {
                                moved = true
                                currentActions.transform(target, pan.x / pageW, pan.y / pageH, zoom, rotation)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (event.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }) {
                            moved = true
                        }
                    }
                    if (hit == null && !moved) currentActions.select(null)
                }
            },
        ) {
            ScrapPageArt(state.draft.items, state.stamps, editing = editing)
        }
    }
}

/** The top-most piece under [point], using each piece's measured size, scale and turn. */
private fun hitTest(point: Offset, items: List<ScrapItem>, sizes: Map<String, IntSize>, pageW: Float, pageH: Float, slop: Float): String? {
    for (item in items.asReversed()) {
        val size = sizes[item.id] ?: continue
        val dx = point.x - item.x * pageW
        val dy = point.y - item.y * pageH
        val a = Math.toRadians(-item.rotation.toDouble())
        val lx = ((dx * cos(a) - dy * sin(a)) / item.scale).toFloat()
        val ly = ((dx * sin(a) + dy * cos(a)) / item.scale).toFloat()
        val pad = slop / item.scale
        if (abs(lx) <= size.width / 2f + pad && abs(ly) <= size.height / 2f + pad) return item.id
    }
    return null
}

/** Under the page: what to do with the selected piece, or how the page works. */
@Composable
private fun ToolSlot(state: ScrapbookUiState, selected: ScrapItem?, actions: ScrapbookActions) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    Box(Modifier.fillMaxWidth().height(s.touchTarget).padding(horizontal = s.screenFullBleed), contentAlignment = Alignment.Center) {
        if (selected != null) {
            Row(
                Modifier.background(c.ink, StampTheme.shapes.pill).padding(horizontal = s.xxs),
                horizontalArrangement = Arrangement.spacedBy(s.xxs),
            ) {
                val writing = selected.kind == ScrapKind.Note || selected.kind == ScrapKind.Title
                if (writing) Tool(R.drawable.ic_edit, R.string.scrap_tool_write) { actions.write(selected.id) }
                Tool(R.drawable.ic_minus, R.string.scrap_tool_smaller, enabled = selected.scale > ScrapRules.MinScale) {
                    actions.resize(selected.id, 1f / ResizeStep)
                }
                Tool(R.drawable.ic_plus, R.string.scrap_tool_bigger, enabled = selected.scale < ScrapRules.MaxScale) {
                    actions.resize(selected.id, ResizeStep)
                }
                Tool(R.drawable.ic_rotate, R.string.scrap_tool_turn) { actions.turn(selected.id) }
                Tool(R.drawable.ic_to_front, R.string.scrap_tool_front, enabled = state.draft.items.lastOrNull()?.id != selected.id) {
                    actions.toFront(selected.id)
                }
                Tool(R.drawable.ic_delete, R.string.scrap_tool_remove) { actions.remove(selected.id) }
            }
        } else {
            val (text, color) = when {
                state.full -> R.string.scrap_hint_full to c.airmailRed
                !state.isPro -> R.string.scrap_hint_pro to c.airmailRed
                else -> R.string.scrap_hint to Color.Unspecified
            }
            HandNote(stringResource(text), style = StampTheme.type.noteSmall, strength = InkStrength.Soft, color = color, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Tool(@DrawableRes icon: Int, label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    val c = StampTheme.colors
    val description = stringResource(label)
    Box(
        Modifier
            .size(StampTheme.spacing.touchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (enabled) c.textOnDarkHigh else c.textOnDarkLow,
            modifier = Modifier.size(ToolIcon),
        )
    }
}

/** Tabs + the pieces to add. Layouts only while the page is new. */
@Composable
private fun Tray(state: ScrapbookUiState, actions: ScrapbookActions) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    val tabs = if (state.isNew) TrayTab.entries else TrayTab.entries - TrayTab.Layouts
    var picked by rememberSaveable { mutableStateOf<TrayTab?>(null) }
    // Until something is picked: Layouts for a new page, Stamps for a saved one (it may still be loading).
    val tab = picked?.takeIf { it in tabs } ?: tabs.first()
    Column(
        Modifier
            .padding(horizontal = s.screenFullBleed)
            .fillMaxWidth()
            .background(c.paperWhite, StampTheme.shapes.card)
            .border(1.dp, c.outline, StampTheme.shapes.card)
            .padding(vertical = s.s),
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        Row(Modifier.padding(horizontal = s.s), horizontalArrangement = Arrangement.spacedBy(s.xs)) {
            tabs.forEach { t -> PaperChip(stringResource(t.label), selected = t == tab, onClick = { picked = t }) }
        }
        Box(Modifier.fillMaxWidth().height(TrayHeight), contentAlignment = Alignment.CenterStart) {
            when (tab) {
                TrayTab.Layouts -> LayoutRow(state.draft.layout, actions.applyLayout)
                TrayTab.Stamps -> StampRow(state.albumStamps, enabled = !state.full, onAdd = actions.addStamp)
                TrayTab.Stickers -> StickerRow(enabled = !state.full, onAdd = actions.addSticker)
                TrayTab.Write -> Row(Modifier.padding(horizontal = s.s), horizontalArrangement = Arrangement.spacedBy(s.s)) {
                    PillButton(stringResource(R.string.scrap_write_note), { actions.addWriting(ScrapKind.Note) }, style = PillStyle.Outline, enabled = !state.full)
                    PillButton(stringResource(R.string.scrap_write_title), { actions.addWriting(ScrapKind.Title) }, style = PillStyle.Outline, enabled = !state.full)
                }
            }
        }
    }
}

@Composable
private fun LayoutRow(current: ScrapLayout?, onPick: (ScrapLayout) -> Unit) {
    val s = StampTheme.spacing
    Row(Modifier.fillMaxWidth().padding(horizontal = s.s), horizontalArrangement = Arrangement.SpaceBetween) {
        ScrapLayout.entries.forEach { layout -> LayoutThumb(layout, layout == current) { onPick(layout) } }
    }
}

@Composable
private fun LayoutThumb(layout: ScrapLayout, selected: Boolean, onClick: () -> Unit) {
    val c = StampTheme.colors
    val shape = StampTheme.shapes.plate
    Column(
        Modifier.clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(ThumbW, ThumbH)
                .border(if (selected) 2.dp else 1.dp, if (selected) c.airmailRed else c.outline, shape)
                .padding(3.dp)
                .background(c.kraft, shape)
                .padding(5.dp),
        ) { LayoutSketch(layout) }
        HandNote(
            stringResource(layout.label),
            style = StampTheme.type.noteSmall,
            color = if (selected) c.airmailRed else Color.Unspecified,
        )
    }
}

/** A tiny sketch of a layout: white blocks for stamps, lines for writing. */
@Composable
private fun BoxScope.LayoutSketch(layout: ScrapLayout) {
    val gap = 3.dp
    when (layout) {
        ScrapLayout.Diary -> Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            SketchLine(28.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                SketchBlock(22.dp, 26.dp)
                Column(verticalArrangement = Arrangement.spacedBy(gap)) { SketchBlock(14.dp, 11.dp); SketchBlock(14.dp, 11.dp) }
            }
            SketchLine(32.dp)
        }
        ScrapLayout.Collage -> Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(3) { Row(horizontalArrangement = Arrangement.spacedBy(gap)) { SketchBlock(18.dp, 13.dp); SketchBlock(18.dp, 13.dp) } }
        }
        ScrapLayout.Postcard -> Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            SketchBlock(39.dp, 24.dp)
            repeat(2) { SketchLine(24.dp) }
        }
        ScrapLayout.BigOne -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
            SketchBlock(30.dp, 38.dp)
            SketchLine(24.dp)
        }
    }
}

@Composable
private fun SketchBlock(width: Dp, height: Dp) {
    Box(Modifier.size(width, height).background(StampTheme.colors.paperWhite))
}

@Composable
private fun SketchLine(width: Dp) {
    Box(Modifier.width(width).height(1.dp).background(StampTheme.colors.textLow))
}

@Composable
private fun StampRow(stamps: List<Stamp>, enabled: Boolean, onAdd: (Stamp) -> Unit) {
    val s = StampTheme.spacing
    if (stamps.isEmpty()) {
        HandNote(stringResource(R.string.scrap_no_stamps), style = StampTheme.type.noteSmall, strength = InkStrength.Soft, modifier = Modifier.padding(horizontal = s.s))
        return
    }
    val add = stringResource(R.string.scrap_add_cd)
    LazyRow(
        contentPadding = PaddingValues(horizontal = s.s),
        horizontalArrangement = Arrangement.spacedBy(s.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(stamps, key = { it.id }, contentType = { "stamp" }) { stamp ->
            val description = stamp.description()
            Box(
                Modifier
                    .height(TrayHeight)
                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = add) { onAdd(stamp) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                RenderedStamp(File(stamp.imagePath), stamp.shape, null, Modifier.height(TrayStamp), perforation = stamp.perforation, elevation = Elevation.Card)
            }
        }
    }
}

@Composable
private fun StickerRow(enabled: Boolean, onAdd: (ScrapSticker) -> Unit) {
    val s = StampTheme.spacing
    val add = stringResource(R.string.scrap_add_cd)
    LazyRow(
        contentPadding = PaddingValues(horizontal = s.s),
        horizontalArrangement = Arrangement.spacedBy(s.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(ScrapSticker.entries, key = { it.name }, contentType = { "sticker" }) { sticker ->
            val description = stringResource(sticker.label)
            Box(
                Modifier
                    .size(TrayHeight)
                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = add) { onAdd(sticker) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                // Drawn at full size and shrunk, so the tray shows the real sticker.
                Box(Modifier.graphicsLayer { scaleX = TraySticker; scaleY = TraySticker }.wrapUnbounded()) {
                    ScrapStickerArt(sticker, sampleText(sticker))
                }
            }
        }
    }
}

@Composable
private fun sampleText(sticker: ScrapSticker): String = when (sticker) {
    ScrapSticker.WaxSeal -> stringResource(R.string.scrap_sample_seal)
    ScrapSticker.Ticket -> stringResource(R.string.scrap_sample_ticket)
    ScrapSticker.Postmark -> stringResource(R.string.scrap_sample_postmark)
    else -> ""
}

/** Lets a sticker lay out wider than its tray cell (it is shrunk to fit by the layer above). */
private fun Modifier.wrapUnbounded() = this.then(
    Modifier.layout { measurable, constraints ->
        val p = measurable.measure(androidx.compose.ui.unit.Constraints())
        layout(constraints.maxWidth, constraints.maxHeight) {
            p.place((constraints.maxWidth - p.width) / 2, (constraints.maxHeight - p.height) / 2)
        }
    },
)

/** Typing into a note or title: the words appear on the page as they're typed. */
@Composable
private fun WriteBar(item: ScrapItem, onText: (String) -> Unit, onDone: () -> Unit) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(item.id) { focus.requestFocus() }
    val title = item.kind == ScrapKind.Title
    val hint = stringResource(if (title) R.string.scrap_title_placeholder else R.string.scrap_note_placeholder)
    Row(
        Modifier
            .padding(horizontal = s.screenFullBleed)
            .fillMaxWidth()
            .background(c.paperWhite, StampTheme.shapes.card)
            .border(1.dp, c.outline, StampTheme.shapes.card)
            .padding(start = s.m, end = s.xs, top = s.xs, bottom = s.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.xs),
    ) {
        val style = (if (title) StampTheme.type.albumName else StampTheme.type.note).copy(color = c.textHigh)
        BasicTextField(
            value = item.text,
            onValueChange = onText,
            textStyle = style,
            cursorBrush = SolidColor(c.airmailRed),
            maxLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = hint },
            decorationBox = { inner ->
                Box {
                    if (item.text.isEmpty()) Text(hint, style = style.copy(color = c.textLow))
                    inner()
                }
            },
        )
        CircleIconButton(R.drawable.ic_check, stringResource(R.string.scrap_write_done_cd), onDone)
    }
}

@Composable
private fun ConfirmBar(question: Int, yes: Int, no: Int, onYes: () -> Unit, onNo: () -> Unit) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    Column(
        Modifier
            .padding(horizontal = s.screenFullBleed)
            .fillMaxWidth()
            .background(c.paperWhite, StampTheme.shapes.card)
            .border(1.dp, c.outline, StampTheme.shapes.card)
            .padding(s.m),
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        HandNote(stringResource(question), style = StampTheme.type.note)
        Row(horizontalArrangement = Arrangement.spacedBy(s.s)) {
            PillButton(stringResource(no), onNo, style = PillStyle.Outline, modifier = Modifier.weight(1f))
            PillButton(stringResource(yes), onYes, style = PillStyle.Red, modifier = Modifier.weight(1f))
        }
    }
}

private const val ResizeStep = 1.15f
private val HitSlop = 8.dp
private val ToolIcon = 20.dp
private val TrayHeight = 96.dp
private val TrayStamp = 76.dp
private const val TraySticker = 0.75f
private val ThumbW = 50.dp
private val ThumbH = 62.dp

@Preview(showBackground = true, backgroundColor = 0xFFF6F0E6, heightDp = 860)
@Composable
private fun ScrapbookPreview() {
    val stamps = previewStamps(5)
    val items = ScrapLayout.Diary.build(ScrapLayoutInput(stamps, "Japan trip", "KYOTO\n05 NOV 26 · JP", "J", "05 NOV"))
    StampAppTheme {
        ScrapbookContent(
            state = ScrapbookUiState(
                loaded = true,
                album = Album(1, "Japan trip", AlbumCategory.Trips, AlbumCoverColor.Ink, Instant.EPOCH),
                albumStamps = stamps,
                stamps = stamps.associateBy { it.id },
                draft = ScrapDraft(items = items, selectedId = items[1].id, layout = ScrapLayout.Diary),
            ),
            actions = ScrapbookActions(),
            onClose = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F0E6, heightDp = 860)
@Composable
private fun ScrapbookFreePreview() {
    val stamps = previewStamps(1)
    StampAppTheme {
        ScrapbookContent(
            state = ScrapbookUiState(
                loaded = true,
                albumStamps = stamps,
                stamps = stamps.associateBy { it.id },
                draft = ScrapDraft(
                    items = ScrapLayout.BigOne.build(ScrapLayoutInput(stamps, "Everyday", "05 NOV\n2026", "E", "05 NOV")),
                    layout = ScrapLayout.BigOne,
                ),
            ),
            actions = ScrapbookActions(),
            onClose = {},
        )
    }
}

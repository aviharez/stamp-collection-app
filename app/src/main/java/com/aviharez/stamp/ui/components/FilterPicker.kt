package com.aviharez.stamp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.R
import com.aviharez.stamp.data.billing.LookAccess
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.StampFilter
import com.aviharez.stamp.domain.model.StampLook
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.airmailSpring
import com.aviharez.stamp.ui.theme.rememberReduceMotion

/**
 * Lets a row reach [horizontal] past each side of its padded parent (the picker scrolls edge to
 * edge while the screen keeps its 24dp padding).
 */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = (horizontal * 2).roundToPx()
    val max = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth, maxWidth = max))
    val width = (placeable.width - extra).coerceAtLeast(0)
    layout(width, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** Compose colour filter for this stamp filter (null = untouched). */
fun StampFilter.composeColorFilter(): ColorFilter? = colorMatrix()?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }

/** Name shown under the filter's mini stamp. */
val StampFilter.label: Int
    get() = when (this) {
        StampFilter.Original -> R.string.filter_original
        StampFilter.Postcard -> R.string.filter_postcard
        StampFilter.Engraved -> R.string.filter_engraved
        StampFilter.Sepia -> R.string.filter_sepia
        StampFilter.Vivid -> R.string.filter_vivid
        StampFilter.Faded -> R.string.filter_faded
        StampFilter.RedInk -> R.string.filter_red_ink
        StampFilter.BlueInk -> R.string.filter_blue_ink
        StampFilter.GreenInk -> R.string.filter_green_ink
    }

/** Name shown under the edge's mini stamp. */
val PerforationStyle.label: Int
    get() = when (this) {
        PerforationStyle.Classic -> R.string.edge_classic
        PerforationStyle.Fine -> R.string.edge_fine
        PerforationStyle.Bold -> R.string.edge_bold
        PerforationStyle.Scallop -> R.string.edge_scallop
    }

/** Which row the look picker shows. */
enum class LookTab { Filters, Edges }

/**
 * Everything about a stamp's look in one place: "Filters · Edges" tabs over a row of mini stamps.
 * The edge row shows the photo in the picked filter, so each mini stamp is the whole look.
 * Locked choices carry a "PRO" tag (an edge unlocked for a day by an ad says "24H"); tapping one
 * still calls back (the caller tries it on).
 *
 * @param working the picked look is still being drawn: its mini stamp shows a turning postmark.
 * @param onDark labels in PaperWhite (Stamped!'s cobalt).
 */
@Composable
fun LookPicker(
    photo: ImageBitmap?,
    selected: StampLook,
    access: LookAccess,
    onPickFilter: (StampFilter) -> Unit,
    onPickEdge: (PerforationStyle) -> Unit,
    modifier: Modifier = Modifier,
    working: Boolean = false,
    onDark: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = StampTheme.spacing.screen),
) {
    var tab by rememberSaveable { mutableStateOf(LookTab.Filters) }
    Column(modifier) {
        LookTabs(tab, onPick = { tab = it }, onDark = onDark, modifier = Modifier.padding(contentPadding))
        when (tab) {
            LookTab.Filters -> FilterPicker(photo, selected.filter, access.isPro, onPickFilter, working = working, onDark = onDark, contentPadding = contentPadding)
            LookTab.Edges -> EdgePicker(photo, selected, access, onPickEdge, working = working, onDark = onDark, contentPadding = contentPadding)
        }
    }
}

/** Two words, the picked one in red ink and underlined like a hand-made index tab. */
@Composable
private fun LookTabs(tab: LookTab, onPick: (LookTab) -> Unit, onDark: Boolean, modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    val haptics = LocalHapticFeedback.current
    val locale = LocalConfiguration.current.locales[0]
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.m)) {
        LookTab.entries.forEach { t ->
            val picked = t == tab
            val ink = when {
                picked && onDark -> c.textOnDarkHigh
                picked -> c.airmailRed
                onDark -> c.textOnDarkMedium
                else -> c.textMedium
            }
            Box(
                Modifier
                    .heightIn(min = StampTheme.spacing.touchTargetMin)
                    .selectable(selected = picked, role = Role.Tab) {
                        if (!picked) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onPick(t)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(t.label).lowercase(locale),
                    style = StampTheme.type.noteLarge,
                    color = ink,
                    modifier = Modifier.drawBehind {
                        if (picked) {
                            val y = size.height - TabUnderline.toPx()
                            drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = TabUnderline.toPx(), cap = StrokeCap.Round)
                        }
                    },
                )
            }
        }
    }
}

private val LookTab.label: Int
    get() = when (this) {
        LookTab.Filters -> R.string.look_tab_filters
        LookTab.Edges -> R.string.look_tab_edges
    }

/**
 * A row of mini stamps, one per [StampFilter], each showing [photo] in that filter (a small,
 * square-cropped copy of the picture). The picked one lifts and tilts like the nav bar's active
 * stamp. Pro filters carry a small "PRO" tag while [isPro] is false; tapping one still calls
 * [onPick] (the caller decides: preview it, or open the paywall).
 *
 * @param onDark labels in PaperWhite (Stamped!'s cobalt, the Ink framing screen).
 */
@Composable
fun FilterPicker(
    photo: ImageBitmap?,
    selected: StampFilter,
    isPro: Boolean,
    onPick: (StampFilter) -> Unit,
    modifier: Modifier = Modifier,
    working: Boolean = false,
    onDark: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = StampTheme.spacing.screen),
) {
    MiniStampRow(modifier, contentPadding) {
        StampFilter.entries.forEachIndexed { index, filter ->
            val name = stringResource(filter.label)
            val locked = !isPro && !filter.free
            LookStamp(
                photo = photo,
                colorFilter = remember(filter) { filter.composeColorFilter() },
                perforation = PerforationStyle.Fine,
                name = name,
                description = if (locked) stringResource(R.string.filter_pro_cd, name) else name,
                tag = if (locked) stringResource(R.string.filter_pro_tag) else null,
                selected = filter == selected,
                working = working && filter == selected,
                restTilt = if (index % 2 == 0) RestTilt else -RestTilt,
                onDark = onDark,
                onClick = { onPick(filter) },
            )
        }
    }
}

/** A row of mini stamps, one per [PerforationStyle], each the photo in [selected]'s filter with that edge. */
@Composable
fun EdgePicker(
    photo: ImageBitmap?,
    selected: StampLook,
    access: LookAccess,
    onPick: (PerforationStyle) -> Unit,
    modifier: Modifier = Modifier,
    working: Boolean = false,
    onDark: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = StampTheme.spacing.screen),
) {
    val colorFilter = remember(selected.filter) { selected.filter.composeColorFilter() }
    MiniStampRow(modifier, contentPadding) {
        PerforationStyle.entries.forEachIndexed { index, edge ->
            val name = stringResource(edge.label)
            val (tag, description) = when {
                edge.free || access.isPro -> null to name
                edge in access.trialEdges -> stringResource(R.string.edge_trial_tag) to stringResource(R.string.edge_trial_cd, name)
                else -> stringResource(R.string.filter_pro_tag) to stringResource(R.string.edge_pro_cd, name)
            }
            LookStamp(
                photo = photo,
                colorFilter = colorFilter,
                perforation = edge,
                name = name,
                description = description,
                tag = tag,
                trialTag = edge in access.trialEdges && !access.isPro,
                selected = edge == selected.edge,
                working = working && edge == selected.edge,
                restTilt = if (index % 2 == 0) RestTilt else -RestTilt,
                onDark = onDark,
                onClick = { onPick(edge) },
            )
        }
    }
}

@Composable
private fun MiniStampRow(modifier: Modifier, contentPadding: PaddingValues, content: @Composable () -> Unit) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(vertical = StampTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.s),
    ) { content() }
}

@Composable
private fun LookStamp(
    photo: ImageBitmap?,
    colorFilter: ColorFilter?,
    perforation: PerforationStyle,
    name: String,
    description: String,
    tag: String?,
    selected: Boolean,
    restTilt: Float,
    onDark: Boolean,
    onClick: () -> Unit,
    trialTag: Boolean = false,
    working: Boolean = false,
) {
    val c = StampTheme.colors
    val reduceMotion = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val tilt by animateFloatAsState(if (selected) PickedTilt else restTilt, airmailSpring(reduceMotion), label = "tilt")
    val lift by animateFloatAsState(
        when {
            pressed -> PressedScale
            selected -> PickedScale
            else -> 1f
        },
        airmailSpring(reduceMotion),
        label = "lift",
    )
    val locale = LocalConfiguration.current.locales[0]
    val workingNote = stringResource(R.string.look_working)
    // Quick looks finish before the ring would show, so it never just flickers.
    var showWorking by remember { mutableStateOf(false) }
    LaunchedEffect(working) {
        if (working) delay(WorkingShowAfterMillis)
        showWorking = working
    }

    Column(
        Modifier
            .widthIn(min = StampTheme.spacing.touchTarget)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = null, // the stamp lifts / squashes instead of a ripple
                role = Role.RadioButton,
                onClick = {
                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                },
            )
            .semantics {
                contentDescription = description
                if (working) stateDescription = workingNote
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.graphicsLayer {
                rotationZ = tilt
                scaleX = lift
                scaleY = lift
            },
        ) {
            Stamp(
                StampShape.Square,
                Modifier.size(StampSize),
                perforation = perforation,
                elevation = if (selected) Elevation.Card else Elevation.Low,
                shadowTint = if (onDark) c.shadowNavy else c.shadowWarm,
            ) {
                if (photo != null) {
                    Image(
                        photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = colorFilter,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    Box(Modifier.matchParentSize().background(c.surface2))
                }
                val workingAlpha by animateFloatAsState(if (showWorking) 1f else 0f, tween(WorkingFadeMillis), label = "working")
                if (workingAlpha > 0f) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = workingAlpha }
                            .background(c.ink.copy(alpha = WorkingScrimAlpha)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PostmarkSpinner(
                            ring = c.textOnDarkHigh,
                            inner = c.textOnDarkMedium,
                            modifier = Modifier.size(WorkingRingSize),
                            outerStroke = WorkingStroke,
                            dash = WorkingDash,
                        )
                    }
                }
            }
            if (tag != null) {
                Text(
                    tag,
                    style = StampTheme.type.overline,
                    color = c.textOnDarkHigh,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = TagOffset, y = -TagOffset)
                        .background(if (trialTag) c.ink else c.airmailRed, StampTheme.shapes.plate)
                        .padding(horizontal = StampTheme.spacing.xxs),
                )
            }
        }
        Text(
            name.lowercase(locale),
            style = StampTheme.type.noteSmall,
            color = when {
                selected && onDark -> c.textOnDarkHigh
                selected -> c.airmailRed
                onDark -> c.textOnDarkMedium
                else -> c.handInkSoft
            },
            maxLines = 1,
            modifier = Modifier.padding(top = StampTheme.spacing.xxs),
        )
    }
}

private val StampSize = 64.dp
private val TagOffset = 6.dp
private const val RestTilt = 1.5f
private const val PickedTilt = -7f
private const val PickedScale = 1.12f
private const val PressedScale = 0.9f
private val TabUnderline = 2.dp
private const val WorkingShowAfterMillis = 120L
private const val WorkingFadeMillis = 150
private const val WorkingScrimAlpha = 0.45f
private val WorkingRingSize = 30.dp
private val WorkingStroke = 2.dp
private val WorkingDash = 4.dp

@PaperPreview
@Composable
private fun FilterPickerPreview() {
    StampAppTheme {
        Column {
            FilterPicker(photo = null, selected = StampFilter.Postcard, isPro = false, onPick = {})
            FilterPicker(photo = null, selected = StampFilter.Original, isPro = true, onPick = {})
            FilterPicker(photo = null, selected = StampFilter.Sepia, isPro = true, onPick = {}, working = true)
        }
    }
}

@PaperPreview
@Composable
private fun EdgePickerPreview() {
    StampAppTheme {
        Column {
            LookPicker(
                photo = null,
                selected = StampLook(edge = PerforationStyle.Bold),
                access = LookAccess(trialEdges = setOf(PerforationStyle.Bold)),
                onPickFilter = {},
                onPickEdge = {},
            )
            EdgePicker(photo = null, selected = StampLook(), access = LookAccess(isPro = true), onPick = {})
        }
    }
}

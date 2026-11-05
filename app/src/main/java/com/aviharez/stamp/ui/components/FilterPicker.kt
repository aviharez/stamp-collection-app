package com.aviharez.stamp.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.StampFilter
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
    onDark: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = StampTheme.spacing.screen),
) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(vertical = StampTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.s),
    ) {
        StampFilter.entries.forEachIndexed { index, filter ->
            FilterStamp(
                photo = photo,
                filter = filter,
                selected = filter == selected,
                locked = !isPro && !filter.free,
                restTilt = if (index % 2 == 0) RestTilt else -RestTilt,
                onDark = onDark,
                onClick = { onPick(filter) },
            )
        }
    }
}

@Composable
private fun FilterStamp(
    photo: ImageBitmap?,
    filter: StampFilter,
    selected: Boolean,
    locked: Boolean,
    restTilt: Float,
    onDark: Boolean,
    onClick: () -> Unit,
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
    val name = stringResource(filter.label)
    val description = if (locked) stringResource(R.string.filter_pro_cd, name) else name
    val colorFilter = remember(filter) { filter.composeColorFilter() }
    val locale = LocalConfiguration.current.locales[0]

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
            .semantics { contentDescription = description },
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
                perforation = PerforationStyle.Fine,
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
            }
            if (locked) {
                Text(
                    stringResource(R.string.filter_pro_tag),
                    style = StampTheme.type.overline,
                    color = c.textOnDarkHigh,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = TagOffset, y = -TagOffset)
                        .background(c.airmailRed, StampTheme.shapes.plate)
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

@PaperPreview
@Composable
private fun FilterPickerPreview() {
    StampAppTheme {
        Column {
            FilterPicker(photo = null, selected = StampFilter.Postcard, isPro = false, onPick = {})
            FilterPicker(photo = null, selected = StampFilter.Original, isPro = true, onPick = {})
        }
    }
}

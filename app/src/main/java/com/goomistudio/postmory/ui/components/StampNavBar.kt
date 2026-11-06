package com.goomistudio.postmory.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.goomistudio.postmory.R
import com.goomistudio.postmory.domain.model.PerforationStyle
import com.goomistudio.postmory.domain.model.StampShape
import com.goomistudio.postmory.ui.theme.Elevation
import com.goomistudio.postmory.ui.theme.PaperPreview
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme
import com.goomistudio.postmory.ui.theme.airmailSpring
import com.goomistudio.postmory.ui.theme.rememberReduceMotion
import com.goomistudio.postmory.ui.theme.tintedShadow

/** Top-level destinations in the nav bar. */
enum class NavTab(@param:DrawableRes val icon: Int, @param:StringRes val label: Int) {
    Album(R.drawable.ic_nav_album, R.string.nav_album),
    Calendar(R.drawable.ic_nav_calendar, R.string.nav_calendar),
    Map(R.drawable.ic_nav_map, R.string.nav_map),
    Profile(R.drawable.ic_nav_profile, R.string.nav_profile),
}

/**
 * "Stamp sheet" nav bar: a grained kraft strip holding four mini perforated stamps with
 * handwritten labels. The active stamp turns AirmailRed, tilts and lifts as if picked off the
 * sheet; every tab squashes on press and ticks a haptic.
 *
 * Place it in the bottom third with 16dp side padding (full-bleed card) and system-bar insets.
 */
@Composable
fun StampNavBar(
    selected: NavTab,
    onSelect: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StampTheme.colors
    val shape = StampTheme.shapes.card
    Box(modifier.tintedShadow(Elevation.Raised, shape, c.shadowWarm)) {
        GrainedSurface(c.kraft, Modifier.fillMaxWidth(), shape = shape) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = StampTheme.spacing.s)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                NavTab.entries.forEachIndexed { index, tab ->
                    NavStamp(
                        tab = tab,
                        selected = tab == selected,
                        restTilt = if (index % 2 == 0) RestTilt else -RestTilt,
                        onClick = { onSelect(tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavStamp(tab: NavTab, selected: Boolean, restTilt: Float, onClick: () -> Unit) {
    val c = StampTheme.colors
    val reduceMotion = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val stampShape = remember { PerforatedShape(StampShape.Square, PerforationStyle.Fine) }

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
    val fill by animateColorAsState(
        if (selected) c.airmailRed else c.paperWhite,
        if (reduceMotion) tween(0) else tween(ColorMillis),
        label = "fill",
    )
    val locale = LocalConfiguration.current.locales[0]
    val label = stringResource(tab.label)

    Column(
        Modifier
            .widthIn(min = TabMinWidth)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = null, // the stamp itself reacts (squash/lift) instead of a ripple
                role = Role.Tab,
                onClick = {
                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(StampSize)
                .graphicsLayer {
                    rotationZ = tilt
                    scaleX = lift
                    scaleY = lift
                },
            contentAlignment = Alignment.Center,
        ) {
            // Convex shadow caster under the perforated paper (see Stamp.kt for why).
            Box(
                Modifier
                    .size(StampSize - StampTheme.spacing.xs)
                    .tintedShadow(if (selected) Elevation.Card else Elevation.Low, StampTheme.shapes.plate, c.shadowWarm)
                    .background(fill, StampTheme.shapes.plate),
            )
            Box(
                Modifier
                    .size(StampSize)
                    .background(fill, stampShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(tab.icon),
                    contentDescription = null, // the label below names the tab
                    tint = if (selected) c.textOnDarkHigh else c.ink,
                    modifier = Modifier.size(IconSize),
                )
            }
        }
        Text(
            text = label.lowercase(locale),
            style = StampTheme.type.noteSmall,
            color = if (selected) c.handInk else c.handInkSoft,
            maxLines = 1,
            modifier = Modifier.padding(top = StampTheme.spacing.xxs),
        )
    }
}

private const val RestTilt = 1.5f
private const val PickedTilt = -7f
private const val PickedScale = 1.12f
private const val PressedScale = 0.9f
private const val ColorMillis = 180
private val StampSize = 46.dp
private val IconSize = 22.dp
private val TabMinWidth = 72.dp

@PaperPreview
@Composable
private fun StampNavBarPreview() {
    StampAppTheme {
        var tab by remember { mutableStateOf(NavTab.Album) }
        Box(Modifier.padding(StampTheme.spacing.screenFullBleed)) {
            StampNavBar(selected = tab, onSelect = { tab = it }, modifier = Modifier.fillMaxWidth())
        }
    }
}

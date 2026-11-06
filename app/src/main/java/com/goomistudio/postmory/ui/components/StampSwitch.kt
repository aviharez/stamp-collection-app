package com.goomistudio.postmory.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.goomistudio.postmory.ui.theme.PaperPreview
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme
import com.goomistudio.postmory.ui.theme.airmailSpring
import com.goomistudio.postmory.ui.theme.rememberReduceMotion

/**
 * A settings line with an Airmail switch: the whole row is the target (≥ 48dp, Role.Switch).
 * The switch is an Ink pill with a postmark-red knob when on, a PaperWhite knob on Surface2 when off.
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val scale by pressScale(source)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = StampTheme.spacing.touchTarget)
            .toggleable(
                value = checked,
                interactionSource = source,
                indication = null,
                role = Role.Switch,
                onValueChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onCheckedChange(it)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.s),
    ) {
        Text(label, style = StampTheme.type.body, color = StampTheme.colors.textHigh, modifier = Modifier.weight(1f))
        StampSwitch(checked, Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
    }
}

/** The switch alone (drawn only; [SwitchRow] handles the tap). */
@Composable
fun StampSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    val reduceMotion = rememberReduceMotion()
    val travel by animateFloatAsState(if (checked) 1f else 0f, airmailSpring(reduceMotion), label = "knob")
    val track by animateColorAsState(if (checked) c.ink else c.surface2, label = "track")
    val knob by animateColorAsState(if (checked) c.airmailRed else c.paperWhite, label = "knob colour")
    Box(
        modifier
            .size(TrackWidth, TrackHeight)
            .background(track, CircleShape)
            .padding(KnobInset),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset { IntOffset(((TrackWidth - TrackHeight).toPx() * travel).toInt(), 0) }
                .size(TrackHeight - KnobInset * 2)
                .background(knob, CircleShape),
        )
    }
}

private val TrackWidth = 48.dp
private val TrackHeight = 28.dp
private val KnobInset = 4.dp

@PaperPreview
@Composable
private fun SwitchRowPreview() {
    StampAppTheme {
        Column(Modifier.padding(StampTheme.spacing.m)) {
            SwitchRow("Mirror selfies", checked = true, onCheckedChange = {})
            SwitchRow("Place on postmarks", checked = false, onCheckedChange = {})
        }
    }
}

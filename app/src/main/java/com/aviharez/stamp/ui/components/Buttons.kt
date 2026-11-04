package com.aviharez.stamp.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.R
import com.aviharez.stamp.ui.theme.AirmailColors
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.airmailSpring
import com.aviharez.stamp.ui.theme.rememberReduceMotion
import com.aviharez.stamp.ui.theme.tintedShadow

/** Visual variants of [PillButton]. */
enum class PillStyle {
    /** AirmailRed — the one primary action on a light screen ("Stamp it"). */
    Red,

    /** Ink — strong secondary on Paper ("Share this page"). */
    Ink,

    /** PaperWhite on dark/cobalt — primary on dark screens ("Stick in album"). */
    White,

    /** Ink outline on Paper ("Map"). */
    Outline,

    /** PaperWhite outline on dark/cobalt ("Share"). */
    OutlineOnDark,

    /** Translucent PaperWhite plate on dark/cobalt ("Retake", "Edit"). */
    Plate,
}

private data class PillColors(val container: Color, val content: Color, val border: Color?, val shadow: Color?)

private fun AirmailColors.pill(style: PillStyle) = when (style) {
    PillStyle.Red -> PillColors(airmailRed, textOnDarkHigh, null, airmailRed)
    PillStyle.Ink -> PillColors(ink, textOnDarkHigh, null, shadowWarm)
    PillStyle.White -> PillColors(paperWhite, ink, null, shadowNavy)
    PillStyle.Outline -> PillColors(Color.Transparent, ink, ink, null)
    PillStyle.OutlineOnDark -> PillColors(Color.Transparent, textOnDarkHigh, outlineOnDark, null)
    PillStyle.Plate -> PillColors(plateOnDark, textOnDarkHigh, null, null)
}

/**
 * Fully-rounded button. Squashes slightly while pressed (springy, short; snaps on reduce motion).
 *
 * @param trailingIconTint optional tint for the trailing icon (e.g. AirmailRed arrow on a white pill).
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Red,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
    trailingIconTint: Color? = null,
    enabled: Boolean = true,
) {
    val colors = StampTheme.colors.pill(style)
    val shape = StampTheme.shapes.pill
    val s = StampTheme.spacing
    val source = remember { MutableInteractionSource() }
    val scale by pressScale(source)

    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = ButtonHeight)
            .then(if (colors.shadow != null) Modifier.tintedShadow(Elevation.Card, shape, colors.shadow) else Modifier)
            .clip(shape)
            .background(colors.container)
            .then(if (colors.border != null) Modifier.border(BorderWidth, colors.border, shape) else Modifier)
            .clickableWithRipple(source, enabled, colors.content, onClick)
            .padding(horizontal = s.l),
        horizontalArrangement = Arrangement.spacedBy(s.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(painterResource(leadingIcon), null, tint = colors.content, modifier = Modifier.size(IconSize))
        }
        Text(text, style = StampTheme.type.button, color = colors.content, maxLines = 1)
        if (trailingIcon != null) {
            Icon(painterResource(trailingIcon), null, tint = trailingIconTint ?: colors.content, modifier = Modifier.size(IconSize))
        }
    }
}

/**
 * Round icon button: Surface2 plate on Paper, translucent plate on dark (camera / Stamped!).
 * 48dp touch target.
 */
@Composable
fun CircleIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
    size: Dp = StampTheme.spacing.touchTarget,
) {
    val c = StampTheme.colors
    val source = remember { MutableInteractionSource() }
    val scale by pressScale(source)
    val content = if (onDark) c.textOnDarkHigh else c.ink
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(size)
            .clip(CircleShape)
            .background(if (onDark) c.plateOnDark else c.surface2)
            .clickableWithRipple(source, true, content, onClick, contentDescription),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(IconSize))
    }
}

@Composable
internal fun pressScale(source: MutableInteractionSource) = run {
    val pressed by source.collectIsPressedAsState()
    animateFloatAsState(if (pressed) PressedScale else 1f, airmailSpring(rememberReduceMotion()), label = "press")
}

internal fun Modifier.clickableWithRipple(
    source: MutableInteractionSource,
    enabled: Boolean,
    rippleColor: Color,
    onClick: () -> Unit,
    contentDescription: String? = null,
): Modifier = this
    .clickable(
        interactionSource = source,
        indication = ripple(color = rippleColor),
        enabled = enabled,
        role = Role.Button,
        onClick = onClick,
    )
    .then(
        if (contentDescription != null) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        },
    )

private const val PressedScale = 0.96f
private val ButtonHeight = 52.dp
private val BorderWidth = 1.5.dp
private val IconSize = 20.dp

@PaperPreview
@Composable
private fun ButtonsPreview() {
    StampAppTheme {
        Column(
            Modifier.padding(StampTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(StampTheme.spacing.s),
        ) {
            PillButton("Stamp it", {}, leadingIcon = R.drawable.ic_camera)
            PillButton("Share this page", {}, style = PillStyle.Ink)
            PillButton("Map", {}, style = PillStyle.Outline)
            Row(
                Modifier
                    .background(StampTheme.colors.cobalt, StampTheme.shapes.card)
                    .padding(StampTheme.spacing.m),
                horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.s),
            ) {
                PillButton("Share", {}, style = PillStyle.OutlineOnDark)
                PillButton(
                    "Stick in album",
                    {},
                    style = PillStyle.White,
                    trailingIcon = R.drawable.ic_arrow_right,
                    trailingIconTint = StampTheme.colors.airmailRed,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.s)) {
                CircleIconButton(R.drawable.ic_back, "Back", {})
                Box(Modifier.background(StampTheme.colors.ink, StampTheme.shapes.card).padding(StampTheme.spacing.xs)) {
                    CircleIconButton(R.drawable.ic_close, "Close", {}, onDark = true)
                }
            }
        }
    }
}

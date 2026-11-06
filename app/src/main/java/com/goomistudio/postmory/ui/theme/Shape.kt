package com.goomistudio.postmory.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Radii from CLAUDE.md §5. */
@Immutable
data class AirmailShapes(
    /** Buttons, chips, streak chip, nav bar — fully rounded. */
    val pill: Shape,
    /** Cards (envelope card, album page, plates) — 16dp. */
    val card: Shape,
    /** Camera viewfinder — 20dp. */
    val viewfinder: Shape,
    /** Album cover: 4dp on the spine (start) side, 12dp on the open (end) side. */
    val albumCover: Shape,
    /** Shape-picker tiles, text fields, small plates — 12dp. */
    val tile: Shape,
    /** Name plates on covers, thumbnails — 4dp. */
    val plate: Shape,
)

internal val AirmailShapeSet = AirmailShapes(
    pill = CircleShape,
    card = RoundedCornerShape(16.dp),
    viewfinder = RoundedCornerShape(20.dp),
    albumCover = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp),
    tile = RoundedCornerShape(12.dp),
    plate = RoundedCornerShape(4.dp),
)

/** Material 3 shapes mapped onto the same radii, for any M3 component fallbacks. */
internal val AirmailMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

val LocalAirmailShapes = staticCompositionLocalOf { AirmailShapeSet }

/** Elevation steps for tinted shadows. */
object Elevation {
    val Low: Dp = 2.dp
    val Card: Dp = 6.dp
    val Stamp: Dp = 8.dp
    val Raised: Dp = 12.dp
    val Floating: Dp = 20.dp
}

/** Spot/ambient strength of tinted shadows — soft, never harsh. */
private const val SpotAlpha = 0.36f
private const val AmbientAlpha = 0.18f

/**
 * Soft shadow tinted with [tint] (warm brown on paper, navy on blue) instead of pure black.
 * Use StampTheme.colors.shadowWarm / shadowNavy as the tint.
 */
fun Modifier.tintedShadow(
    elevation: Dp,
    shape: Shape,
    tint: Color,
    clip: Boolean = false,
): Modifier = shadow(
    elevation = elevation,
    shape = shape,
    clip = clip,
    ambientColor = tint.copy(alpha = AmbientAlpha),
    spotColor = tint.copy(alpha = SpotAlpha),
)

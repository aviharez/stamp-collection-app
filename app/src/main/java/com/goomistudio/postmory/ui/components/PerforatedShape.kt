package com.goomistudio.postmory.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.goomistudio.postmory.domain.model.PerforationStyle
import com.goomistudio.postmory.domain.model.StampShape
import com.goomistudio.postmory.ui.theme.PaperPreview
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Stamp paper outline: a (slightly rounded) rectangle or circle with half-circle perforation
 * holes centred on every edge.
 *
 * The path is built in one pass with arcs (no boolean path ops), so it is cheap to rebuild and
 * identical when reused for bitmap export via [perforatedPath].
 *
 * Never cast an elevation shadow from this shape (concave path → RenderThread stall); see [Stamp].
 */
class PerforatedShape(
    private val stampShape: StampShape,
    private val style: PerforationStyle = PerforationStyle.Classic,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = with(density) {
            perforatedPath(
                size = size,
                stampShape = stampShape,
                holeRadius = style.holeRadiusDp.dp.toPx(),
                pitch = style.pitchDp.dp.toPx(),
                cornerRadius = PerforationCornerDp.dp.toPx(),
            )
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is PerforatedShape && other.stampShape == stampShape && other.style == style

    override fun hashCode(): Int = 31 * stampShape.hashCode() + style.hashCode()
}

/** Corner radius of the stamp paper, in dp at on-screen size. */
const val PerforationCornerDp = 1.5f

/**
 * Builds the perforated outline in pixels. Shared by [PerforatedShape] (screen) and the
 * stamp renderer (export): call `.asAndroidPath()` on the result to draw it on a Canvas.
 */
fun perforatedPath(
    size: Size,
    stampShape: StampShape,
    holeRadius: Float,
    pitch: Float,
    cornerRadius: Float = 0f,
): Path = when (stampShape) {
    StampShape.Circle -> circlePath(size, holeRadius, pitch)
    else -> rectPath(size, holeRadius, pitch, cornerRadius)
}

private fun holeCount(length: Float, pitch: Float): Int = max(1, (length / pitch).roundToInt())

private fun rectPath(size: Size, r: Float, pitch: Float, corner: Float): Path {
    val w = size.width
    val h = size.height
    val c = corner.coerceAtMost(r)
    val path = Path()

    // Hole centres along an edge, inset half a step from each corner so corners stay solid.
    fun centres(length: Float): List<Float> {
        val n = holeCount(length, pitch)
        val step = length / n
        return List(n) { i -> step * (i + 0.5f) }
    }

    fun hole(cx: Float, cy: Float, startAngle: Float) {
        // Sweep -180° from the entry point bends the arc into the paper.
        path.arcTo(Rect(Offset(cx, cy), r), startAngle, -180f, forceMoveTo = false)
    }

    path.moveTo(c, 0f)
    // Top edge, left → right. Hole entry at angle 180°.
    centres(w).forEach { x -> path.lineTo(x - r, 0f); hole(x, 0f, 180f) }
    path.lineTo(w - c, 0f)
    if (c > 0f) path.arcTo(Rect(Offset(w - c, c), c), 270f, 90f, false)
    // Right edge, top → bottom. Entry at 270°.
    centres(h).forEach { y -> path.lineTo(w, y - r); hole(w, y, 270f) }
    path.lineTo(w, h - c)
    if (c > 0f) path.arcTo(Rect(Offset(w - c, h - c), c), 0f, 90f, false)
    // Bottom edge, right → left. Entry at 0°.
    centres(w).reversed().forEach { x -> path.lineTo(x + r, h); hole(x, h, 0f) }
    path.lineTo(c, h)
    if (c > 0f) path.arcTo(Rect(Offset(c, h - c), c), 90f, 90f, false)
    // Left edge, bottom → top. Entry at 90°.
    centres(h).reversed().forEach { y -> path.lineTo(0f, y + r); hole(0f, y, 90f) }
    path.lineTo(0f, c)
    if (c > 0f) path.arcTo(Rect(Offset(c, c), c), 180f, 90f, false)
    path.close()
    return path
}

private fun circlePath(size: Size, r: Float, pitch: Float): Path {
    val outer = minOf(size.width, size.height) / 2f
    val centre = Offset(size.width / 2f, size.height / 2f)
    val n = holeCount((2 * PI * outer).toFloat(), pitch)
    val stepDeg = 360f / n
    // Half the angle (seen from the centre) that each hole cuts out of the outer circle.
    val halfCutDeg = Math.toDegrees(2.0 * asin((r / (2f * outer)).toDouble())).toFloat()
    val outerRect = Rect(centre, outer)
    val path = Path()

    fun pointOnOuter(deg: Float): Offset {
        val rad = Math.toRadians(deg.toDouble())
        return Offset(centre.x + outer * cos(rad).toFloat(), centre.y + outer * sin(rad).toFloat())
    }

    for (i in 0 until n) {
        val theta = i * stepDeg - 90f
        val holeCentre = pointOnOuter(theta)
        val entry = pointOnOuter(theta - halfCutDeg)
        val exit = pointOnOuter(theta + halfCutDeg)
        val a1 = Math.toDegrees(atan2((entry.y - holeCentre.y).toDouble(), (entry.x - holeCentre.x).toDouble())).toFloat()
        val a2 = Math.toDegrees(atan2((exit.y - holeCentre.y).toDouble(), (exit.x - holeCentre.x).toDouble())).toFloat()
        // Go the "inner" way round the hole (through the side facing the stamp centre).
        var sweep = ((a2 - a1) % 360f + 360f) % 360f
        val inward = theta + 180f
        val mid = a1 + sweep / 2f
        if (angularDistance(mid, inward) > 90f) sweep -= 360f

        if (i == 0) path.moveTo(entry.x, entry.y)
        path.arcTo(Rect(holeCentre, r), a1, sweep, forceMoveTo = false)
        // Outer rim up to the next hole.
        path.arcTo(outerRect, theta + halfCutDeg, stepDeg - 2f * halfCutDeg, forceMoveTo = false)
    }
    path.close()
    return path
}

private fun angularDistance(a: Float, b: Float): Float {
    val d = ((a - b) % 360f + 360f) % 360f
    return if (d > 180f) 360f - d else d
}

@PaperPreview
@Composable
private fun PerforatedShapePreview() {
    StampAppTheme {
        Row(
            Modifier.background(StampTheme.colors.kraft).padding(StampTheme.spacing.m),
            horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.m),
        ) {
            StampShape.entries.forEach { shape ->
                Box(
                    Modifier
                        .width(72.dp)
                        .aspectRatio(shape.aspectRatio)
                        .background(StampTheme.colors.paperWhite, PerforatedShape(shape)),
                )
            }
        }
    }
}

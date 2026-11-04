package com.aviharez.stamp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/** Washi tape patterns, each tied to an Airmail colour token. */
enum class TapeColor { Red, Cobalt, Sage;

    companion object {
        /** Stable pick for a seed (e.g. a stamp id hash). */
        fun forSeed(seed: Long): TapeColor = entries[Math.floorMod(seed, entries.size.toLong()).toInt()]
    }
}

@Composable
private fun TapeColor.color(): Color = when (this) {
    TapeColor.Red -> StampTheme.colors.airmailRed
    TapeColor.Cobalt -> StampTheme.colors.cobalt
    TapeColor.Sage -> StampTheme.colors.sage
}

/**
 * Semi-transparent striped strip of washi tape with torn (zig-zag) ends. Sits on top of a stamp
 * corner; whatever is below shows through. Use [Tilt.tape] for [rotation].
 */
@Composable
fun WashiTape(
    modifier: Modifier = Modifier,
    color: TapeColor = TapeColor.Red,
    rotation: Float = 0f,
    width: Dp = DefaultWidth,
    height: Dp = DefaultHeight,
) {
    val base = color.color()
    val white = StampTheme.colors.paperWhite
    Canvas(modifier.size(width, height).rotate(rotation)) {
        val w = size.width
        val h = size.height
        // Squeezed to nothing (e.g. mid-rotation): the stripe step below would be 0 → endless loop.
        if (w <= 0f || h <= 0f) return@Canvas
        val tooth = h / TornTeeth
        val bite = h * TornDepth
        val outline = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            // Torn right end.
            for (i in 1..TornTeeth) lineTo(if (i % 2 == 1) w - bite else w, tooth * i)
            lineTo(0f, h)
            // Torn left end.
            for (i in TornTeeth - 1 downTo 0) lineTo(if (i % 2 == 1) bite else 0f, tooth * i)
            close()
        }
        clipPath(outline) {
            drawRect(base.copy(alpha = BaseAlpha))
            val pitch = h * StripePitch
            var x = -h
            while (x < w + h) {
                drawLine(
                    color = white.copy(alpha = StripeAlpha),
                    start = Offset(x, h),
                    end = Offset(x + h, 0f),
                    strokeWidth = pitch / 2f,
                    cap = StrokeCap.Butt,
                )
                x += pitch
            }
        }
    }
}

private val DefaultWidth = 52.dp
private val DefaultHeight = 16.dp
private const val TornTeeth = 6
private const val TornDepth = 0.14f
private const val StripePitch = 0.7f
private const val BaseAlpha = 0.5f
private const val StripeAlpha = 0.4f

@PaperPreview
@Composable
private fun WashiTapePreview() {
    StampAppTheme {
        Row(
            Modifier.padding(StampTheme.spacing.l),
            horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TapeColor.entries.forEachIndexed { i, tape ->
                Box {
                    Stamp(StampShape.Portrait, Modifier.width(72.dp)) {
                        PreviewArt(PreviewArtKind.entries[i], Modifier.matchParentSize())
                    }
                    WashiTape(
                        Modifier.align(Alignment.TopCenter).padding(top = 0.dp),
                        color = tape,
                        rotation = Tilt.tape(i.toLong()),
                    )
                }
            }
        }
    }
}

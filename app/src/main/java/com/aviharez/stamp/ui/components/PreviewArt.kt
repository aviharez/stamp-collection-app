package com.aviharez.stamp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.aviharez.stamp.ui.theme.StampTheme

/** Flat stand-in illustrations for @Preview and the debug gallery (real stamps hold photos). */
enum class PreviewArtKind { Sunset, Sea, Night }

@Composable
internal fun PreviewArt(kind: PreviewArtKind, modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    Canvas(modifier) {
        when (kind) {
            PreviewArtKind.Sunset -> {
                drawRect(Brush.verticalGradient(listOf(c.paper, c.kraft)))
                drawCircle(c.ember, radius = size.width * 0.17f, center = Offset(size.width * 0.62f, size.height * 0.3f))
                drawRidge(0.52f, 0.30f, c.sage.copy(alpha = 0.55f))
                drawRidge(0.66f, 0.22f, c.sage)
                drawRect(c.ink.copy(alpha = 0.35f), topLeft = Offset(0f, size.height * 0.86f), size = Size(size.width, size.height * 0.14f))
            }
            PreviewArtKind.Sea -> {
                drawRect(Brush.verticalGradient(listOf(c.paperWhite, c.kraft)))
                drawCircle(c.ember.copy(alpha = 0.85f), radius = size.width * 0.12f, center = Offset(size.width * 0.3f, size.height * 0.32f))
                drawRect(c.cobalt.copy(alpha = 0.55f), topLeft = Offset(0f, size.height * 0.6f), size = Size(size.width, size.height * 0.4f))
                drawRect(c.cobalt.copy(alpha = 0.35f), topLeft = Offset(0f, size.height * 0.75f), size = Size(size.width, size.height * 0.25f))
            }
            PreviewArtKind.Night -> {
                drawRect(c.ink)
                drawCircle(c.paperWhite, radius = size.width * 0.09f, center = Offset(size.width * 0.72f, size.height * 0.2f))
                val bw = size.width / 5f
                listOf(0.55f, 0.35f, 0.5f, 0.28f, 0.6f).forEachIndexed { i, top ->
                    drawRect(c.cobalt.copy(alpha = 0.45f), topLeft = Offset(i * bw, size.height * top), size = Size(bw * 0.9f, size.height))
                }
            }
        }
    }
}

/** Zig-zag mountain ridge starting at [baseline] (fraction of height) with peak [amplitude]. */
private fun DrawScope.drawRidge(baseline: Float, amplitude: Float, color: androidx.compose.ui.graphics.Color) {
    val path = Path().apply {
        moveTo(0f, size.height * baseline)
        val peaks = listOf(0.22f to -1f, 0.42f to -0.35f, 0.62f to -0.85f, 0.85f to -0.2f, 1f to -0.55f)
        peaks.forEach { (x, dy) -> lineTo(size.width * x, size.height * (baseline + dy * amplitude * 0.6f)) }
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(path, color)
}

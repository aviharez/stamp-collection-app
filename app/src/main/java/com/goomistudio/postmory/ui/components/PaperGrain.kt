package com.goomistudio.postmory.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import com.goomistudio.postmory.R
import com.goomistudio.postmory.ui.theme.PaperPreview
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme

/** Default grain strength (CLAUDE.md §5: multiply, ~30 % opacity). */
const val PaperGrainAlpha = 0.3f

/**
 * Subtle paper-noise overlay: a pre-rendered tiling PNG multiplied onto whatever is drawn
 * underneath. Place it after the background, e.g. inside a Box with `Modifier.matchParentSize()`.
 */
@Composable
fun PaperGrain(modifier: Modifier = Modifier, alpha: Float = PaperGrainAlpha) {
    val tile = ImageBitmap.imageResource(R.drawable.paper_grain)
    val brush = remember(tile) { ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)) }
    Canvas(modifier) {
        drawRect(brush = brush, alpha = alpha, blendMode = BlendMode.Multiply)
    }
}

/** A coloured, grained surface (kraft album pages, cobalt celebration background, covers). */
@Composable
fun GrainedSurface(
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    grainAlpha: Float = PaperGrainAlpha,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier.clip(shape).background(color)) {
        PaperGrain(Modifier.matchParentSize(), alpha = grainAlpha)
        content()
    }
}

@PaperPreview
@Composable
private fun PaperGrainPreview() {
    StampAppTheme {
        Row(
            Modifier.padding(StampTheme.spacing.m),
            horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.m),
        ) {
            GrainedSurface(StampTheme.colors.kraft, Modifier.size(96.dp), StampTheme.shapes.card)
            GrainedSurface(StampTheme.colors.cobalt, Modifier.size(96.dp), StampTheme.shapes.card)
            GrainedSurface(StampTheme.colors.sage, Modifier.size(96.dp), StampTheme.shapes.albumCover)
        }
    }
}

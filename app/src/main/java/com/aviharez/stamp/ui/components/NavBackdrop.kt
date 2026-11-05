package com.aviharez.stamp.ui.components

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/**
 * Records what this node draws into [layer] (and still draws it), so a [NavBackdrop] can show a
 * blurred copy of it. Put it on the content that scrolls behind the nav bar.
 */
fun Modifier.backdropSource(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

/**
 * Frosted strip behind the StampNavBar: the bottom [height] of the content recorded in [source],
 * blurred and washed with Paper, fading in over [Fade] above it so there is no hard edge. Place it
 * at the bottom of the same box as the content, under the nav bar. Android 12+ blurs; older
 * phones (no RenderEffect) get a stronger Paper fade instead.
 */
@Composable
fun NavBackdrop(source: GraphicsLayer, height: Dp, modifier: Modifier = Modifier) {
    if (height <= 0.dp) return
    val paper = StampTheme.colors.paper
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height + Fade)
            .graphicsLayer {
                clip = true
                compositingStrategy = CompositingStrategy.Offscreen
                if (blurs) renderEffect = BlurEffect(BlurRadius.toPx(), BlurRadius.toPx(), TileMode.Clamp)
            },
    ) {
        val fade = Fade.toPx() / size.height
        if (blurs) {
            // The content is laid out over the whole box; this strip is its bottom edge.
            translate(top = size.height - source.size.height) { drawLayer(source) }
            drawRect(paper.copy(alpha = WashAlpha))
        } else {
            drawRect(paper.copy(alpha = FallbackAlpha))
        }
        // Fade the top edge in (the blur then softens the fade itself).
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, fade to Color.Black),
            blendMode = BlendMode.DstIn,
        )
    }
}

private val BlurRadius = 14.dp
private val Fade = 28.dp
private const val WashAlpha = 0.35f
private const val FallbackAlpha = 0.88f

@Preview(showBackground = true, backgroundColor = 0xFFF6F0E6, widthDp = 360, heightDp = 360)
@Composable
private fun NavBackdropPreview() {
    StampAppTheme {
        val layer = rememberGraphicsLayer()
        val c = StampTheme.colors
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().backdropSource(layer)) {
                listOf(c.airmailRed, c.cobalt, c.sage, c.kraftDark, c.ink, c.ember).forEach { color ->
                    Canvas(Modifier.fillMaxWidth().height(60.dp)) {
                        drawCircle(color, radius = 22.dp.toPx())
                        drawRect(color, size = size.copy(width = size.width / 4))
                    }
                }
            }
            NavBackdrop(layer, 100.dp, Modifier.align(Alignment.BottomCenter))
            StampNavBar(
                selected = NavTab.Album,
                onSelect = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = StampTheme.spacing.screenFullBleed, vertical = StampTheme.spacing.xs)
                    .size(width = 344.dp, height = 84.dp),
            )
        }
    }
}

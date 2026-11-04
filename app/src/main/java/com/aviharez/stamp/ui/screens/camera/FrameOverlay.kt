package com.aviharez.stamp.ui.screens.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.ui.components.PerforationCornerDp
import com.aviharez.stamp.ui.components.perforatedPath
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import kotlin.math.min

/**
 * Where the stamp frame sits in the viewfinder (px).
 * @param outer perforated paper edge; @param window clear picture area (what gets captured);
 * @param shortDp short side of [outer] in dp (export scales perforations from it).
 */
data class FrameGeometry(val outer: Rect, val window: Rect, val shortDp: Float)

fun frameGeometry(viewfinder: Size, shape: StampShape, perforation: PerforationStyle, density: Density): FrameGeometry {
    val maxW = viewfinder.width * FrameMaxWidth
    val maxH = viewfinder.height * FrameMaxHeight
    var w = maxW
    var h = w / shape.aspectRatio
    if (h > maxH) {
        h = maxH
        w = h * shape.aspectRatio
    }
    val outer = Rect(Offset((viewfinder.width - w) / 2f, (viewfinder.height - h) / 2f), Size(w, h))
    val margin = with(density) { perforation.paperMarginDp.dp.toPx() }
    return FrameGeometry(outer = outer, window = outer.deflate(margin), shortDp = with(density) { min(w, h).toDp().value })
}

/** Fractions of the viewfinder the frame may use (from design/camera.png). */
private const val FrameMaxWidth = 0.70f
private const val FrameMaxHeight = 0.72f

/** Dims everything outside the stamp and draws its PaperWhite perforated border. */
@Composable
fun FrameOverlay(
    geometry: FrameGeometry,
    shape: StampShape,
    perforation: PerforationStyle,
    modifier: Modifier = Modifier,
) {
    val c = StampTheme.colors
    Box(
        modifier.drawWithCache {
            val hole = with(this) { perforation.holeRadiusDp.dp.toPx() }
            val pitch = with(this) { perforation.pitchDp.dp.toPx() }
            val corner = with(this) { PerforationCornerDp.dp.toPx() }
            val outerPath = perforatedPath(geometry.outer.size, shape, hole, pitch, corner).apply {
                translate(geometry.outer.topLeft)
            }
            val windowPath = Path().apply {
                if (shape == StampShape.Circle) addOval(geometry.window) else addRect(geometry.window)
            }
            val full = Path().apply { addRect(Rect(Offset.Zero, size)) }
            // Path ops run once per size/shape change (cached), never per frame.
            val dim = Path.combine(PathOperation.Difference, full, outerPath)
            val paper = Path.combine(PathOperation.Difference, outerPath, windowPath)
            onDrawBehind {
                drawPath(dim, c.ink.copy(alpha = DimAlpha))
                drawPath(paper, c.paperWhite)
            }
        },
    )
}

private const val DimAlpha = 0.55f

@Preview(showBackground = true, backgroundColor = 0xFF6E8F6A)
@Composable
private fun FrameOverlayPreview() {
    StampAppTheme {
        val density = LocalDensity.current
        val vf = with(density) { Size(300.dp.toPx(), 400.dp.toPx()) }
        val g = frameGeometry(vf, StampShape.Portrait, PerforationStyle.Classic, density)
        FrameOverlay(g, StampShape.Portrait, PerforationStyle.Classic, Modifier.size(300.dp, 400.dp))
    }
}

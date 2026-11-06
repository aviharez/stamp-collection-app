package com.goomistudio.postmory.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.goomistudio.postmory.data.map.EqualEarth
import com.goomistudio.postmory.data.map.WorldMapRepository
import com.goomistudio.postmory.data.map.WorldOutline
import com.goomistudio.postmory.ui.theme.PaperPreview
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme
import kotlin.math.max

/** The part of the projected world a map shows (Equal Earth units, y grows north). */
@Immutable
data class MapWindow(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val aspect: Float get() = (maxX - minX) / (maxY - minY)

    fun toScreen(x: Float, y: Float, size: Size): Offset =
        Offset((x - minX) / (maxX - minX) * size.width, (maxY - y) / (maxY - minY) * size.height)

    fun place(latitude: Double, longitude: Double, size: Size): Offset =
        toScreen(EqualEarth.x(longitude, latitude).toFloat(), EqualEarth.y(latitude).toFloat(), size)

    private fun intersects(b: FloatArray) = b[2] >= minX && b[0] <= maxX && b[3] >= minY && b[1] <= maxY

    companion object {
        /** Everything but Antarctica and the far north. */
        val World = MapWindow(
            minX = EqualEarth.x(-180.0, 0.0).toFloat(),
            minY = EqualEarth.y(WorldSouth).toFloat(),
            maxX = EqualEarth.x(180.0, 0.0).toFloat(),
            maxY = EqualEarth.y(WorldNorth).toFloat(),
        )

        /**
         * A window around [bounds] (minX, minY, maxX, maxY) with some margin, at least [minSpan]
         * wide, widened or heightened to [aspect] (width / height).
         */
        fun around(bounds: FloatArray, aspect: Float, minSpan: Float = MinSpan): MapWindow {
            val cx = (bounds[0] + bounds[2]) / 2
            val cy = (bounds[1] + bounds[3]) / 2
            var w = max((bounds[2] - bounds[0]) * (1 + Margin * 2), minSpan)
            var h = max((bounds[3] - bounds[1]) * (1 + Margin * 2), minSpan / aspect)
            if (w / h < aspect) w = h * aspect else h = w / aspect
            return MapWindow(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        }

        private const val WorldSouth = -56.0
        private const val WorldNorth = 84.0
        private const val Margin = 0.18f
        /** About 6° of longitude: a city-state still shows its neighbours. */
        private const val MinSpan = 0.11f
    }

    internal fun visible(bounds: FloatArray) = intersects(bounds)
}

/** Colours of a [WorldMap]. */
@Immutable
data class WorldMapColors(val land: Color, val border: Color, val visited: Color, val visitedBorder: Color)

/** Land on kraft: pale paper countries, the ones you stamped in postmark red. */
@Composable
fun kraftMapColors(): WorldMapColors {
    val c = StampTheme.colors
    return WorldMapColors(
        land = c.paperWhite.copy(alpha = LandOnKraftAlpha),
        border = c.kraftDark,
        visited = c.airmailRed.copy(alpha = VisitedAlpha),
        visitedBorder = c.airmailRed,
    )
}

/**
 * The bundled world map drawn flat (no tiles, no network). Countries whose code is in [visited] get
 * the visited colours. [overlay] draws on top in the same space (use [MapWindow.place]).
 */
@Composable
fun WorldMap(
    outline: WorldOutline,
    window: MapWindow,
    visited: Set<String>,
    modifier: Modifier = Modifier,
    colors: WorldMapColors = kraftMapColors(),
    overlay: DrawScope.() -> Unit = {},
) {
    Box(
        modifier
            .clipToBounds()
            .drawWithCache {
                val minStep = MinStep.dp.toPx()
                val paths = outline.countries.mapNotNull { country ->
                    val shown = country.ringBounds.indices.filter { window.visible(country.ringBounds[it]) }
                    if (shown.isEmpty()) return@mapNotNull null
                    val path = Path().apply {
                        fillType = PathFillType.EvenOdd
                        shown.forEach { addRing(country.rings[it], window, size, minStep) }
                    }
                    (country.code in visited) to path
                }
                val hairline = Stroke(Hairline.dp.toPx())
                onDrawBehind {
                    // Visited countries last, so their red border isn't covered by a neighbour's.
                    paths.forEach { (on, path) ->
                        if (!on) {
                            drawPath(path, colors.land)
                            drawPath(path, colors.border, style = hairline)
                        }
                    }
                    paths.forEach { (on, path) ->
                        if (on) {
                            drawPath(path, colors.visited)
                            drawPath(path, colors.visitedBorder, style = hairline)
                        }
                    }
                    overlay()
                }
            },
    )
}

/** Adds one ring, dropping points closer than [minStep] px to the last one kept (the world view needs few). */
private fun Path.addRing(ring: FloatArray, window: MapWindow, size: Size, minStep: Float) {
    var last = Offset.Unspecified
    var kept = 0
    for (i in ring.indices step 2) {
        val o = window.toScreen(ring[i], ring[i + 1], size)
        val isLast = i == ring.size - 2
        if (last != Offset.Unspecified && !isLast && (o - last).getDistanceSquared() < minStep * minStep) continue
        if (kept == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
        last = o
        kept++
    }
    if (kept > 0) close()
}

private const val LandOnKraftAlpha = 0.55f
private const val VisitedAlpha = 0.78f
private const val Hairline = 0.6f
private const val MinStep = 0.75f

@PaperPreview
@Composable
private fun WorldMapPreview() {
    StampAppTheme {
        val context = LocalContext.current
        val outline = remember { context.assets.open(WorldMapRepository.AssetName).use(WorldOutline::read) }
        GrainedSurface(StampTheme.colors.kraft, Modifier.width(360.dp).padding(StampTheme.spacing.m), shape = StampTheme.shapes.card) {
            WorldMap(
                outline,
                MapWindow.World,
                visited = setOf("JP", "ID", "PT", "FR", "US"),
                modifier = Modifier.fillMaxWidth().aspectRatio(MapWindow.World.aspect).padding(StampTheme.spacing.xs),
            )
        }
    }
}

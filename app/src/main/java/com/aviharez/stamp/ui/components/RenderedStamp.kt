package com.aviharez.stamp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.tintedShadow
import java.io.File

/**
 * A stamp that was already rendered to PNG (paper + perforations baked in, transparent outside).
 * Adds only the lift: a soft shadow from a convex shape under the paper (see [Stamp]).
 */
@Composable
fun RenderedStamp(
    file: File,
    shape: StampShape,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tilt: Float = 0f,
    elevation: Dp = Elevation.Stamp,
    shadowTint: Color = StampTheme.colors.shadowWarm,
    perforation: PerforationStyle = PerforationStyle.Classic,
) {
    val shadowShape = if (shape == StampShape.Circle) CircleShape else RectangleShape
    Box(modifier.aspectRatio(shape.aspectRatio).rotate(tilt)) {
        Box(
            Modifier
                .matchParentSize()
                .padding(perforation.holeRadiusDp.dp)
                .tintedShadow(elevation, shadowShape, shadowTint)
                .background(StampTheme.colors.paperWhite, shadowShape),
        )
        AsyncImage(
            model = file,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

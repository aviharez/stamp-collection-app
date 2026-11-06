package com.goomistudio.postmory.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.goomistudio.postmory.R
import com.goomistudio.postmory.domain.model.PerforationStyle
import com.goomistudio.postmory.domain.model.StampShape
import com.goomistudio.postmory.ui.theme.Elevation
import com.goomistudio.postmory.ui.theme.StampTheme
import com.goomistudio.postmory.ui.theme.tintedShadow
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
        if (LocalInspectionMode.current) {
            // @Preview has no stamp files: draw a stand-in picture on perforated paper instead.
            val kind = PreviewArtKind.entries[Math.floorMod(file.path.hashCode(), PreviewArtKind.entries.size)]
            Stamp(shape, Modifier.fillMaxSize(), perforation = perforation, elevation = 0.dp) {
                PreviewArt(kind, Modifier.matchParentSize())
            }
        } else {
            val loads = LocalImageLoads.current
            // The PNG is gone (should never happen; a backup skips such stamps): a faded photo mark
            // on the paper instead of a blank card that looks like a bug.
            var missing by remember(file) { mutableStateOf(false) }
            AsyncImage(
                model = file,
                onSuccess = { loads?.finished(file.path) },
                onError = {
                    missing = true
                    loads?.finished(file.path)
                },
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (missing) {
                Icon(
                    painterResource(R.drawable.ic_gallery),
                    contentDescription = null,
                    tint = StampTheme.colors.textMedium.copy(alpha = MissingAlpha),
                    modifier = Modifier.align(Alignment.Center).fillMaxSize(MissingIconFraction),
                )
            }
        }
    }
}

private const val MissingAlpha = 0.5f
private const val MissingIconFraction = 0.3f

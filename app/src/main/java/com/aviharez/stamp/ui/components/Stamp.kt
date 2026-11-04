package com.aviharez.stamp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.tintedShadow

/**
 * A postage stamp: [content] (usually the photo) on PaperWhite stamp paper with perforated edges.
 *
 * Give it a width (or height) via [modifier]; the aspect ratio comes from [shape].
 * Use [tilt] (see [Tilt.stamp]) in album/collage contexts and 0° in editors.
 * [shadowTint]: warm brown on paper (default), StampTheme.colors.shadowNavy on cobalt / ink.
 *
 * @param denomination small italic value in the top corner of the picture, e.g. "26".
 * @param caption letter-spaced line at the bottom of the picture, e.g. "NIPPON · KYOTO".
 */
@Composable
fun Stamp(
    shape: StampShape,
    modifier: Modifier = Modifier,
    perforation: PerforationStyle = PerforationStyle.Classic,
    tilt: Float = 0f,
    elevation: Dp = Elevation.Stamp,
    shadowTint: Color = StampTheme.colors.shadowWarm,
    denomination: String? = null,
    caption: String? = null,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val paperShape = remember(shape, perforation) { PerforatedShape(shape, perforation) }
    val pictureShape = if (shape == StampShape.Circle) CircleShape else RectangleShape
    val shadowShape = pictureShape
    // Paper margin around the picture: a hole's diameter plus a little white.
    val margin = perforation.paperMarginDp.dp
    val colors = StampTheme.colors
    val type = StampTheme.type

    Box(
        modifier
            .aspectRatio(shape.aspectRatio)
            .rotate(tilt)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                        role = Role.Image
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        // Shadow is cast by a simple convex shape inset by one hole radius, NOT by the perforated
        // outline: Skia's concave-shadow tessellation of dozens of arcs freezes the RenderThread
        // (ANR on the emulator) and concave shadows are not drawn at all below API 30.
        if (elevation > 0.dp) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(perforation.holeRadiusDp.dp)
                    .tintedShadow(elevation, shadowShape, shadowTint)
                    // An empty layer is skipped by the renderer (and its shadow with it), so it
                    // gets a fill. Inset by one hole radius, the paper on top hides it fully.
                    .background(colors.paperWhite, shadowShape),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(colors.paperWhite, paperShape)
                .padding(margin)
                .clip(pictureShape),
        ) {
            content()
            if (denomination != null) {
                Text(
                    text = denomination,
                    style = type.albumName,
                    color = colors.textOnDarkHigh,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = StampTheme.spacing.xxs, end = StampTheme.spacing.xs),
                )
            }
            if (caption != null) {
                // Auto-shrinks so long place names fit small stamps.
                BasicText(
                    text = caption,
                    style = type.overline.copy(color = colors.textOnDarkHigh, textAlign = TextAlign.Center),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = CaptionMinSize,
                        maxFontSize = type.overline.fontSize,
                        stepSize = CaptionStep,
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = StampTheme.spacing.xs)
                        .padding(bottom = StampTheme.spacing.xs),
                )
            }
        }
    }
}

private val CaptionMinSize = 6.sp
private val CaptionStep = 0.5.sp

@PaperPreview
@Composable
private fun StampShapesPreview() {
    StampAppTheme {
        FlowRow(
            Modifier.padding(StampTheme.spacing.l),
            horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(StampTheme.spacing.l),
        ) {
            StampShape.entries.forEachIndexed { i, shape ->
                Stamp(shape = shape, modifier = Modifier.width(120.dp), tilt = Tilt.stamp(i.toLong())) {
                    PreviewArt(PreviewArtKind.entries[i % PreviewArtKind.entries.size], Modifier.fillMaxSize())
                }
            }
        }
    }
}

@PaperPreview
@Composable
private fun StampEdgesPreview() {
    StampAppTheme {
        FlowRow(
            Modifier.padding(StampTheme.spacing.l),
            horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.m),
        ) {
            PerforationStyle.entries.forEach { style ->
                Stamp(shape = StampShape.Portrait, perforation = style, modifier = Modifier.width(80.dp)) {
                    PreviewArt(PreviewArtKind.Sunset, Modifier.fillMaxSize())
                }
            }
        }
    }
}

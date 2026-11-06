package com.goomistudio.postmory.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.goomistudio.postmory.ui.theme.Elevation
import com.goomistudio.postmory.ui.theme.PaperPreview
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme
import com.goomistudio.postmory.ui.theme.tintedShadow

/**
 * Repeating 45° airmail stripes: Red / White / [accent] / White.
 * Each stripe is [stripeWidth] wide measured along the x axis.
 */
fun Modifier.airmailStripes(
    red: Color,
    white: Color,
    accent: Color,
    stripeWidth: Dp,
): Modifier = drawWithCache {
    val w = stripeWidth.toPx()
    val h = size.height
    val redPath = Path()
    val accentPath = Path()
    // Start far enough left that the slanted stripes also cover the left edge.
    var x = -h - (h % (w * 4))
    var i = 0
    while (x < size.width + w) {
        val target = when (i % 4) {
            0 -> redPath
            2 -> accentPath
            else -> null // white — painted by the background rect
        }
        target?.apply {
            moveTo(x + h, 0f)
            lineTo(x + h + w, 0f)
            lineTo(x + w, h)
            lineTo(x, h)
            close()
        }
        x += w
        i++
    }
    onDrawBehind {
        drawRect(white)
        drawPath(redPath, red)
        drawPath(accentPath, accent)
    }
}

/** A band of airmail stripes (camera / celebration top edge). Set the height via [modifier]. */
@Composable
fun AirmailStripe(
    modifier: Modifier = Modifier,
    accent: Color = StampTheme.colors.cobalt,
    stripeWidth: Dp = DefaultStripeWidth,
) {
    val c = StampTheme.colors
    Box(modifier.airmailStripes(red = c.airmailRed, white = c.paperWhite, accent = accent, stripeWidth = stripeWidth))
}

/**
 * Airmail envelope: striped border around a PaperWhite card (Home "Today's stamp is waiting").
 */
@Composable
fun AirmailEnvelope(
    modifier: Modifier = Modifier,
    shape: Shape = StampTheme.shapes.card,
    innerShape: Shape = StampTheme.shapes.tile,
    borderWidth: Dp = DefaultEnvelopeBorder,
    accent: Color = StampTheme.colors.cobalt,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = StampTheme.colors
    Box(
        modifier
            .tintedShadow(Elevation.Card, shape, c.shadowWarm)
            .clip(shape)
            .airmailStripes(red = c.airmailRed, white = c.paperWhite, accent = accent, stripeWidth = DefaultStripeWidth)
            .padding(borderWidth),
    ) {
        Box(Modifier.background(c.paperWhite, innerShape), content = content)
    }
}

private val DefaultStripeWidth = 8.dp
private val DefaultEnvelopeBorder = 8.dp

@PaperPreview
@Composable
private fun AirmailStripePreview() {
    StampAppTheme {
        Column(
            Modifier.padding(StampTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(StampTheme.spacing.m),
        ) {
            AirmailStripe(Modifier.fillMaxWidth().height(8.dp))
            AirmailStripe(Modifier.fillMaxWidth().height(8.dp), accent = StampTheme.colors.ink)
            AirmailEnvelope(Modifier.fillMaxWidth()) {
                Text(
                    "Today's stamp is waiting",
                    style = StampTheme.type.cardTitle,
                    modifier = Modifier.padding(StampTheme.spacing.l),
                )
            }
        }
    }
}

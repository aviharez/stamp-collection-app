package com.aviharez.stamp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.aviharez.stamp.R
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * Circular cancellation mark: double ring with the city (bold, letter-spaced) and date, plus
 * optional wavy cancellation lines running to the right.
 *
 * Total size is [diameter] high and `diameter × (1 + WavesWidthFactor)` wide when [showWaves].
 *
 * @param color AirmailRed ~85 % on light ([com.aviharez.stamp.ui.theme.AirmailColors.postmarkOnLight]),
 *   PaperWhite on the camera preview.
 * @param rotation degrees; use [Tilt.postmark] for the −15..−8° hand-stamped look.
 */
@Composable
fun Postmark(
    city: String,
    date: String,
    modifier: Modifier = Modifier,
    color: Color = StampTheme.colors.postmarkOnLight,
    diameter: Dp = 72.dp,
    showWaves: Boolean = true,
    dashedOuter: Boolean = false,
    rotation: Float = -10f,
) {
    val measurer = rememberTextMeasurer()
    val baseStyle = StampTheme.type.overline
    val description = stringResource(R.string.postmark_description, city, date)
    val width = if (showWaves) diameter * (1f + WavesWidthFactor) else diameter

    Canvas(
        modifier
            .size(width = width, height = diameter)
            .semantics { contentDescription = description },
    ) {
        val d = size.height
        val r = d / 2f
        val centre = Offset(r, r)
        rotate(rotation, pivot = centre) {
            // Rings
            val outerStroke = d * 0.028f
            drawCircle(
                color = color,
                radius = r - outerStroke / 2f,
                center = centre,
                style = Stroke(
                    width = outerStroke,
                    pathEffect = if (dashedOuter) PathEffect.dashPathEffect(floatArrayOf(d * 0.06f, d * 0.035f)) else null,
                ),
            )
            drawCircle(color = color, radius = r * 0.8f, center = centre, style = Stroke(width = d * 0.018f))

            // City — shrink to fit inside the inner ring.
            val maxTextWidth = r * 1.18f
            val cityText = measureFitting(measurer, city.uppercase(), baseStyle, color, (d * 0.17f).toSp(), maxTextWidth)
            val dateText = measureFitting(measurer, date.uppercase(), baseStyle, color, (d * 0.105f).toSp(), maxTextWidth * 0.9f)
            drawText(
                cityText,
                topLeft = Offset(centre.x - cityText.size.width / 2f, centre.y - cityText.size.height * 0.92f),
            )
            drawText(
                dateText,
                topLeft = Offset(centre.x - dateText.size.width / 2f, centre.y + d * 0.04f),
            )

            // Cancellation waves to the right of the ring.
            // A 0-size postmark would make the wave step 0 (endless loop), so skip waves then.
            if (showWaves && d > 0f) {
                val waveStroke = Stroke(width = d * 0.024f, cap = StrokeCap.Round)
                val startX = d * 0.86f
                val endX = size.width
                val amplitude = d * 0.045f
                val wavelength = d * 0.36f
                WaveOffsets.forEach { offset ->
                    val y0 = centre.y + offset * r
                    val path = Path().apply {
                        moveTo(startX, y0)
                        var x = startX
                        while (x < endX) {
                            x = (x + d * 0.02f).coerceAtMost(endX)
                            lineTo(x, y0 + amplitude * sin(2 * PI * (x - startX) / wavelength).toFloat())
                        }
                    }
                    drawPath(path, color, style = waveStroke)
                }
            }
        }
    }
}

/** Width of the wave block relative to the diameter. */
private const val WavesWidthFactor = 1.1f

/** Vertical offsets of the wavy lines, as a fraction of the ring radius. */
private val WaveOffsets = listOf(-0.42f, -0.14f, 0.14f, 0.42f)

private fun androidx.compose.ui.unit.Density.measureFitting(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    base: TextStyle,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    maxWidth: Float,
): androidx.compose.ui.text.TextLayoutResult {
    val style = base.copy(color = color, fontSize = fontSize, lineHeight = fontSize, letterSpacing = 0.14.em)
    val first = measurer.measure(text, style, maxLines = 1, softWrap = false, density = this)
    if (first.size.width <= maxWidth) return first
    val scale = maxWidth / first.size.width
    val smaller = fontSize * scale
    return measurer.measure(
        text,
        style.copy(fontSize = smaller, lineHeight = smaller),
        maxLines = 1,
        softWrap = false,
        density = this,
    )
}

@PaperPreview
@Composable
private fun PostmarkPreview() {
    StampAppTheme {
        Column(verticalArrangement = Arrangement.spacedBy(StampTheme.spacing.m)) {
            Postmark(city = "Kyoto", date = "03.11.26", modifier = Modifier.padding(StampTheme.spacing.m))
            Postmark(
                city = "Kyoto",
                date = "04 Nov '26",
                showWaves = false,
                dashedOuter = true,
                color = StampTheme.colors.postmarkOnDark,
                diameter = 88.dp,
                rotation = -12f,
                modifier = Modifier.background(StampTheme.colors.ink).padding(StampTheme.spacing.m),
            )
        }
    }
}

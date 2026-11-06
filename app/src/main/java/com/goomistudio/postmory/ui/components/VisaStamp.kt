package com.goomistudio.postmory.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.goomistudio.postmory.ui.theme.PaperPreview
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme
import kotlin.math.abs

/**
 * A passport visa: a rubber stamp for one country, double-ruled, in one of four inks and either round
 * or square (both picked from the country code, so a country always gets the same visa).
 * Read out as one sentence ([description]); the lines inside shrink to fit and never wrap, at font scale 1.
 */
@Composable
fun VisaStamp(
    code: String,
    name: String,
    lines: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    tilt: Float = 0f,
) {
    val c = StampTheme.colors
    val t = StampTheme.type
    val seed = abs(code.hashCode())
    val ink = listOf(c.airmailRed, c.cobalt, c.sage, c.ink)[seed % Inks].copy(alpha = InkAlpha)
    val round = seed / Inks % 2 == 0
    val shape = if (round) CircleShape else RoundedCornerShape(Corner)
    Box(
        modifier
            .rotate(tilt)
            .size(if (round) RoundSize else SquareWidth, if (round) RoundSize else SquareHeight)
            .border(OuterRule, ink, shape)
            .padding(RuleGap)
            .border(InnerRule, ink, shape)
            .padding(horizontal = if (round) RoundInset else StampTheme.spacing.s)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // A drawn rubber stamp of fixed size: its words stay at font scale 1 (the whole visa is read
        // out through [description], which follows the user's text size like everything else).
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FitText(code, t.cardTitle, color = ink)
                FitText(name.uppercase(), t.overline, color = ink, textAlign = TextAlign.Center)
                lines.forEach { FitText(it.uppercase(), t.overline, color = ink, textAlign = TextAlign.Center, minScale = LineMinScale) }
            }
        }
    }
}

private const val Inks = 4
private const val InkAlpha = 0.85f
private const val LineMinScale = 0.7f
private val Corner = 12.dp
private val RoundSize = 144.dp
private val SquareWidth = 176.dp
private val SquareHeight = 112.dp
private val OuterRule = 2.5.dp
private val InnerRule = 1.dp
private val RuleGap = 4.dp
private val RoundInset = 14.dp

@PaperPreview
@Composable
private fun VisaStampPreview() {
    StampAppTheme {
        Row(
            Modifier.fillMaxWidth().padding(StampTheme.spacing.m),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VisaStamp("JP", "Japan", listOf("First 3 Nov 2026", "20 stamps · 2 places"), "Japan", tilt = -8f)
            VisaStamp("PT", "Portugal", listOf("First 12 Jun 2026", "7 stamps · 2 places"), "Portugal", tilt = 6f)
        }
    }
}

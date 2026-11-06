package com.aviharez.stamp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.R
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay

/**
 * "Making your stamp…": an Ink scrim with a turning postmark ring, shown while a photo is being
 * turned into a stamp (crop, render, save), so a busy shutter never looks broken. It appears only
 * after a short wait (quick saves don't flicker) and swallows taps meanwhile; Back still works.
 */
@Composable
fun StampingOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(ShowAfterMillis)
            shown = true
        } else {
            shown = false
        }
    }
    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = fadeIn(tween(FadeMillis)),
        exit = fadeOut(tween(FadeMillis)),
    ) {
        StampingOverlayContent()
    }
}

@Composable
private fun StampingOverlayContent() {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val note = stringResource(R.string.camera_stamping)
    Box(
        Modifier
            .fillMaxSize()
            .background(c.ink.copy(alpha = ScrimAlpha))
            // Nothing underneath can be tapped while the stamp is made.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(s.m),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            PostmarkSpinner(ring = c.postmarkOnLight, inner = c.textOnDarkMedium, modifier = Modifier.size(RingSize))
            HandNote(note, color = c.textOnDarkHigh, rotation = NoteTilt)
        }
    }
}

/**
 * A dashed postmark ring that turns while something is being made (still and tilted with reduce
 * motion), around a thin inner ring. Sized by [modifier]; strokes are fixed.
 */
@Composable
fun PostmarkSpinner(ring: Color, inner: Color, modifier: Modifier = Modifier, outerStroke: Dp = OuterStroke, dash: Dp = Dash) {
    val reduceMotion = rememberReduceMotion()
    val turn = if (reduceMotion) {
        null
    } else {
        rememberInfiniteTransition(label = "postmarkSpinner").animateFloat(
            initialValue = 0f,
            targetValue = FullTurn,
            animationSpec = infiniteRepeatable(tween(TurnMillis, easing = LinearEasing), RepeatMode.Restart),
            label = "turn",
        )
    }
    Canvas(modifier) {
        val outerWidth = outerStroke.toPx()
        val dashPx = dash.toPx()
        rotate(turn?.value ?: TiltAtRest) {
            drawCircle(
                ring,
                radius = size.minDimension / 2 - outerWidth,
                style = Stroke(outerWidth, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx * DashGapShare))),
            )
        }
        drawCircle(inner, radius = size.minDimension / 2 * InnerShare, style = Stroke(InnerStroke.toPx()))
    }
}

private const val ShowAfterMillis = 200L
private const val FadeMillis = 180
private const val TurnMillis = 1600
private const val FullTurn = 360f
private const val TiltAtRest = -12f
private const val ScrimAlpha = 0.72f
private const val InnerShare = 0.7f
private const val DashGapShare = 0.8f
private const val NoteTilt = -2f
private val RingSize = 72.dp
private val OuterStroke = 3.dp
private val InnerStroke = 1.5.dp
private val Dash = 7.dp

@PaperPreview
@Composable
private fun StampingOverlayPreview() {
    StampAppTheme { StampingOverlayContent() }
}

@PaperPreview
@Composable
private fun PostmarkSpinnerPreview() {
    StampAppTheme { PostmarkSpinner(ring = StampTheme.colors.airmailRed, inner = StampTheme.colors.handInkSoft, modifier = Modifier.size(RingSize)) }
}

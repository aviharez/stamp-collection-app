package com.aviharez.stamp.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.ui.components.AirmailEnvelope
import com.aviharez.stamp.ui.components.Postmark
import com.aviharez.stamp.ui.components.PreviewArt
import com.aviharez.stamp.ui.components.PreviewArtKind
import com.aviharez.stamp.ui.components.Stamp
import com.aviharez.stamp.ui.components.rememberDateText
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cold-start splash, drawn over the app right after the system splash (plain Paper) hands over:
 * an airmail envelope addressed to the app drops in, today's postmark slams onto its stamp and
 * the tagline writes in (~0.8 s); it fades away once that has played and [appReady] (Home or the
 * welcome is known underneath). Reduce motion: shown still, gone once ready.
 */
@Composable
fun LaunchSplash(appReady: Boolean, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val drop = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val slam = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val words = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val fade = remember { Animatable(1f) }
    var played by remember { mutableStateOf(reduceMotion) }
    val finished by rememberUpdatedState(onFinished)

    // The whole stamping plays at once (no blank or half-made screen while loading); a slow start
    // just holds on the finished envelope until the app underneath is ready.
    LaunchedEffect(Unit) {
        if (reduceMotion) return@LaunchedEffect
        launch { drop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)) }
        // The spring's tail is invisible; the postmark lands as the envelope does.
        delay(DropMillis)
        launch { slam.animateTo(1f, tween(SlamMillis)) }
        delay(SlamMillis / 2L)
        words.animateTo(1f, tween(WordsMillis))
        delay(HoldMillis)
        played = true
    }
    LaunchedEffect(played && appReady) {
        if (!(played && appReady)) return@LaunchedEffect
        // Let the screen underneath get its first heavy frames out before fading over it.
        repeat(SettleFrames) { withFrameNanos { } }
        if (!reduceMotion) fade.animateTo(0f, tween(FadeMillis))
        finished()
    }

    val appName = stringResource(R.string.app_name)
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(StampTheme.colors.paper)
            // Swallows taps meant for the screen underneath while it shows.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .clearAndSetSemantics { contentDescription = appName },
        contentAlignment = Alignment.Center,
    ) {
        SplashEnvelope(drop.value, slam.value, words.value)
    }
}

/** The envelope addressed to the app: stamp top-right, postmark over its edge, name + tagline. */
@Composable
private fun SplashEnvelope(drop: Float, slam: Float, words: Float) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val appName = stringResource(R.string.app_name)
    AirmailEnvelope(
        Modifier
            .size(EnvelopeWidth, EnvelopeHeight)
            .graphicsLayer {
                alpha = drop.coerceIn(0f, 1f)
                translationY = (1f - drop) * DropDistance.toPx()
                val scale = DropScale + (1f - DropScale) * drop
                scaleX = scale
                scaleY = scale
            },
    ) {
        Box(Modifier.fillMaxSize()) {
            Stamp(
                StampShape.Portrait,
                Modifier.align(Alignment.TopEnd).padding(s.m).size(StampWidth, StampHeight),
                tilt = StampTilt,
            ) { PreviewArt(PreviewArtKind.Sea, Modifier.fillMaxSize()) }
            Postmark(
                city = appName.uppercase(),
                date = rememberDateText(R.string.pattern_postmark_date),
                diameter = PostmarkDiameter,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = PostmarkOffsetX, y = PostmarkOffsetY)
                    .graphicsLayer {
                        alpha = if (slam > 0f) 1f else 0f
                        val scale = SlamFrom - (SlamFrom - 1f) * slam
                        scaleX = scale
                        scaleY = scale
                    },
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(s.l)
                    .graphicsLayer {
                        alpha = words
                        translationY = (1f - words) * RiseDistance.toPx()
                    },
            ) {
                Text(appName, style = StampTheme.type.screenTitle, color = c.textHigh)
                Text(stringResource(R.string.splash_tagline), style = StampTheme.type.note, color = c.textMedium)
            }
        }
    }
}

private val EnvelopeWidth = 300.dp
private val EnvelopeHeight = 196.dp
private val StampWidth = 60.dp
private val StampHeight = 76.dp
private const val StampTilt = 3f
private val PostmarkDiameter = 64.dp
private val PostmarkOffsetX = (-44).dp
private val PostmarkOffsetY = 34.dp
private val DropDistance = (-48).dp
private const val DropScale = 0.92f
private const val SlamFrom = 1.4f
private val RiseDistance = 12.dp
private const val SettleFrames = 3
private const val DropMillis = 260L
private const val SlamMillis = 140
private const val WordsMillis = 220
private const val HoldMillis = 250L
private const val FadeMillis = 220

@Preview(showBackground = true, backgroundColor = 0xFFF6F0E6, widthDp = 360, heightDp = 640)
@Composable
private fun LaunchSplashPreview() {
    StampAppTheme {
        Box(Modifier.fillMaxSize().background(StampTheme.colors.paper), contentAlignment = Alignment.Center) {
            SplashEnvelope(drop = 1f, slam = 1f, words = 1f)
        }
    }
}

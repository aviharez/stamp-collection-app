package com.goomistudio.postmory.ui.screens.camera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.goomistudio.postmory.ui.theme.Elevation
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme
import com.goomistudio.postmory.ui.theme.airmailSpring
import com.goomistudio.postmory.ui.theme.rememberReduceMotion
import com.goomistudio.postmory.ui.theme.tintedShadow
import kotlin.math.PI
import kotlin.math.sin

/** Red postmark-style shutter: dashed PaperWhite ring + one cancellation wave. Haptic on tap. */
@Composable
fun ShutterButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = ShutterSize,
) {
    val c = StampTheme.colors
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PressedScale else 1f, airmailSpring(rememberReduceMotion()), label = "shutter")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(size)
            .tintedShadow(Elevation.Raised, CircleShape, c.airmailRed)
            .clip(CircleShape)
            .background(if (enabled) c.airmailRed else c.airmailRed.copy(alpha = DisabledAlpha))
            .clickable(
                interactionSource = source,
                indication = ripple(color = c.paperWhite),
                enabled = enabled,
                role = Role.Button,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.padding(size * RingInsetFraction).size(size)) {
            val stroke = this.size.minDimension * 0.04f
            drawCircle(
                c.paperWhite,
                radius = this.size.minDimension / 2f - stroke,
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 2f, stroke * 1.5f))),
            )
            val w = this.size.width * 0.44f
            val x0 = (this.size.width - w) / 2f
            val path = Path().apply {
                moveTo(x0, center.y)
                val steps = 32
                for (i in 1..steps) {
                    val x = w * i / steps
                    lineTo(x0 + x, center.y + this@Canvas.size.height * 0.06f * sin(2 * PI * x / w).toFloat())
                }
            }
            drawPath(path, c.paperWhite, style = Stroke(stroke * 1.4f, cap = StrokeCap.Round))
        }
    }
}

private val ShutterSize = 84.dp
private const val RingInsetFraction = 0.12f
private const val PressedScale = 0.92f
private const val DisabledAlpha = 0.5f

@Preview(showBackground = true, backgroundColor = 0xFF18223F)
@Composable
private fun ShutterButtonPreview() {
    StampAppTheme { Box(Modifier.padding(24.dp)) { ShutterButton({}, "Take stamp photo") } }
}

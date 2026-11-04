package com.aviharez.stamp.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Pencilled dashed outline: empty slots, "New album", the caption line. */
fun Modifier.dashedOutline(
    color: Color,
    cornerRadius: Dp = DefaultCorner,
    strokeWidth: Dp = DefaultStroke,
    dash: Dp = DefaultDash,
): Modifier = drawBehind {
    val inset = strokeWidth.toPx() / 2f
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx())),
        ),
    )
}

private val DefaultCorner = 12.dp
private val DefaultStroke = 1.5.dp
private val DefaultDash = 6.dp

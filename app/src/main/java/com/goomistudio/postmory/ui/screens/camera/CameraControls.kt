package com.goomistudio.postmory.ui.screens.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.goomistudio.postmory.R
import com.goomistudio.postmory.domain.model.StampShape
import com.goomistudio.postmory.ui.components.FitText
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Exposure: vertical slider with the EV value below (e.g. −0.3).
// ---------------------------------------------------------------------------------------------

@Composable
fun ExposureSlider(
    exposure: ExposureUi,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    iconRotation: Float = 0f,
) {
    val c = StampTheme.colors
    val density = LocalDensity.current
    val label = formatEv(exposure.ev)
    val description = stringResource(R.string.camera_exposure)
    val span = (exposure.max - exposure.min).coerceAtLeast(1)
    val fraction = (exposure.index - exposure.min).toFloat() / span
    var trackHeightPx by remember { mutableIntStateOf(1) }

    fun indexAt(y: Float): Int {
        val f = 1f - (y / trackHeightPx).coerceIn(0f, 1f)
        return exposure.min + (f * span).roundToInt()
    }

    Column(
        modifier.width(StampTheme.spacing.touchTarget),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_exposure),
            contentDescription = null,
            tint = c.textOnDarkHigh,
            modifier = Modifier.size(IconSize).rotate(iconRotation),
        )
        Box(
            Modifier
                .padding(vertical = StampTheme.spacing.xs)
                .width(StampTheme.spacing.touchTarget)
                .height(TrackHeight)
                .semantics {
                    contentDescription = description
                    stateDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        exposure.index.toFloat(),
                        exposure.min.toFloat()..exposure.max.toFloat(),
                        steps = (span - 1).coerceAtLeast(0),
                    )
                    setProgress { target -> onIndexChange(target.roundToInt()); true }
                }
                .pointerInput(exposure.min, exposure.max) {
                    trackHeightPx = size.height
                    detectTapGestures { onIndexChange(indexAt(it.y)) }
                }
                .pointerInput(exposure.min, exposure.max) {
                    trackHeightPx = size.height
                    detectVerticalDragGestures { change, _ -> onIndexChange(indexAt(change.position.y)) }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Canvas(Modifier.width(TrackWidth).height(TrackHeight)) {
                val r = size.width / 2f
                drawRoundRect(c.textOnDarkLow, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
                val filledTop = size.height * (1f - fraction)
                drawRoundRect(
                    c.airmailRed,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, filledTop),
                    size = Size(size.width, size.height - filledTop),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
                )
            }
            val thumbOffsetPx = with(density) { ((TrackHeight - ThumbSize) * (1f - fraction)).roundToPx() }
            Box(
                Modifier
                    .offset { IntOffset(0, thumbOffsetPx) }
                    .size(ThumbSize)
                    .background(c.paperWhite, CircleShape)
                    .padding(ThumbRing)
                    .background(c.airmailRed, CircleShape),
            )
        }
        Text(
            label,
            style = StampTheme.type.label,
            color = c.textOnDarkHigh,
            modifier = Modifier.rotate(iconRotation),
        )
    }
}

private val TrackHeight = 150.dp
private val TrackWidth = 4.dp
private val ThumbSize = 20.dp
private val ThumbRing = 3.dp
private val IconSize = 20.dp

// ---------------------------------------------------------------------------------------------
// Zoom / lens chips: .5 · 1× · 2 · 50MP
// ---------------------------------------------------------------------------------------------

/** Zoom stops the camera can actually do (ultra-wide only when min zoom < 1). */
fun zoomStops(minZoom: Float, maxZoom: Float): List<Float> = buildList {
    if (minZoom < UltraWideThreshold) add(minZoom)
    add(1f)
    if (maxZoom >= 2f) add(2f)
}

@Composable
fun ZoomChips(
    zoomRatio: Float,
    stops: List<Float>,
    onZoom: (Float) -> Unit,
    highResSupported: Boolean,
    highResOn: Boolean,
    onToggleHighRes: () -> Unit,
    modifier: Modifier = Modifier,
    iconRotation: Float = 0f,
) {
    val active = stops.minByOrNull { abs(it - zoomRatio) }?.takeIf { abs(it - zoomRatio) < ActiveTolerance }
    Row(
        modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stops.forEach { stop ->
            val selected = stop == active
            val text = zoomLabel(stop, selected)
            Chip(
                text = text,
                selected = selected,
                onClick = { onZoom(stop) },
                contentDescription = stringResource(R.string.camera_zoom_cd, zoomNumber(stop)),
                round = true,
                textRotation = iconRotation,
            )
        }
        if (highResSupported) {
            Chip(
                text = stringResource(R.string.camera_high_res),
                selected = highResOn,
                onClick = onToggleHighRes,
                contentDescription = stringResource(R.string.camera_high_res_cd),
                round = false,
                textRotation = iconRotation,
            )
        }
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    round: Boolean,
    textRotation: Float,
) {
    val c = StampTheme.colors
    Box(
        Modifier
            .heightIn(min = ChipSize)
            .widthIn(min = ChipSize)
            .clip(StampTheme.shapes.pill)
            .background(if (selected) c.paperWhite else c.ink.copy(alpha = ChipScrimAlpha))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = if (round) 0.dp else StampTheme.spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = StampTheme.type.label,
            color = if (selected) c.ink else c.textOnDarkHigh,
            modifier = Modifier.rotate(textRotation),
        )
    }
}

private fun zoomNumber(stop: Float): String =
    if (stop == stop.roundToInt().toFloat()) stop.roundToInt().toString() else "%.1f".format(stop)

/** ".5", "1×", "2" — the active chip gets the × like the mockup. */
private fun zoomLabel(stop: Float, selected: Boolean): String {
    val n = zoomNumber(stop).removePrefix("0")
    return if (selected) "$n×" else n
}

private const val UltraWideThreshold = 0.95f
private const val ActiveTolerance = 0.06f
private const val ChipScrimAlpha = 0.6f
private val ChipSize = 44.dp

// ---------------------------------------------------------------------------------------------
// Shape picker: four stamp-shaped tiles.
// ---------------------------------------------------------------------------------------------

@Composable
fun ShapePicker(
    selected: StampShape,
    onSelect: (StampShape) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StampTheme.colors
    Row(
        modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.xs),
    ) {
        StampShape.entries.forEach { shape ->
            val isSelected = shape == selected
            val outline = if (isSelected) c.airmailRed else c.textOnDarkMedium
            Column(
                Modifier
                    .weight(1f)
                    .clip(StampTheme.shapes.tile)
                    .background(if (isSelected) c.paperWhite else c.plateOnDark)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(shape) })
                    .padding(vertical = StampTheme.spacing.s),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.size(ShapeIconBox)) {
                    val stroke = 1.6.dp.toPx()
                    val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))
                    val h = size.height * 0.86f
                    val w = h * shape.aspectRatio
                    val (iw, ih) = if (w > size.width) size.width to size.width / shape.aspectRatio else w to h
                    val topLeft = androidx.compose.ui.geometry.Offset((size.width - iw) / 2f, (size.height - ih) / 2f)
                    if (shape == StampShape.Circle) {
                        drawCircle(outline, radius = ih / 2f, style = Stroke(stroke, pathEffect = dash))
                    } else {
                        drawRect(outline, topLeft = topLeft, size = Size(iw, ih), style = Stroke(stroke, pathEffect = dash))
                    }
                }
                FitText(
                    stringResource(shape.label),
                    StampTheme.type.label,
                    Modifier.padding(top = StampTheme.spacing.xxs),
                    color = if (isSelected) c.ink else c.textOnDarkMedium,
                )
            }
        }
    }
}

private val StampShape.label: Int
    get() = when (this) {
        StampShape.Portrait -> R.string.shape_portrait
        StampShape.Landscape -> R.string.shape_landscape
        StampShape.Square -> R.string.shape_square
        StampShape.Circle -> R.string.shape_circle
    }

private val ShapeIconBox = 24.dp

// ---------------------------------------------------------------------------------------------
// Previews (on Ink, like the camera).
// ---------------------------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF18223F)
@Composable
private fun CameraControlsPreview() {
    StampAppTheme {
        Column(Modifier.padding(StampTheme.spacing.m), verticalArrangement = Arrangement.spacedBy(StampTheme.spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExposureSlider(ExposureUi(index = -1, min = -6, max = 6, stepEv = 1f / 3f), {})
                ZoomChips(1f, listOf(0.5f, 1f, 2f), {}, highResSupported = true, highResOn = false, onToggleHighRes = {})
            }
            ShapePicker(StampShape.Portrait, {}, Modifier.fillMaxWidth())
        }
    }
}


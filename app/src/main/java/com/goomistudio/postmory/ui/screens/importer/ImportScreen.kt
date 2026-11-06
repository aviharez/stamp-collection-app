package com.goomistudio.postmory.ui.screens.importer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goomistudio.postmory.R
import com.goomistudio.postmory.data.image.NormRect
import com.goomistudio.postmory.domain.model.Place
import com.goomistudio.postmory.domain.model.StampShape
import com.goomistudio.postmory.ui.components.AirmailStripe
import com.goomistudio.postmory.ui.components.StampingOverlay
import com.goomistudio.postmory.ui.components.CircleIconButton
import com.goomistudio.postmory.ui.components.GalleryStampButton
import com.goomistudio.postmory.ui.components.HandNote
import com.goomistudio.postmory.ui.components.InkStrength
import com.goomistudio.postmory.ui.components.Postmark
import com.goomistudio.postmory.ui.components.rememberDateText
import com.goomistudio.postmory.ui.components.rememberPostmarkLines
import com.goomistudio.postmory.ui.screens.camera.FrameGeometry
import com.goomistudio.postmory.ui.screens.camera.FrameOverlay
import com.goomistudio.postmory.ui.screens.camera.ShapePicker
import com.goomistudio.postmory.ui.screens.camera.ShutterButton
import com.goomistudio.postmory.ui.screens.camera.frameGeometry
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme
import java.time.LocalDate
import kotlin.math.roundToInt

/** Launches the system photo picker for one image (no storage permission needed). */
@Composable
fun rememberPhotoPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(latest)
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/**
 * Gallery import: the picked photo behind the same perforated frame as the camera. Pinch and
 * drag to fit, rotate in quarter turns, choose the shape, then the red postmark button makes it
 * a stamp (same save flow and Stamped! moment as a capture).
 */
@Composable
fun ImportScreen(
    onClose: () -> Unit,
    onSaved: (stampId: String) -> Unit,
    onPickAnother: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val latestOnSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(state.saved) {
        state.saved?.let {
            latestOnSaved(it)
            viewModel.onSavedHandled()
        }
    }
    ImportContent(
        state = state,
        onClose = onClose,
        onSelectShape = viewModel::selectShape,
        onRotate = viewModel::rotate,
        onPickAnother = rememberPhotoPicker(onPickAnother),
        onMakeStamp = viewModel::makeStamp,
        modifier = modifier,
    )
}

@Composable
fun ImportContent(
    state: ImportUiState,
    onClose: () -> Unit,
    onSelectShape: (StampShape) -> Unit,
    onRotate: () -> Unit,
    onPickAnother: () -> Unit,
    onMakeStamp: (window: NormRect, frameShortDp: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    val density = LocalDensity.current
    // What is under the frame right now; the button renders exactly this (not state: no redraws).
    val shown = remember { ShownWindow() }
    val ready = state.photo != null && !state.saving

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(c.ink)) {
            AirmailStripe(Modifier.fillMaxWidth().height(s.xs))

            // Header: close · "City · date" of the photo · (balance)
            Row(
                Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = s.screenFullBleed, vertical = s.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(R.drawable.ic_close, stringResource(R.string.cd_close), onClose, onDark = true)
                val date = rememberDateText(R.string.pattern_short_date, state.day)
                Text(
                    state.place.city?.let { stringResource(R.string.camera_header_place, it, date) } ?: date,
                    style = StampTheme.type.albumName,
                    color = c.textOnDarkHigh,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = s.xs),
                )
                Spacer(Modifier.size(s.touchTarget))
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = s.screenFullBleed),
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints(
                    Modifier
                        .aspectRatio(ViewfinderAspect)
                        .clip(StampTheme.shapes.viewfinder)
                        .background(c.plateOnDark),
                ) {
                    val vfSize = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
                    val geometry = remember(vfSize, state.shape, state.perforation) {
                        frameGeometry(vfSize, state.shape, state.perforation, density)
                    }
                    val photo = state.photo
                    if (photo != null) {
                        PhotoUnderFrame(
                            photo = photo,
                            quarterTurns = state.quarterTurns,
                            geometry = geometry,
                            onShown = { window -> shown.window = window; shown.shortDp = geometry.shortDp },
                        )
                    }
                    FrameOverlay(geometry, state.shape, state.perforation, Modifier.fillMaxSize())

                    val postmark = rememberPostmarkLines(state.place, state.day)
                    val postmarkPx = with(density) { PostmarkDiameter.toPx() }
                    Postmark(
                        city = postmark.top,
                        date = postmark.bottom,
                        color = c.postmarkOnLight,
                        diameter = PostmarkDiameter,
                        showWaves = false,
                        dashedOuter = true,
                        rotation = PostmarkTilt,
                        modifier = Modifier.offset {
                            IntOffset(
                                (geometry.outer.right - postmarkPx * 0.95f).roundToInt(),
                                (geometry.outer.bottom - postmarkPx * 0.9f).roundToInt(),
                            )
                        },
                    )

                    val note = when {
                        state.storageFull -> stringResource(R.string.storage_full)
                        state.failed -> stringResource(R.string.import_failed)
                        state.loading -> stringResource(R.string.import_loading)
                        state.saving -> stringResource(R.string.import_saving)
                        else -> null
                    }
                    if (note != null) {
                        HandNote(
                            note,
                            color = c.textOnDarkHigh,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(c.ink.copy(alpha = NoteScrimAlpha), StampTheme.shapes.tile)
                                .padding(horizontal = s.m, vertical = s.xs),
                        )
                    } else {
                        HandNote(
                            stringResource(R.string.import_hint),
                            color = c.textOnDarkMedium,
                            strength = InkStrength.Soft,
                            style = StampTheme.type.noteSmall,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = s.s),
                        )
                    }
                }
            }

            ShapePicker(
                selected = state.shape,
                onSelect = onSelectShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = s.screenFullBleed, vertical = s.s),
            )

            // Bottom: another photo · make the stamp · rotate
            Row(
                Modifier
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = s.screen, vertical = s.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (!state.reframing) GalleryStampButton(
                        onClick = onPickAnother,
                        contentDescription = stringResource(R.string.import_pick_another),
                        enabled = !state.saving,
                    )
                }
                ShutterButton(
                    onClick = { shown.window?.let { onMakeStamp(it, shown.shortDp) } },
                    contentDescription = stringResource(R.string.import_make),
                    enabled = ready,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    CircleIconButton(
                        R.drawable.ic_rotate,
                        stringResource(R.string.import_rotate),
                        onRotate,
                        onDark = true,
                    )
                }
            }
        }
        // While the photo becomes a stamp: the shutter is busy, so say so.
        StampingOverlay(visible = state.saving)
    }
}

/**
 * The photo, turned and placed by pinch/drag, filling the viewfinder behind the frame. Reports
 * the frame window as a fraction of the turned photo whenever it changes.
 */
@Composable
private fun PhotoUnderFrame(
    photo: ImageBitmap,
    quarterTurns: Int,
    geometry: FrameGeometry,
    onShown: (NormRect) -> Unit,
) {
    val turned = if (quarterTurns % 2 == 0) {
        Size(photo.width.toFloat(), photo.height.toFloat())
    } else {
        Size(photo.height.toFloat(), photo.width.toFloat())
    }
    // A new turn or frame starts fitted again; a shape change keeps the zoom where it can.
    val fitState = rememberSaveable(photo, quarterTurns, saver = PhotoFit.StateSaver) { mutableStateOf(PhotoFit()) }
    val fit = fitState.value
    val window = geometry.window
    val clamped = remember(fit, window, turned) { fit.clamped(window, turned) }
    SideEffect { onShown(clamped.windowInPhoto(window, turned)) }

    val gestureWindow by rememberUpdatedState(window)
    val gestureFit by rememberUpdatedState(clamped)
    val description = stringResource(R.string.import_photo_cd)
    Canvas(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
            .pointerInput(fitState, turned) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    fitState.value = gestureFit.transformed(gestureWindow, turned, centroid, pan, zoom)
                }
            },
    ) {
        val r: Rect = clamped.photoRect(window, turned)
        // Draw the unturned bitmap centred on r, then turn it about r's centre.
        val w = if (quarterTurns % 2 == 0) r.width else r.height
        val h = if (quarterTurns % 2 == 0) r.height else r.width
        rotate(degrees = quarterTurns * QuarterTurn, pivot = r.center) {
            drawImage(
                photo,
                dstOffset = IntOffset((r.center.x - w / 2f).roundToInt(), (r.center.y - h / 2f).roundToInt()),
                dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

private class ShownWindow {
    var window: NormRect? = null
    var shortDp: Float = 0f
}

private const val ViewfinderAspect = 3f / 4f
private const val PostmarkTilt = -12f
private val PostmarkDiameter = 84.dp
private const val NoteScrimAlpha = 0.7f
private const val QuarterTurn = 90f

@Preview(heightDp = 800)
@Composable
private fun ImportContentPreview() {
    StampAppTheme {
        ImportContent(
            state = ImportUiState(
                loading = false,
                day = LocalDate.of(2026, 10, 12),
                place = Place("Kyoto", "Japan", "JP", null, null),
            ),
            onClose = {},
            onSelectShape = {},
            onRotate = {},
            onPickAnother = {},
            onMakeStamp = { _, _ -> },
        )
    }
}

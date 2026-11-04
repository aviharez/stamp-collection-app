package com.aviharez.stamp.ui.screens.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aviharez.stamp.R
import com.aviharez.stamp.data.image.NormRect
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.ui.components.AirmailStripe
import com.aviharez.stamp.ui.components.CircleIconButton
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.Postmark
import com.aviharez.stamp.ui.components.rememberDateText
import com.aviharez.stamp.ui.components.rememberPostmarkLines
import com.aviharez.stamp.ui.theme.StampTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Camera: permission pre-prompt, then the live stamp camera. */
@Composable
fun CameraScreen(
    onClose: () -> Unit,
    onCaptured: (stampId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LockPortrait()
    val context = LocalContext.current
    val activity = LocalActivity.current
    fun isGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(isGranted()) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        askedOnce = true
    }
    // Back from Settings: pick up a newly granted permission.
    LifecycleResumeEffect(Unit) {
        granted = isGranted()
        onPauseOrDispose { }
    }

    if (granted) {
        CameraContent(onClose = onClose, onCaptured = onCaptured, modifier = modifier)
    } else {
        val permanentlyDenied = askedOnce &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
        CameraPermissionPrompt(
            permanentlyDenied = permanentlyDenied,
            onAllow = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
            onClose = onClose,
            modifier = modifier,
        )
    }
}

@Composable
private fun CameraContent(
    onClose: () -> Unit,
    onCaptured: (stampId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val appContext = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val density = LocalDensity.current
    val s = StampTheme.spacing
    val c = StampTheme.colors

    LaunchedEffect(state.lens, state.highResOn, lifecycleOwner) {
        viewModel.bindCamera(appContext, lifecycleOwner)
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onLocationAnswered(granted)
    }
    // Back from system Settings with location newly allowed: fetch the place.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshPlace()
        onPauseOrDispose { }
    }
    val latestOnCaptured by rememberUpdatedState(onCaptured)
    LaunchedEffect(state.captured) {
        state.captured?.let {
            latestOnCaptured(it)
            viewModel.onCapturedHandled()
        }
    }
    LaunchedEffect(state.problem) {
        if (state.problem == CameraProblem.CaptureFailed) {
            delay(ProblemShownMillis)
            viewModel.dismissProblem()
        }
    }

    val displayRotation = view.display?.rotation ?: Surface.ROTATION_0
    val deviceRotation = relativeToDisplay(rememberDeviceRotation(), displayRotation)
    val iconRotation = rememberIconRotation(deviceRotation)
    // The exact frame shown on screen; the shutter crops to this.
    var shownFrame by remember { mutableStateOf<ShownFrame?>(null) }

    Column(modifier.fillMaxSize().background(c.ink)) {
        AirmailStripe(Modifier.fillMaxWidth().height(s.xs))

        // Header: close · date (city joins in milestone 2) · flash
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = s.screenFullBleed, vertical = s.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                R.drawable.ic_close,
                stringResource(R.string.cd_close),
                onClose,
                onDark = true,
                modifier = Modifier.rotate(iconRotation),
            )
            val today = rememberDateText(R.string.pattern_short_date)
            Text(
                state.place.city?.let { stringResource(R.string.camera_header_place, it, today) } ?: today,
                style = StampTheme.type.albumName,
                color = c.textOnDarkHigh,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = s.xs),
            )
            if (state.lens == Lens.Front) {
                CircleIconButton(
                    icon = if (state.mirrorSelfie) R.drawable.ic_mirror_on else R.drawable.ic_mirror_off,
                    contentDescription = stringResource(
                        if (state.mirrorSelfie) R.string.camera_mirror_on else R.string.camera_mirror_off,
                    ),
                    onClick = viewModel::toggleMirrorSelfie,
                    onDark = true,
                    modifier = Modifier.rotate(iconRotation),
                )
            }
            if (state.hasFlash) {
                CircleIconButton(
                    icon = state.flash.icon,
                    contentDescription = stringResource(state.flash.label),
                    onClick = viewModel::cycleFlash,
                    onDark = true,
                    modifier = Modifier.rotate(iconRotation),
                )
            } else if (state.lens != Lens.Front) {
                Spacer(Modifier.size(s.touchTarget))
            }
        }

        // Viewfinder (3:4, the sensor's native aspect) with the stamp frame.
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
                SideEffect { shownFrame = ShownFrame(vfSize, geometry) }
                val coordinateTransformer = remember { MutableCoordinateTransformer() }
                var focusPoint by remember { mutableStateOf<Offset?>(null) }
                val zoomNow by rememberUpdatedState(state.zoomRatio)
                val focusDescription = stringResource(R.string.camera_focus_cd)
                // CameraX mirrors the front preview; with "mirror selfie" off we flip it back so the
                // preview matches the saved stamp. The frame is centred, so the crop is unaffected.
                val unmirror = state.lens == Lens.Front && !state.mirrorSelfie
                val unmirrorNow by rememberUpdatedState(unmirror)

                surfaceRequest?.let { request ->
                    CameraXViewfinder(
                        surfaceRequest = request,
                        coordinateTransformer = coordinateTransformer,
                        // A SurfaceView ignores graphicsLayer transforms, so un-mirroring needs the
                        // embedded (TextureView-style) viewfinder. Preview.setMirrorMode would be
                        // simpler but is a no-op below API 33.
                        implementationMode = if (unmirror) ImplementationMode.EMBEDDED else ImplementationMode.EXTERNAL,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { scaleX = if (unmirror) -1f else 1f },
                    )
                }
                FrameOverlay(
                    geometry,
                    state.shape,
                    state.perforation,
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = focusDescription }
                        .pointerInput(Unit) {
                            detectTapGestures { tap ->
                                focusPoint = tap
                                // Undo the visual flip before mapping into surface coordinates.
                                val surfaceTap = if (unmirrorNow) tap.copy(x = size.width - tap.x) else tap
                                with(coordinateTransformer) { viewModel.focusAt(surfaceTap.transform()) }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoomChange, _ ->
                                if (zoomChange != 1f) viewModel.setZoom(zoomNow * zoomChange)
                            }
                        },
                )

                // Live postmark on the frame's lower-right corner: city · date, or date-only.
                val postmark = rememberPostmarkLines(state.place)
                // AirmailRed as in design/camera.png: PaperWhite vanishes where it crosses the paper.
                val postmarkDiameter = PostmarkDiameter
                val postmarkPx = with(density) { postmarkDiameter.toPx() }
                Postmark(
                    city = postmark.top,
                    date = postmark.bottom,
                    color = c.postmarkOnLight,
                    diameter = postmarkDiameter,
                    showWaves = false,
                    dashedOuter = true,
                    rotation = PostmarkTilt + iconRotation,
                    modifier = Modifier.offset {
                        IntOffset(
                            (geometry.outer.right - postmarkPx * 0.95f).roundToInt(),
                            (geometry.outer.bottom - postmarkPx * 0.9f).roundToInt(),
                        )
                    },
                )

                focusPoint?.let { FocusRing(it, onDone = { focusPoint = null }) }

                state.exposure?.let { exposure ->
                    ExposureSlider(
                        exposure = exposure,
                        onIndexChange = viewModel::setExposureIndex,
                        iconRotation = iconRotation,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = s.xxs),
                    )
                }

                if (state.askLocation) {
                    LocationAsk(
                        onAllow = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                        onNotNow = { viewModel.onLocationAnswered(granted = false) },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(s.s),
                    )
                } else ZoomChips(
                    zoomRatio = state.zoomRatio,
                    stops = zoomStops(state.minZoom, state.maxZoom),
                    onZoom = viewModel::setZoom,
                    highResSupported = state.highResSupported,
                    highResOn = state.highResOn,
                    onToggleHighRes = viewModel::toggleHighRes,
                    iconRotation = iconRotation,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = s.s),
                )

                state.problem?.let { problem ->
                    HandNote(
                        stringResource(if (problem == CameraProblem.Unavailable) R.string.camera_unavailable else R.string.camera_capture_failed),
                        color = c.textOnDarkHigh,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(c.ink.copy(alpha = ProblemScrimAlpha), StampTheme.shapes.tile)
                            .padding(horizontal = s.m, vertical = s.xs),
                    )
                }

                CaptureFlash(capturing = state.capturing)
            }
        }

        ShapePicker(
            selected = state.shape,
            onSelect = viewModel::selectShape,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = s.screenFullBleed, vertical = s.s),
        )

        // Bottom: (gallery import joins in milestone 2) · shutter · flip
        Row(
            Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = s.screen, vertical = s.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            ShutterButton(
                onClick = {
                    shownFrame?.let { frame ->
                        viewModel.capture(
                            window = frame.normalisedWindow(),
                            viewfinderAspect = frame.viewfinder.width / frame.viewfinder.height,
                            frameShortDp = frame.geometry.shortDp,
                            displayRotation = displayRotation,
                            deviceRotation = deviceRotation,
                        )
                    }
                },
                contentDescription = stringResource(R.string.camera_shutter),
                enabled = !state.capturing && surfaceRequest != null,
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (state.canFlip) {
                    CircleIconButton(
                        R.drawable.ic_flip,
                        stringResource(R.string.camera_flip),
                        viewModel::flipLens,
                        onDark = true,
                        modifier = Modifier.rotate(iconRotation),
                    )
                }
            }
        }
    }
}

/** Viewfinder size + frame geometry as last laid out (px). */
private data class ShownFrame(val viewfinder: Size, val geometry: FrameGeometry) {
    fun normalisedWindow() = NormRect(
        geometry.window.left / viewfinder.width,
        geometry.window.top / viewfinder.height,
        geometry.window.right / viewfinder.width,
        geometry.window.bottom / viewfinder.height,
    )
}

@Composable
private fun FocusRing(at: Offset, onDone: () -> Unit) {
    val c = StampTheme.colors
    val scale = remember(at) { Animatable(FocusStartScale) }
    LaunchedEffect(at) {
        scale.animateTo(1f, tween(FocusSettleMillis))
        delay(FocusHoldMillis)
        onDone()
    }
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            c.paperWhite,
            radius = FocusRadius.toPx() * scale.value,
            center = at,
            style = Stroke(1.5.dp.toPx()),
        )
    }
}

@Composable
private fun CaptureFlash(capturing: Boolean) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(capturing) {
        if (capturing) {
            alpha.snapTo(FlashAlpha)
            alpha.animateTo(0f, tween(FlashMillis))
        }
    }
    val c = StampTheme.colors
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }.background(c.paperWhite))
}

private val FlashSetting.icon: Int
    get() = when (this) {
        FlashSetting.Off -> R.drawable.ic_flash_off
        FlashSetting.Auto -> R.drawable.ic_flash_auto
        FlashSetting.On -> R.drawable.ic_flash_on
    }

private val FlashSetting.label: Int
    get() = when (this) {
        FlashSetting.Off -> R.string.camera_flash_off
        FlashSetting.Auto -> R.string.camera_flash_auto
        FlashSetting.On -> R.string.camera_flash_on
    }

private const val ViewfinderAspect = 3f / 4f
private const val PostmarkTilt = -12f
private val PostmarkDiameter = 84.dp
private val FocusRadius = 32.dp
private const val FocusStartScale = 1.4f
private const val FocusSettleMillis = 220
private const val FocusHoldMillis = 700L
private const val FlashAlpha = 0.7f
private const val FlashMillis = 260
private const val ProblemShownMillis = 2500L
private const val ProblemScrimAlpha = 0.7f

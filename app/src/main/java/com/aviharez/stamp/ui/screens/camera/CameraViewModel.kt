package com.aviharez.stamp.ui.screens.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aviharez.stamp.data.image.CropSpec
import com.aviharez.stamp.data.image.NormRect
import com.aviharez.stamp.data.image.StampRenderer
import com.aviharez.stamp.data.image.StampStorage
import com.aviharez.stamp.data.location.PlaceProvider
import com.aviharez.stamp.data.repo.NewStamp
import com.aviharez.stamp.data.repo.StampRepository
import com.aviharez.stamp.data.settings.SettingsRepository
import com.aviharez.stamp.domain.model.Place
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.domain.model.StampSource
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.StampShape
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

enum class Lens { Back, Front }

enum class FlashSetting(val cameraXMode: Int) {
    Off(ImageCapture.FLASH_MODE_OFF),
    Auto(ImageCapture.FLASH_MODE_AUTO),
    On(ImageCapture.FLASH_MODE_ON),
    ;

    fun next(): FlashSetting = entries[(ordinal + 1) % entries.size]
}

/** Exposure compensation as exposed by the camera: integer index × step (EV). */
data class ExposureUi(val index: Int, val min: Int, val max: Int, val stepEv: Float) {
    val ev: Float get() = index * stepEv
}

/** A finished capture, ready for the Stamped! screen. */

enum class CameraProblem { Unavailable, CaptureFailed }

data class CameraUiState(
    val shape: StampShape = StampShape.Portrait,
    val perforation: PerforationStyle = PerforationStyle.Classic,
    val lens: Lens = Lens.Back,
    val canFlip: Boolean = false,
    val zoomRatio: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val exposure: ExposureUi? = null,
    val flash: FlashSetting = FlashSetting.Off,
    val hasFlash: Boolean = false,
    val highResSupported: Boolean = false,
    val highResOn: Boolean = false,
    /** Front camera only: true saves the selfie as the (mirrored) preview shows it. */
    val mirrorSelfie: Boolean = true,
    /** Postmark place; [Place.Unknown] → date-only. */
    val place: Place = Place.Unknown,
    /** Show the one-time "put your city on the postmark?" card. */
    val askLocation: Boolean = false,
    val capturing: Boolean = false,
    /** Id of the stamp just saved; the screen navigates to Stamped! with it. */
    val captured: String? = null,
    val problem: CameraProblem? = null,
    /** Newest saved stamp, shown on the gallery button. */
    val latest: Stamp? = null,
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val storage: StampStorage,
    private val renderer: StampRenderer,
    private val stamps: StampRepository,
    private val settings: SettingsRepository,
    private val places: PlaceProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            stamps.observeRecent(1).collect { list -> _state.update { it.copy(latest = list.firstOrNull()) } }
        }
        viewModelScope.launch {
            settings.mirrorSelfie.collect { on -> _state.update { it.copy(mirrorSelfie = on) } }
        }
        viewModelScope.launch {
            if (places.hasPermission()) refreshPlace()
            else if (!settings.locationAsked()) _state.update { it.copy(askLocation = true) }
        }
    }

    private var placeJob: Deferred<Place>? = null

    /** Looks up the postmark place in the background (cheap when cached). */
    fun refreshPlace() {
        if (!places.hasPermission() || placeJob?.isActive == true) return
        placeJob = viewModelScope.async {
            places.current().also { place -> _state.update { it.copy(place = place) } }
        }
    }

    /** Result of the location ask (system dialog answered, or "Not now"). */
    fun onLocationAnswered(granted: Boolean) {
        _state.update { it.copy(askLocation = false) }
        viewModelScope.launch { settings.setLocationAsked() }
        if (granted) refreshPlace()
    }

    /** Place for a capture: the known one, or a short wait for a lookup still in flight. */
    private suspend fun placeForCapture(): Place {
        val known = _state.value.place
        if (known.hasName) return known
        return placeJob?.let { withTimeoutOrNull(PlaceWaitMillis) { it.await() } } ?: known
    }

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null

    /**
     * Binds preview + capture to [lifecycleOwner] and suspends until cancelled (when the lens or
     * resolution mode changes, or the screen leaves composition), then unbinds.
     */
    suspend fun bindCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(appContext)
        val current = _state.value
        val selector = if (current.lens == Lens.Front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

        val canFlip = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) &&
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        if (!provider.hasCamera(selector)) {
            _state.update { it.copy(problem = CameraProblem.Unavailable, canFlip = canFlip) }
            return
        }

        val preview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(FourByThree).build())
            .build()
            .apply { setSurfaceProvider { request -> _surfaceRequest.value = request } }

        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(current.flash.cameraXMode)
            .setResolutionSelector(captureResolution(highRes = current.highResOn))
            .build()

        provider.unbindAll()
        val bound = try {
            provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                UseCaseGroup.Builder().addUseCase(preview).addUseCase(capture).build(),
            )
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Bind failed", e)
            _state.update { it.copy(problem = CameraProblem.Unavailable) }
            return
        }
        camera = bound
        imageCapture = capture
        readCapabilities(bound.cameraInfo, canFlip)

        try {
            awaitCancellation()
        } finally {
            provider.unbindAll()
            camera = null
            imageCapture = null
            _surfaceRequest.value = null
        }
    }

    private fun readCapabilities(info: CameraInfo, canFlip: Boolean) {
        val zoom = info.zoomState.value
        val exposure = info.exposureState
        _state.update {
            it.copy(
                canFlip = canFlip,
                minZoom = zoom?.minZoomRatio ?: 1f,
                maxZoom = zoom?.maxZoomRatio ?: 1f,
                zoomRatio = zoom?.zoomRatio ?: 1f,
                hasFlash = info.hasFlashUnit(),
                exposure = if (exposure.isExposureCompensationSupported) {
                    ExposureUi(
                        index = exposure.exposureCompensationIndex,
                        min = exposure.exposureCompensationRange.lower,
                        max = exposure.exposureCompensationRange.upper,
                        stepEv = exposure.exposureCompensationStep.toFloat(),
                    )
                } else {
                    null
                },
                highResSupported = supportsHighRes(info),
                problem = null,
            )
        }
    }

    /** True when the camera exposes a ≥40 MP JPEG size (shown as the "50MP" chip). */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun supportsHighRes(info: CameraInfo): Boolean {
        val map = Camera2CameraInfo.from(info)
            .getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return false
        val sizes = map.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty() +
            map.getHighResolutionOutputSizes(ImageFormat.JPEG)?.toList().orEmpty()
        return sizes.any { it.width.toLong() * it.height >= HighResMinPixels }
    }

    fun selectShape(shape: StampShape) = _state.update { it.copy(shape = shape) }

    fun setZoom(ratio: Float) {
        val s = _state.value
        val clamped = ratio.coerceIn(s.minZoom, s.maxZoom)
        camera?.cameraControl?.setZoomRatio(clamped)
        _state.update { it.copy(zoomRatio = clamped) }
    }

    fun setExposureIndex(index: Int) {
        val exposure = _state.value.exposure ?: return
        val clamped = index.coerceIn(exposure.min, exposure.max)
        if (clamped == exposure.index) return
        camera?.cameraControl?.setExposureCompensationIndex(clamped)
        _state.update { it.copy(exposure = exposure.copy(index = clamped)) }
    }

    fun cycleFlash() {
        val next = _state.value.flash.next()
        imageCapture?.flashMode = next.cameraXMode
        _state.update { it.copy(flash = next) }
    }

    /** Flipping or switching resolution mode rebinds (the screen re-runs [bindCamera]). */
    fun flipLens() = _state.update {
        it.copy(lens = if (it.lens == Lens.Back) Lens.Front else Lens.Back, zoomRatio = 1f, exposure = null, highResOn = false)
    }

    fun toggleHighRes() = _state.update { it.copy(highResOn = !it.highResOn) }

    fun toggleMirrorSelfie() {
        val next = !_state.value.mirrorSelfie
        _state.update { it.copy(mirrorSelfie = next) }
        viewModelScope.launch { settings.setMirrorSelfie(next) }
    }

    /** Tap-to-focus at [surfacePoint] (already transformed into SurfaceRequest coordinates). */
    fun focusAt(surfacePoint: Offset) {
        val request = _surfaceRequest.value ?: return
        val factory = SurfaceOrientedMeteringPointFactory(
            request.resolution.width.toFloat(),
            request.resolution.height.toFloat(),
        )
        val point = factory.createPoint(surfacePoint.x, surfacePoint.y)
        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
    }

    /**
     * Captures at full quality, then renders the stamp from what was inside the frame.
     *
     * @param window picture window normalised to the viewfinder box.
     * @param displayRotation Surface.ROTATION_* of the (portrait-locked) display.
     * @param deviceRotation physical rotation of the phone (0/90/180/270, clockwise).
     */
    fun capture(
        window: NormRect,
        viewfinderAspect: Float,
        frameShortDp: Float,
        displayRotation: Int,
        deviceRotation: Int,
    ) {
        val capture = imageCapture ?: return
        if (_state.value.capturing) return
        val snapshot = _state.value
        _state.update { it.copy(capturing = true, problem = null) }

        viewModelScope.launch {
            val id = storage.newId()
            val original = storage.originalFile(id)
            try {
                capture.targetRotation = displayRotation
                capture.takePictureTo(original, mirror = snapshot.lens == Lens.Front && snapshot.mirrorSelfie)
                val rendered = withContext(Dispatchers.Default) {
                    renderer.render(
                        original = original,
                        spec = CropSpec(
                            window = window,
                            viewfinderAspect = viewfinderAspect,
                            shape = snapshot.shape,
                            perforation = snapshot.perforation,
                            frameShortDp = frameShortDp,
                            deviceRotation = deviceRotation,
                        ),
                        output = storage.renderedFile(id),
                    )
                }
                val stampId = stamps.add(
                    NewStamp(
                        id = id,
                        shape = rendered.shape,
                        perforation = snapshot.perforation,
                        imageFile = rendered.file,
                        originalFile = original,
                        place = placeForCapture(),
                        source = StampSource.Camera,
                    ),
                )
                _state.update { it.copy(capturing = false, captured = stampId) }
            } catch (e: ImageCaptureException) {
                Log.w(TAG, "Capture failed", e)
                _state.update { it.copy(capturing = false, problem = CameraProblem.CaptureFailed) }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Render failed", e)
                _state.update { it.copy(capturing = false, problem = CameraProblem.CaptureFailed) }
            }
        }
    }

    /** Call after navigating to Stamped! so the event is not handled twice. */
    fun onCapturedHandled() = _state.update { it.copy(captured = null) }

    fun dismissProblem() = _state.update { it.copy(problem = null) }

    private suspend fun ImageCapture.takePictureTo(file: File, mirror: Boolean) =
        suspendCancellableCoroutine { cont ->
            val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = mirror }
            val options = ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build()
            takePicture(
                options,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) = cont.resume(Unit)
                    override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
                },
            )
        }

    override fun onCleared() {
        cameraExecutor.shutdown()
    }

    private companion object {
        const val TAG = "CameraViewModel"
        const val HighResMinPixels = 40_000_000L
        /** How long a capture waits for a place lookup still running before going date-only. */
        const val PlaceWaitMillis = 1_500L
        val FourByThree: AspectRatioStrategy = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY

        fun captureResolution(highRes: Boolean): ResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(FourByThree)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
            .apply {
                if (highRes) setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
            }
            .build()
    }
}

/** Exposure value label, e.g. "−0.3", "0.0", "+1.0" (true minus sign). */
fun formatEv(ev: Float): String {
    val tenths = (ev * 10).roundToInt()
    val sign = when {
        tenths > 0 -> "+"
        tenths < 0 -> "−"
        else -> ""
    }
    val abs = kotlin.math.abs(tenths)
    return "$sign${abs / 10}.${abs % 10}"
}

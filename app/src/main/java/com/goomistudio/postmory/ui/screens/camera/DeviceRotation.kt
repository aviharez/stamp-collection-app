package com.goomistudio.postmory.ui.screens.camera

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.view.Surface
import android.view.OrientationEventListener
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.goomistudio.postmory.ui.theme.airmailSpring
import com.goomistudio.postmory.ui.theme.rememberReduceMotion
import kotlin.math.abs

/**
 * Physical rotation of the phone, snapped to 0/90/180/270 (clockwise, 90 = left side up), with
 * hysteresis so it does not flicker around 45°. Used to make sideways photos come out upright.
 */
@Composable
fun rememberDeviceRotation(): Int {
    val context = LocalContext.current
    var rotation by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val candidate = ((orientation + 45) / 90 * 90) % 360
                if (candidate != rotation && angularDistance(orientation, candidate) < SnapWithinDegrees) {
                    rotation = candidate
                }
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    return rotation
}

private fun angularDistance(a: Int, b: Int): Int {
    val d = abs(a - b) % 360
    return if (d > 180) 360 - d else d
}

private const val SnapWithinDegrees = 35

/** Animated counter-rotation for icons so they stay upright; always turns the short way. */
@Composable
fun rememberIconRotation(deviceRotation: Int): Float {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(deviceRotation) {
        val desired = -deviceRotation.toFloat()
        val delta = ((desired - target) % 360f + 540f) % 360f - 180f
        target += delta
    }
    val animated by animateFloatAsState(target, airmailSpring(rememberReduceMotion()), label = "iconRotation")
    return animated
}

/**
 * Keeps the camera UI portrait (like the system camera) while it is on screen. Large screens on
 * Android 16+ may ignore the lock; capture stays correct because rotations are measured relative
 * to the current display (see [relativeToDisplay]).
 */
@SuppressLint("SourceLockedOrientationActivity") // deliberate: camera UI is designed portrait-only
@Composable
fun LockPortrait() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity.requestedOrientation = previous }
    }
}

/**
 * Device rotation measured from the current display orientation (both clockwise). On phones the
 * display stays at ROTATION_0 so this is the device rotation itself; when the UI did rotate, the
 * remaining difference is what the photo still needs.
 */
fun relativeToDisplay(deviceRotation: Int, displayRotation: Int): Int {
    val displayDegrees = when (displayRotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }
    return (deviceRotation + displayDegrees) % 360
}

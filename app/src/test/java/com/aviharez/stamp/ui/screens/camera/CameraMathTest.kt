package com.aviharez.stamp.ui.screens.camera

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CameraMathTest {
    @Test
    fun portraitLockedPhone_keepsDeviceRotation() {
        assertEquals(90, relativeToDisplay(deviceRotation = 90, displayRotation = Surface.ROTATION_0))
        assertEquals(270, relativeToDisplay(deviceRotation = 270, displayRotation = Surface.ROTATION_0))
    }

    @Test
    fun uiFollowedTheTurn_needsNoExtraRotation() {
        // Turning the phone clockwise (left side up) makes the display ROTATION_270.
        assertEquals(0, relativeToDisplay(deviceRotation = 90, displayRotation = Surface.ROTATION_270))
        assertEquals(0, relativeToDisplay(deviceRotation = 270, displayRotation = Surface.ROTATION_90))
    }

    @Test
    fun evLabel_usesTrueMinusAndOneDecimal() {
        assertEquals("−0.3", formatEv(-1f / 3f))
        assertEquals("0.0", formatEv(0f))
        assertEquals("+1.0", formatEv(1f))
    }

    @Test
    fun zoomStops_hideUltraWideAndTwoTimesWhenUnsupported() {
        assertEquals(listOf(1f), zoomStops(minZoom = 1f, maxZoom = 1.5f))
        assertEquals(listOf(0.5f, 1f, 2f), zoomStops(minZoom = 0.5f, maxZoom = 8f))
    }
}

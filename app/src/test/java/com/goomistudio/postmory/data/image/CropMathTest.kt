package com.goomistudio.postmory.data.image

import com.goomistudio.postmory.domain.model.StampShape
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CropMathTest {
    private val full = NormRect(0f, 0f, 1f, 1f)
    private val eps = 1e-5f

    @Test
    fun sameAspect_isIdentity() {
        val w = NormRect(0.1f, 0.2f, 0.8f, 0.9f)
        assertEquals(w, viewfinderToPhoto(w, viewfinderAspect = 0.75f, photoAspect = 0.75f))
    }

    @Test
    fun widerPhoto_cropsSides() {
        // 3:4 viewfinder, square photo: only the middle 75% of the width is visible.
        val r = viewfinderToPhoto(full, viewfinderAspect = 0.75f, photoAspect = 1f)
        assertEquals(0.125f, r.left, eps)
        assertEquals(0.875f, r.right, eps)
        assertEquals(0f, r.top, eps)
        assertEquals(1f, r.bottom, eps)
    }

    @Test
    fun tallerPhoto_cropsTopAndBottom() {
        // 3:4 viewfinder, 9:16 photo: visible height fraction = (9/16)/(3/4) = 0.75.
        val r = viewfinderToPhoto(full, viewfinderAspect = 0.75f, photoAspect = 9f / 16f)
        assertEquals(0.125f, r.top, eps)
        assertEquals(0.875f, r.bottom, eps)
        assertEquals(0f, r.left, eps)
        assertEquals(1f, r.right, eps)
    }

    @Test
    fun quarterTurn_swapsPortraitAndLandscapeOnly() {
        assertEquals(StampShape.Landscape, StampShape.Portrait.quarterTurned())
        assertEquals(StampShape.Portrait, StampShape.Landscape.quarterTurned())
        assertEquals(StampShape.Square, StampShape.Square.quarterTurned())
        assertEquals(StampShape.Circle, StampShape.Circle.quarterTurned())
    }
}

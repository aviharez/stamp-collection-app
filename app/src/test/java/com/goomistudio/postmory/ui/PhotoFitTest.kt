package com.goomistudio.postmory.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.goomistudio.postmory.ui.screens.importer.PhotoFit
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoFitTest {
    private val window = Rect(100f, 100f, 400f, 500f) // 300 × 400 portrait frame
    private val landscape = Size(4000f, 3000f)

    @Test fun startsCentredAndCoveringTheWindow() {
        val r = PhotoFit().windowInPhoto(window, landscape)
        // Photo scaled to the window's height: 300 wide of 533 → centred slice.
        assertEquals(400f / 3000f * 4000f, PhotoFit().photoRect(window, landscape).width, 0.01f)
        assertEquals(0f, r.top, 1e-4f)
        assertEquals(1f, r.bottom, 1e-4f)
        assertEquals(0.5f, (r.left + r.right) / 2f, 1e-4f)
        assertEquals(300f / 533.333f, r.width, 1e-3f)
    }

    @Test fun panIsClampedToTheEdges() {
        val fit = PhotoFit().transformed(window, landscape, window.center, Offset(10_000f, 10_000f), 1f)
        val r = fit.windowInPhoto(window, landscape)
        assertEquals(0f, r.left, 1e-4f)
        assertEquals(0f, r.top, 1e-4f)
    }

    @Test fun zoomKeepsThePointUnderTheFingers() {
        val centroid = Offset(150f, 150f)
        val before = PhotoFit().photoRect(window, landscape)
        val fit = PhotoFit().transformed(window, landscape, centroid, Offset.Zero, 2f)
        val after = fit.photoRect(window, landscape)
        val u = (centroid.x - before.left) / before.width
        val v = (centroid.y - before.top) / before.height
        assertEquals(u, (centroid.x - after.left) / after.width, 1e-4f)
        assertEquals(v, (centroid.y - after.top) / after.height, 1e-4f)
    }

    @Test fun zoomStaysWithinLimits() {
        assertEquals(1f, PhotoFit().transformed(window, landscape, window.center, Offset.Zero, 0.2f).zoom)
        assertEquals(PhotoFit.MaxZoom, PhotoFit().transformed(window, landscape, window.center, Offset.Zero, 50f).zoom)
    }

    @Test fun fittedPhotoNeverBreaksTheClamp() {
        // At zoom 1 one side fits exactly; float rounding used to leave a negative slack and crash.
        val photo = Size(2400f, 1566f)
        repeat(2000) { i ->
            val w = 200f + i * 0.37f
            val frame = Rect(24f, 80f, 24f + w, 80f + w * 4f / 3f)
            val pan = PhotoFit(pan = Offset(5f, -5f)).clamped(frame, photo).pan
            assertEquals(5f, pan.x, 1e-3f) // the wide photo has room sideways
            assertEquals(0f, pan.y, 1e-3f) // and none up or down
        }
    }
}

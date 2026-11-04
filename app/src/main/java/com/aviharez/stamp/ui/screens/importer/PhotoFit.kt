package com.aviharez.stamp.ui.screens.importer

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.aviharez.stamp.data.image.NormRect
import kotlin.math.max

/**
 * How a picked photo sits behind the stamp frame on the import screen. At [zoom] 1 the photo
 * just covers the frame's [window]; [pan] moves its centre away from the window's centre (px).
 * The photo always covers the window, so the stamp never shows an empty edge.
 */
data class PhotoFit(val zoom: Float = 1f, val pan: Offset = Offset.Zero) {

    /** Where the (already turned) photo of [photoSize] is drawn, in viewfinder px. */
    fun photoRect(window: Rect, photoSize: Size): Rect {
        val base = max(window.width / photoSize.width, window.height / photoSize.height)
        val w = photoSize.width * base * zoom
        val h = photoSize.height * base * zoom
        val centre = window.center + pan
        return Rect(centre.x - w / 2f, centre.y - h / 2f, centre.x + w / 2f, centre.y + h / 2f)
    }

    /** The frame's window as a fraction of the turned photo: what the stamp will show. */
    fun windowInPhoto(window: Rect, photoSize: Size): NormRect {
        val r = photoRect(window, photoSize)
        return NormRect(
            left = ((window.left - r.left) / r.width).coerceIn(0f, 1f),
            top = ((window.top - r.top) / r.height).coerceIn(0f, 1f),
            right = ((window.right - r.left) / r.width).coerceIn(0f, 1f),
            bottom = ((window.bottom - r.top) / r.height).coerceIn(0f, 1f),
        )
    }

    /**
     * One pinch/drag step: zooms by [zoomChange] around [centroid] (the point under the fingers
     * stays put), moves by [panChange], then keeps the window covered.
     */
    fun transformed(window: Rect, photoSize: Size, centroid: Offset, panChange: Offset, zoomChange: Float): PhotoFit {
        val newZoom = (zoom * zoomChange).coerceIn(1f, MaxZoom)
        val k = newZoom / zoom
        val centre = window.center + pan
        val newCentre = centroid + (centre - centroid) * k + panChange
        return PhotoFit(newZoom, newCentre - window.center).clamped(window, photoSize)
    }

    /** Pulls [pan] back so the photo still covers the whole window. */
    fun clamped(window: Rect, photoSize: Size): PhotoFit {
        val r = PhotoFit(zoom).photoRect(window, photoSize)
        val maxX = (r.width - window.width) / 2f
        val maxY = (r.height - window.height) / 2f
        return copy(pan = Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY)))
    }

    companion object {
        const val MaxZoom = 5f

        /** Keeps the pinch/drag through Android closing the app in the background. */
        val StateSaver: Saver<MutableState<PhotoFit>, FloatArray> = Saver(
            save = { floatArrayOf(it.value.zoom, it.value.pan.x, it.value.pan.y) },
            restore = { mutableStateOf(PhotoFit(it[0], Offset(it[1], it[2]))) },
        )
    }
}

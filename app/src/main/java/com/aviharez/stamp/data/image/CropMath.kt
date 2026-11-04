package com.aviharez.stamp.data.image

/** Axis-aligned rectangle with normalised (0..1) coordinates. */
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * Maps a rectangle normalised to the viewfinder into the same rectangle normalised to the
 * upright photo, assuming the preview fills the viewfinder centre-cropped (ContentScale.Crop /
 * FILL_CENTER) and shows the same field of view as the captured photo.
 */
fun viewfinderToPhoto(window: NormRect, viewfinderAspect: Float, photoAspect: Float): NormRect {
    fun mapX(x: Float) = if (photoAspect > viewfinderAspect) 0.5f + (x - 0.5f) * (viewfinderAspect / photoAspect) else x
    fun mapY(y: Float) = if (photoAspect < viewfinderAspect) 0.5f + (y - 0.5f) * (photoAspect / viewfinderAspect) else y
    return NormRect(
        left = mapX(window.left).coerceIn(0f, 1f),
        top = mapY(window.top).coerceIn(0f, 1f),
        right = mapX(window.right).coerceIn(0f, 1f),
        bottom = mapY(window.bottom).coerceIn(0f, 1f),
    )
}

package com.goomistudio.postmory.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** EXIF orientation of [file] (ORIENTATION_NORMAL when missing or unreadable). */
internal fun exifOrientation(file: File): Int = runCatching {
    ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

/** Width × height of [file] once its EXIF orientation is applied (0 × 0 if it isn't an image). */
internal fun uprightSize(file: File): Pair<Int, Int> {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    return if (exifOrientation(file) in SwapAxes) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
}

/** Maps raw pixels to upright pixels for all 8 EXIF orientations (incl. mirrors). Recycles the source. */
internal fun Bitmap.applyExif(orientation: Int): Bitmap {
    val m = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> m.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> { m.setRotate(180f); m.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSPOSE -> { m.setRotate(90f); m.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> m.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> m.setRotate(-90f)
        else -> return this
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true).also { if (it !== this) recycle() }
}

private val SwapAxes = setOf(
    ExifInterface.ORIENTATION_TRANSPOSE,
    ExifInterface.ORIENTATION_ROTATE_90,
    ExifInterface.ORIENTATION_TRANSVERSE,
    ExifInterface.ORIENTATION_ROTATE_270,
)

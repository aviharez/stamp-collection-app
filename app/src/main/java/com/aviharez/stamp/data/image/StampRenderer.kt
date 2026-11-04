package com.aviharez.stamp.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import androidx.exifinterface.media.ExifInterface
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.ui.components.PerforationCornerDp
import com.aviharez.stamp.ui.components.perforatedPath
import com.aviharez.stamp.ui.theme.PaperWhite
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What the user framed, in the coordinates of the upright (EXIF-applied) photo.
 *
 * @param window the clear picture area inside the perforated frame, normalised 0..1 to the
 *   viewfinder box.
 * @param viewfinderAspect width / height of the viewfinder box (preview is centre-cropped in it).
 * @param frameShortDp short side of the on-screen stamp frame (outer paper edge) in dp; export
 *   scales margins and perforations from it so the PNG is a replica of what was shown.
 * @param deviceRotation how far the phone was turned (0/90/180/270, clockwise) when the shutter
 *   fired; the output is turned back so it is upright in the world.
 */
data class CropSpec(
    val window: NormRect,
    val viewfinderAspect: Float,
    val shape: StampShape,
    val perforation: PerforationStyle,
    val frameShortDp: Float,
    val deviceRotation: Int,
)

/** Result of rendering: the PNG and the shape it ended up as (after the device rotation). */
data class RenderedStamp(val file: File, val shape: StampShape)

/**
 * Turns a captured photo into a stamp PNG: EXIF orientation → crop to the frame window →
 * rotate upright → PaperWhite perforated paper, transparent outside. Long side 1600–2400px.
 * Runs on a background thread (blocking).
 */
@Singleton
class StampRenderer @Inject constructor() {

    fun render(original: File, spec: CropSpec, output: File): RenderedStamp {
        val exifOrientation = ExifInterface(original).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(original.path, bounds)
        val swapsAxes = exifOrientation in SwapAxes
        val uprightW = if (swapsAxes) bounds.outHeight else bounds.outWidth
        val uprightH = if (swapsAxes) bounds.outWidth else bounds.outHeight

        val window = viewfinderToPhoto(spec.window, spec.viewfinderAspect, uprightW.toFloat() / uprightH)

        // Sample down only as far as the crop still delivers MaxLongPx.
        val cropLongPx = max(window.width * uprightW, window.height * uprightH)
        var sample = 1
        while (cropLongPx / (sample * 2) >= MaxLongPx) sample *= 2

        val decoded = BitmapFactory.decodeFile(original.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Could not decode ${original.name}")
        val upright = applyExif(decoded, exifOrientation)

        val cropped = crop(upright, window)
        val photo = rotate(cropped, spec.deviceRotation)
        val shape = if (spec.deviceRotation % 180 != 0) spec.shape.quarterTurned() else spec.shape

        val stamp = drawStamp(photo, shape, spec)
        photo.recycle()
        output.outputStream().use { stamp.compress(Bitmap.CompressFormat.PNG, PngQuality, it) }
        stamp.recycle()
        return RenderedStamp(output, shape)
    }

    /** Maps raw sensor pixels to upright pixels for all 8 EXIF orientations (incl. mirrors). */
    private fun applyExif(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { m.setRotate(180f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.setRotate(-90f)
            else -> return src
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true).also { if (it !== src) src.recycle() }
    }

    private fun crop(src: Bitmap, window: NormRect): Bitmap {
        val left = (window.left * src.width).roundToInt().coerceIn(0, src.width - 1)
        val top = (window.top * src.height).roundToInt().coerceIn(0, src.height - 1)
        val right = (window.right * src.width).roundToInt().coerceIn(left + 1, src.width)
        val bottom = (window.bottom * src.height).roundToInt().coerceIn(top + 1, src.height)
        return Bitmap.createBitmap(src, left, top, right - left, bottom - top).also { if (it !== src) src.recycle() }
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src
        val m = Matrix().apply { setRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true).also { if (it !== src) src.recycle() }
    }

    private fun drawStamp(photo: Bitmap, shape: StampShape, spec: CropSpec): Bitmap {
        // Outer paper size: the photo plus the paper margin, at the frame's proportions.
        val marginRatio = spec.perforation.paperMarginDp / spec.frameShortDp
        val photoShort = min(photo.width, photo.height).toFloat()
        val outerShortFromPhoto = photoShort / (1f - 2f * marginRatio)
        val outerLongFromPhoto = outerShortFromPhoto / min(shape.aspectRatio, 1f / shape.aspectRatio)
        val outerLong = outerLongFromPhoto.coerceIn(MinLongPx.toFloat(), MaxLongPx.toFloat())
        val outerW = (if (shape.aspectRatio >= 1f) outerLong else outerLong * shape.aspectRatio).roundToInt()
        val outerH = (if (shape.aspectRatio >= 1f) outerLong / shape.aspectRatio else outerLong).roundToInt()

        val pxPerDp = min(outerW, outerH) / spec.frameShortDp
        val margin = spec.perforation.paperMarginDp * pxPerDp

        val out = createBitmap(outerW, outerH) // ARGB_8888, transparent
        val canvas = Canvas(out)
        val paperPath = perforatedPath(
            size = Size(outerW.toFloat(), outerH.toFloat()),
            stampShape = shape,
            holeRadius = spec.perforation.holeRadiusDp * pxPerDp,
            pitch = spec.perforation.pitchDp * pxPerDp,
            cornerRadius = PerforationCornerDp * pxPerDp,
        ).asAndroidPath()
        canvas.drawPath(paperPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PaperWhite.toArgb() })

        val inner = RectF(margin, margin, outerW - margin, outerH - margin)
        canvas.withSave {
            val clip = Path().apply {
                if (shape == StampShape.Circle) addOval(inner, Path.Direction.CW) else addRect(inner, Path.Direction.CW)
            }
            clipPath(clip)
            // Centre-crop the photo into the window (aspects already match up to rounding).
            val scale = max(inner.width() / photo.width, inner.height() / photo.height)
            val dw = photo.width * scale
            val dh = photo.height * scale
            val dst = RectF(inner.centerX() - dw / 2f, inner.centerY() - dh / 2f, inner.centerX() + dw / 2f, inner.centerY() + dh / 2f)
            drawBitmap(photo, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        return out
    }

    private companion object {
        const val MinLongPx = 1600
        const val MaxLongPx = 2400
        const val PngQuality = 100
        val SwapAxes = setOf(
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
        )
    }
}

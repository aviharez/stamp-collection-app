package com.goomistudio.postmory.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.goomistudio.postmory.domain.model.PerforationStyle
import com.goomistudio.postmory.domain.model.StampFilter
import com.goomistudio.postmory.domain.model.StampShape
import com.goomistudio.postmory.ui.components.PerforationCornerDp
import com.goomistudio.postmory.ui.components.colorMatrix
import com.goomistudio.postmory.ui.components.perforatedPath
import com.goomistudio.postmory.ui.theme.PaperWhite
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

/**
 * How a stamp's picture was cut from its original, so it can be drawn again (another filter)
 * without asking the user to frame it again.
 *
 * @param window crop of the upright photo after it was turned [turnBefore]° (normalised 0..1).
 * @param turnAfter degrees the crop was turned afterwards (camera: the phone was sideways).
 * @param frameShortDp short side of the on-screen frame the margins and perforations scale from.
 */
data class RenderSpec(
    val window: NormRect,
    val turnBefore: Int,
    val turnAfter: Int,
    val frameShortDp: Float,
) {
    /** "left,top,right,bottom,turnBefore,turnAfter,frameShortDp" for the database and backups. */
    fun encode(): String = listOf(window.left, window.top, window.right, window.bottom, turnBefore, turnAfter, frameShortDp)
        .joinToString(",")

    companion object {
        private const val Parts = 7

        fun decode(text: String?): RenderSpec? {
            val parts = text?.split(",")?.takeIf { it.size == Parts } ?: return null
            val n = parts.map { it.toFloatOrNull() ?: return null }
            val window = NormRect(n[0], n[1], n[2], n[3])
            if (window.width <= 0f || window.height <= 0f || n[6] <= 0f) return null
            return RenderSpec(window, n[4].toInt(), n[5].toInt(), n[6])
        }
    }
}

/** Result of rendering: the PNG, the shape it ended up as (after the device rotation) and how to redo it. */
data class RenderedStamp(val file: File, val shape: StampShape, val spec: RenderSpec)

/**
 * Turns a captured photo into a stamp PNG: EXIF orientation → crop to the frame window →
 * rotate upright → PaperWhite perforated paper, transparent outside. Long side 1600–2400px.
 * Runs on a background thread (blocking).
 */
@Singleton
class StampRenderer @Inject constructor() {

    /** Camera capture: [CropSpec.window] is in viewfinder coordinates. */
    fun render(original: File, spec: CropSpec, output: File): RenderedStamp {
        val (uprightW, uprightH) = uprightSize(original)
        val window = viewfinderToPhoto(spec.window, spec.viewfinderAspect, uprightW.toFloat() / uprightH)
        return renderWindow(
            original = original,
            spec = RenderSpec(window, turnBefore = 0, turnAfter = spec.deviceRotation, frameShortDp = spec.frameShortDp),
            shape = spec.shape,
            perforation = spec.perforation,
            filter = StampFilter.Original,
            output = output,
        )
    }

    /**
     * Gallery import: the photo is turned [quarterTurns] × 90° clockwise first, and [window] is
     * normalised to that turned, upright photo.
     */
    fun renderImport(
        original: File,
        window: NormRect,
        quarterTurns: Int,
        shape: StampShape,
        perforation: PerforationStyle,
        frameShortDp: Float,
        filter: StampFilter,
        output: File,
    ): RenderedStamp = renderWindow(
        original = original,
        spec = RenderSpec(window, turnBefore = Math.floorMod(quarterTurns, 4) * 90, turnAfter = 0, frameShortDp = frameShortDp),
        shape = shape,
        perforation = perforation,
        filter = filter,
        output = output,
    )

    /**
     * Draws a saved stamp again from its original with [filter] ([finalShape] is the shape the
     * stamp has now, i.e. after [RenderSpec.turnAfter]).
     */
    fun rerender(
        original: File,
        spec: RenderSpec,
        finalShape: StampShape,
        perforation: PerforationStyle,
        filter: StampFilter,
        output: File,
    ): RenderedStamp {
        val shape = if (spec.turnAfter % 180 != 0) finalShape.quarterTurned() else finalShape
        return renderWindow(original, spec, shape, perforation, filter, output)
    }

    /**
     * Just the stamp's picture (cropped and upright, no paper or filter), long side about
     * [longPx]: the filter picker's thumbnails. Null when the original won't decode.
     */
    fun picture(original: File, spec: RenderSpec, longPx: Int): Bitmap? = runCatching {
        cutPicture(original, spec, longPx)
    }.getOrNull()

    private fun cutPicture(original: File, spec: RenderSpec, longPx: Int): Bitmap {
        val window = spec.window
        val exifOrientation = exifOrientation(original)
        val (uprightW, uprightH) = uprightSize(original)
        val turnedW = if (spec.turnBefore % 180 != 0) uprightH else uprightW
        val turnedH = if (spec.turnBefore % 180 != 0) uprightW else uprightH

        // Sample down only as far as the crop still delivers longPx.
        val cropLongPx = max(window.width * turnedW, window.height * turnedH)
        var sample = 1
        while (cropLongPx / (sample * 2) >= longPx) sample *= 2

        val decoded = BitmapFactory.decodeFile(original.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Could not decode ${original.name}")
        val upright = decoded.applyExif(exifOrientation)
        val turned = rotate(upright, spec.turnBefore)
        val cropped = crop(turned, window)
        return rotate(cropped, spec.turnAfter)
    }

    private fun renderWindow(
        original: File,
        spec: RenderSpec,
        shape: StampShape,
        perforation: PerforationStyle,
        filter: StampFilter,
        output: File,
    ): RenderedStamp {
        val photo = cutPicture(original, spec, MaxLongPx)
        val finalShape = if (spec.turnAfter % 180 != 0) shape.quarterTurned() else shape

        val stamp = drawStamp(photo, finalShape, perforation, spec.frameShortDp, filter)
        photo.recycle()
        output.outputStream().use { stamp.compress(Bitmap.CompressFormat.PNG, PngQuality, it) }
        stamp.recycle()
        return RenderedStamp(output, finalShape, spec)
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

    internal fun drawStamp(
        photo: Bitmap,
        shape: StampShape,
        perforation: PerforationStyle,
        frameShortDp: Float,
        filter: StampFilter = StampFilter.Original,
    ): Bitmap {
        // Outer paper size: the photo plus the paper margin, at the frame's proportions.
        val marginRatio = perforation.paperMarginDp / frameShortDp
        val photoShort = min(photo.width, photo.height).toFloat()
        val outerShortFromPhoto = photoShort / (1f - 2f * marginRatio)
        val outerLongFromPhoto = outerShortFromPhoto / min(shape.aspectRatio, 1f / shape.aspectRatio)
        val outerLong = outerLongFromPhoto.coerceIn(MinLongPx.toFloat(), MaxLongPx.toFloat())
        val outerW = (if (shape.aspectRatio >= 1f) outerLong else outerLong * shape.aspectRatio).roundToInt()
        val outerH = (if (shape.aspectRatio >= 1f) outerLong / shape.aspectRatio else outerLong).roundToInt()

        val pxPerDp = min(outerW, outerH) / frameShortDp
        val margin = perforation.paperMarginDp * pxPerDp

        val out = createBitmap(outerW, outerH) // ARGB_8888, transparent
        val canvas = Canvas(out)
        val paperPath = perforatedPath(
            size = Size(outerW.toFloat(), outerH.toFloat()),
            stampShape = shape,
            holeRadius = perforation.holeRadiusDp * pxPerDp,
            pitch = perforation.pitchDp * pxPerDp,
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
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            filter.colorMatrix()?.let { paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(it)) }
            drawBitmap(photo, null, dst, paint)
        }
        return out
    }

    private companion object {
        const val MinLongPx = 1600
        const val MaxLongPx = 2400
        const val PngQuality = 100
    }
}

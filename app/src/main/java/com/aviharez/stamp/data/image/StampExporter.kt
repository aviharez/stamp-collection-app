package com.aviharez.stamp.data.image

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.ui.components.drawPostmark
import com.aviharez.stamp.ui.components.postmarkLines
import com.aviharez.stamp.ui.components.postmarkWidth
import com.aviharez.stamp.ui.theme.AirmailPalette
import com.aviharez.stamp.ui.theme.AirmailType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ExportBackground { Transparent, Paper }

data class ExportOptions(
    val background: ExportBackground = ExportBackground.Transparent,
    val postmark: Boolean = true,
    /** Free tier: a small app-name mark in a corner of the picture. */
    val watermark: Boolean = true,
)

/**
 * Builds the image that leaves the app: the rendered stamp at full resolution (long side ≥ 1600px),
 * with its postmark as seen on Stamped!, on a transparent or Paper background, plus the free-tier
 * watermark. Blocking; call off the main thread.
 */
@Singleton
class StampExporter @Inject constructor(@ApplicationContext private val context: Context) {

    private val measurer by lazy {
        TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)
    }
    private val watermarkTypeface by lazy { ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_bold) }

    /** @param maxLongPx downscale for an on-screen preview; null = full resolution. */
    fun render(stamp: Stamp, options: ExportOptions, maxLongPx: Int? = null): Bitmap {
        val stampBitmap = decode(File(stamp.imagePath), maxLongPx)
        val sw = stampBitmap.width.toFloat()
        val sh = stampBitmap.height.toFloat()
        val short = min(sw, sh)
        val d = short * PostmarkScale

        // Content = the stamp plus the postmark hanging over its bottom-end corner; the image is
        // that with even padding (a hair on transparent for clean edges, a mat on Paper).
        val onPaper = options.background == ExportBackground.Paper
        val pad = short * if (onPaper) PaperPad else TransparentPad
        val overhangRight = if (options.postmark) d * (PostmarkOffsetX + OverhangSlop) else 0f
        val overhangBottom = if (options.postmark) d * (PostmarkOffsetY + OverhangSlop) else 0f
        val left = pad
        val top = pad
        val width = ceil(left + sw + overhangRight + pad).toInt()
        val height = ceil(top + sh + overhangBottom + pad).toInt()

        val out = createBitmap(width, height)
        val canvas = android.graphics.Canvas(out)
        if (onPaper) {
            canvas.drawColor(AirmailPalette.paper.toArgb())
            // Tinted soft shadow from the stamp's own outline (perforations included).
            val mask = stampBitmap.extractAlpha()
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AirmailPalette.shadowWarm.copy(alpha = ShadowAlpha).toArgb()
                maskFilter = BlurMaskFilter(short * ShadowBlur, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawBitmap(mask, left, top + short * ShadowDy, shadow)
            mask.recycle()
        }
        canvas.drawBitmap(stampBitmap, left, top, Paint(Paint.FILTER_BITMAP_FLAG))
        stampBitmap.recycle()

        if (options.watermark) drawWatermark(canvas, RectF(left, top, left + sw, top + sh), stamp.shape)

        if (options.postmark) {
            val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
            val lines = postmarkLines(stamp.place, stamp.day, context.resources, locale)
            // Same placement as on Stamped!: bottom-end of the stamp, nudged out over the edge.
            val boxLeft = left + sw + d * PostmarkOffsetX - postmarkWidth(d)
            val boxTop = top + sh + d * PostmarkOffsetY - d
            CanvasDrawScope().draw(
                Density(1f),
                LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(out.asImageBitmap()),
                Size(width.toFloat(), height.toFloat()),
            ) {
                translate(boxLeft, boxTop) {
                    drawPostmark(
                        measurer = measurer,
                        city = lines.top,
                        date = lines.bottom,
                        baseStyle = AirmailType.overline,
                        color = AirmailPalette.postmarkOnLight,
                        diameter = d,
                        rotation = PostmarkTilt,
                        showWaves = true,
                        dashedOuter = false,
                    )
                }
            }
        }
        return out
    }

    /** Writes the full-resolution export to the share cache (replacing earlier ones). */
    fun exportToCache(stamp: Stamp, options: ExportOptions): File = render(stamp, options).let { writeToCache(it, fileName(stamp)) }

    /**
     * Saves the full-resolution export into the shared Pictures/<app name> folder. Below Android 10
     * this needs WRITE_EXTERNAL_STORAGE (asked by the UI first).
     */
    fun saveToPictures(stamp: Stamp, options: ExportOptions): Uri = saveToPictures(fileName(stamp)) { render(stamp, options) }

    /** Any finished PNG (e.g. an album page) to the share cache; recycles [bitmap]. */
    fun writeToCache(bitmap: Bitmap, name: String): File {
        val dir = File(context.cacheDir, ExportDir).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PngQuality, it) }
        bitmap.recycle()
        return file
    }

    /** Any PNG made by [make] into Pictures/<app name> as [name]; the bitmap is recycled. */
    fun saveToPictures(name: String, make: () -> Bitmap): Uri {
        val resolver = context.contentResolver
        val folder = "${Environment.DIRECTORY_PICTURES}/${context.getString(R.string.app_name)}"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, MimePng)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, folder)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), context.getString(R.string.app_name))
                dir.mkdirs()
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(dir, name).path)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore insert failed")
        try {
            val bitmap = make()
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, PngQuality, it) }
                ?: error("No output stream for $uri")
            bitmap.recycle()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** A small PaperWhite label with the app name: bottom-start of the picture (top on a circle). */
    private fun drawWatermark(canvas: android.graphics.Canvas, stampRect: RectF, shape: StampShape) {
        val short = min(stampRect.width(), stampRect.height())
        val h = short * WatermarkHeight
        val text = context.getString(R.string.app_name)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = watermarkTypeface
            textSize = h * WatermarkTextRatio
            color = AirmailPalette.ink.toArgb()
            letterSpacing = WatermarkLetterSpacing
        }
        val w = textPaint.measureText(text) + h * WatermarkPadRatio * 2f
        val inset = short * WatermarkInset
        val rect = if (shape == StampShape.Circle) {
            RectF(stampRect.centerX() - w / 2f, stampRect.top + inset, stampRect.centerX() + w / 2f, stampRect.top + inset + h)
        } else {
            RectF(stampRect.left + inset, stampRect.bottom - inset - h, stampRect.left + inset + w, stampRect.bottom - inset)
        }
        val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AirmailPalette.paperWhite.copy(alpha = WatermarkPlateAlpha).toArgb() }
        canvas.drawRoundRect(rect, h / 2f, h / 2f, plate)
        val metrics = textPaint.fontMetrics
        val baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, rect.left + h * WatermarkPadRatio, baseline, textPaint)
    }

    private fun decode(file: File, maxLongPx: Int?): Bitmap {
        val options = BitmapFactory.Options()
        if (maxLongPx != null) {
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.path, options)
            val long = max(options.outWidth, options.outHeight)
            var sample = 1
            while (long / (sample * 2) >= maxLongPx) sample *= 2
            options.inJustDecodeBounds = false
            options.inSampleSize = sample
        }
        val decoded = BitmapFactory.decodeFile(file.path, options) ?: error("Could not decode ${file.name}")
        if (maxLongPx == null || max(decoded.width, decoded.height) <= maxLongPx) return decoded
        val scale = maxLongPx.toFloat() / max(decoded.width, decoded.height)
        return decoded.scale((decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt())
            .also { if (it !== decoded) decoded.recycle() }
    }

    private fun fileName(stamp: Stamp) = "stamp-${stamp.day}-${stamp.id.take(IdChars)}.png"

    companion object {
        const val MimePng = "image/png"
        /** Must match the cache-path in res/xml/file_paths.xml. */
        private const val ExportDir = "exports"
        private const val PngQuality = 100
        private const val IdChars = 8

        // Postmark, as on Stamped! (diameter relative to the stamp's short side).
        private const val PostmarkScale = 0.42f
        private const val PostmarkTilt = -12f
        private const val PostmarkOffsetX = 0.62f
        private const val PostmarkOffsetY = 0.22f
        private const val OverhangSlop = 0.06f

        private const val TransparentPad = 0.02f
        private const val PaperPad = 0.10f
        private const val ShadowBlur = 0.025f
        private const val ShadowDy = 0.015f
        private const val ShadowAlpha = 0.28f

        private const val WatermarkHeight = 0.065f
        private const val WatermarkTextRatio = 0.5f
        private const val WatermarkPadRatio = 0.45f
        private const val WatermarkInset = 0.075f
        private const val WatermarkLetterSpacing = 0.04f
        private const val WatermarkPlateAlpha = 0.9f
    }
}

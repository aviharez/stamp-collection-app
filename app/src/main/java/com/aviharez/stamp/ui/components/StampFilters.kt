package com.aviharez.stamp.ui.components

import androidx.compose.ui.graphics.Color
import com.aviharez.stamp.domain.model.StampFilter
import com.aviharez.stamp.ui.theme.AirmailRed
import com.aviharez.stamp.ui.theme.Cobalt
import com.aviharez.stamp.ui.theme.Ink
import com.aviharez.stamp.ui.theme.PaperWhite
import com.aviharez.stamp.ui.theme.Sage
import com.aviharez.stamp.ui.theme.SepiaInk
import com.aviharez.stamp.ui.theme.SepiaPaper

/**
 * The 4×5 colour matrix (Android / Compose layout, offsets in 0..255) for each [StampFilter].
 * One source for the renderer (android.graphics.ColorMatrix) and live previews (Compose
 * ColorFilter.colorMatrix), so what you pick is what gets saved. The single-ink filters print the
 * picture in one of the app's own inks on stamp paper, like old engraved stamps.
 */
fun StampFilter.colorMatrix(): FloatArray? = when (this) {
    StampFilter.Original -> null
    // Warm, a little faded: lifted blacks, softer contrast, golden cast.
    StampFilter.Postcard -> multiply(
        tone(contrast = 0.86f, brightness = 14f),
        saturation(0.82f),
        scale(1.06f, 1.0f, 0.88f),
    )
    StampFilter.Engraved -> duotone(Ink, PaperWhite, contrast = 1.3f)
    StampFilter.Sepia -> duotone(SepiaInk, SepiaPaper, contrast = 1.2f)
    StampFilter.Vivid -> multiply(tone(contrast = 1.14f, brightness = -6f), saturation(1.45f))
    // Washed-out and cool, like a stamp left in the sun.
    StampFilter.Faded -> multiply(
        tone(contrast = 0.72f, brightness = 34f),
        saturation(0.55f),
        scale(0.96f, 1.0f, 1.04f),
    )
    StampFilter.RedInk -> duotone(AirmailRed, PaperWhite, contrast = 1.3f)
    StampFilter.BlueInk -> duotone(Cobalt, PaperWhite, contrast = 1.3f)
    StampFilter.GreenInk -> duotone(Sage, PaperWhite, contrast = 1.3f)
}

private const val LumaR = 0.299f
private const val LumaG = 0.587f
private const val LumaB = 0.114f
private const val Max = 255f
private const val Mid = 128f

/** Picture in one ink: black → [dark], white → [light], with [contrast] around mid-grey. */
private fun duotone(dark: Color, light: Color, contrast: Float): FloatArray {
    val m = FloatArray(MatrixSize)
    val ink = floatArrayOf(dark.red, dark.green, dark.blue)
    val paper = floatArrayOf(light.red, light.green, light.blue)
    for (c in 0..2) {
        val span = (paper[c] - ink[c]) * contrast
        val row = c * RowSize
        m[row] = span * LumaR
        m[row + 1] = span * LumaG
        m[row + 2] = span * LumaB
        // Contrast pivots on mid-grey: out = ink + span·(L − 128)/255·255 + (paper − ink)·128
        m[row + 4] = ink[c] * Max + (paper[c] - ink[c]) * Mid - span * Mid
    }
    m[AlphaIndex] = 1f
    return m
}

private fun tone(contrast: Float, brightness: Float): FloatArray {
    val offset = Mid * (1f - contrast) + brightness
    return floatArrayOf(
        contrast, 0f, 0f, 0f, offset,
        0f, contrast, 0f, 0f, offset,
        0f, 0f, contrast, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    )
}

private fun saturation(s: Float): FloatArray {
    val ir = (1f - s) * LumaR
    val ig = (1f - s) * LumaG
    val ib = (1f - s) * LumaB
    return floatArrayOf(
        ir + s, ig, ib, 0f, 0f,
        ir, ig + s, ib, 0f, 0f,
        ir, ig, ib + s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}

private fun scale(r: Float, g: Float, b: Float): FloatArray = floatArrayOf(
    r, 0f, 0f, 0f, 0f,
    0f, g, 0f, 0f, 0f,
    0f, 0f, b, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

/** Applies [matrices] in order (first one first). */
private fun multiply(vararg matrices: FloatArray): FloatArray = matrices.reduce { acc, next -> concat(next, acc) }

/** a ∘ b: b is applied first, then a (4×5 affine matrices). */
private fun concat(a: FloatArray, b: FloatArray): FloatArray {
    val out = FloatArray(MatrixSize)
    for (i in 0..3) {
        for (j in 0..4) {
            var v = if (j == 4) a[i * RowSize + 4] else 0f
            for (k in 0..3) v += a[i * RowSize + k] * b[k * RowSize + j]
            out[i * RowSize + j] = v
        }
    }
    return out
}

private const val RowSize = 5
private const val MatrixSize = 20
private const val AlphaIndex = 18

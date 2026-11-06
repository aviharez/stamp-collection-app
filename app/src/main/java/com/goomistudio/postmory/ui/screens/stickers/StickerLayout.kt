package com.goomistudio.postmory.ui.screens.stickers

import com.goomistudio.postmory.domain.model.ScrapSticker
import com.goomistudio.postmory.domain.model.Stamp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** How the stamps sit on the sheet: an even grid of one size, or a mix of sizes with paper stickers. */
enum class SheetLook { Grid, Mix }

/** Grid sticker size: the long side of each stamp, in mm. */
enum class StickerSize(val longMm: Float) { Small(28f), Medium(38f), Large(52f) }

/** Printer paper: size in mm (layout) and in PDF points (1/72 inch). */
enum class SheetPaper(val widthMm: Float, val heightMm: Float, val widthPt: Int, val heightPt: Int) {
    A4(210f, 297f, 595, 842),
    Letter(215.9f, 279.4f, 612, 792),
}

/**
 * One sticker on a sheet. [x], [y], [w], [h] are the printed piece itself (stamp or paper sticker)
 * in mm from the page's top-left corner, before [tilt]; the dashed cut line runs [StickerSheets.CutMm] outside it.
 */
sealed interface SheetPiece {
    val x: Float
    val y: Float
    val w: Float
    val h: Float
    val tilt: Float

    data class StampPiece(val stamp: Stamp, override val x: Float, override val y: Float, override val w: Float, override val h: Float, override val tilt: Float) : SheetPiece

    /** A paper sticker in a gap; [stamp] gives a postmark sticker its words. */
    data class StickerPiece(val sticker: ScrapSticker, val stamp: Stamp?, override val x: Float, override val y: Float, override val w: Float, override val h: Float, override val tilt: Float) : SheetPiece
}

data class SheetPage(val pieces: List<SheetPiece>)

/** Lays album stamps out on printable sticker pages (all sizes in mm). */
object StickerSheets {
    /** Dashed cut line distance from each piece. */
    const val CutMm = 2f
    const val SideMm = 12f
    /** Room for the header (album name · "cut along the dashed lines"). */
    const val TopMm = 22f
    const val BottomMm = 12f
    private const val GridGapMm = 4f
    private const val MixGapMm = 3f
    /** Square and round stamps look as big as the oblong ones at this share of the long side. */
    private const val SquareShare = 0.86f
    private const val MaxTilt = 3

    private val MixSizes = floatArrayOf(60f, 42f, 42f)
    private const val MixSmallMm = 30f
    /** A paper sticker goes in after every this many stamps (only while more stamps follow). */
    private const val StickerEvery = 4

    /** Paper stickers for the gaps, in turn, with their size on the sheet (mm). */
    private val GapStickers = listOf(
        Triple(ScrapSticker.ParAvion, 34f, 16f),
        Triple(ScrapSticker.Heart, 18f, 18f),
        Triple(ScrapSticker.Postmark, 38f, 18f),
        Triple(ScrapSticker.Star, 18f, 18f),
        Triple(ScrapSticker.Received, 30f, 11f),
    )

    fun layout(stamps: List<Stamp>, look: SheetLook, size: StickerSize, paper: SheetPaper): List<SheetPage> =
        if (stamps.isEmpty()) emptyList() else when (look) {
            SheetLook.Grid -> grid(stamps, size, paper)
            SheetLook.Mix -> mix(stamps, paper)
        }

    /** The printed size of a stamp whose long side is [long]. */
    fun stampSize(stamp: Stamp, long: Float): Pair<Float, Float> {
        val aspect = stamp.shape.aspectRatio
        return when {
            aspect < 1f -> long * aspect to long
            aspect > 1f -> long to long / aspect
            else -> long * SquareShare to long * SquareShare
        }
    }

    /** A: every stamp the same size, centred in an even grid of square cells. */
    private fun grid(stamps: List<Stamp>, size: StickerSize, paper: SheetPaper): List<SheetPage> {
        val cell = size.longMm + CutMm * 2
        val usableW = paper.widthMm - SideMm * 2
        val usableH = paper.heightMm - TopMm - BottomMm
        val cols = floor((usableW + GridGapMm) / (cell + GridGapMm)).toInt().coerceAtLeast(1)
        val rows = floor((usableH + GridGapMm) / (cell + GridGapMm)).toInt().coerceAtLeast(1)
        val x0 = SideMm + (usableW - (cols * cell + (cols - 1) * GridGapMm)) / 2
        return stamps.chunked(cols * rows).map { chunk ->
            SheetPage(
                chunk.mapIndexed { i, stamp ->
                    val (w, h) = stampSize(stamp, size.longMm)
                    val cx = x0 + (i % cols) * (cell + GridGapMm) + cell / 2
                    val cy = TopMm + (i / cols) * (cell + GridGapMm) + cell / 2
                    SheetPiece.StampPiece(stamp, cx - w / 2, cy - h / 2, w, h, tilt = 0f)
                },
            )
        }
    }

    /**
     * C: each page opens with one big stamp and two medium ones, then small ones, slightly tilted,
     * with a paper sticker after every few stamps. Packed "lowest spot first" on a 1 mm skyline;
     * leftover room at the foot of a page gets more paper stickers.
     */
    private fun mix(stamps: List<Stamp>, paper: SheetPaper): List<SheetPage> {
        val pages = ArrayList<SheetPage>()
        var next = 0
        var stickerTurn = 0
        while (next < stamps.size) {
            val page = Skyline(paper)
            var onPage = 0
            while (next < stamps.size) {
                if (onPage > 0 && onPage % StickerEvery == 0 && page.lastWasStamp) {
                    val (sticker, w, h) = GapStickers[stickerTurn % GapStickers.size]
                    if (page.place(w, h, tiltFor(stickerTurn)) { x, y -> SheetPiece.StickerPiece(sticker, stamps[next], x, y, w, h, tiltFor(stickerTurn)) }) {
                        stickerTurn++
                    }
                }
                val stamp = stamps[next]
                val tilt = tiltFor(stamp.id.hashCode())
                // Smaller when the planned size no longer fits on this page.
                val placed = listOf(MixSizes.getOrElse(onPage) { MixSmallMm }, MixSmallMm).distinct().any { long ->
                    val (w, h) = stampSize(stamp, long)
                    page.place(w, h, tilt) { x, y -> SheetPiece.StampPiece(stamp, x, y, w, h, tilt) }
                }
                if (!placed) break
                next++
                onPage++
            }
            // Fill the gaps beside the stamps with paper stickers (never rows of stickers below them).
            val stampsEnd = page.stampsBottom
            var tries = 0
            while (tries < GapStickers.size) {
                val (sticker, w, h) = GapStickers[stickerTurn % GapStickers.size]
                val anchor = stamps[(next - 1).coerceAtLeast(0)]
                if (page.place(w, h, tiltFor(stickerTurn), maxBottom = stampsEnd) { x, y -> SheetPiece.StickerPiece(sticker, anchor, x, y, w, h, tiltFor(stickerTurn)) }) {
                    tries = 0
                } else {
                    tries++
                }
                stickerTurn++
            }
            if (onPage == 0) break // a page too small for even one stamp: never loop forever
            pages += SheetPage(page.pieces)
        }
        return pages
    }

    private fun tiltFor(seed: Int): Float = (Math.floorMod(seed, MaxTilt * 2 + 1) - MaxTilt).toFloat()

    /** Bottom-left skyline packing in 1 mm columns over the page's usable area. */
    private class Skyline(paper: SheetPaper) {
        private val usableH = paper.heightMm - TopMm - BottomMm
        private val tops = FloatArray(floor(paper.widthMm - SideMm * 2).toInt())
        val pieces = ArrayList<SheetPiece>()
        var lastWasStamp = false
            private set
        /** Where the lowest stamp's cut line ends, in the usable area. */
        var stampsBottom = 0f
            private set

        /** Puts a [w] × [h] piece (tilted by [tilt]) on the lowest free spot ending above [maxBottom]; false if none is left. */
        fun place(w: Float, h: Float, tilt: Float, maxBottom: Float = usableH, piece: (x: Float, y: Float) -> SheetPiece): Boolean {
            val r = Math.toRadians(abs(tilt.toDouble()))
            val cutW = w + CutMm * 2
            val cutH = h + CutMm * 2
            // The tilted cut line's bounding box.
            val fw = (cutW * cos(r) + cutH * sin(r)).toFloat()
            val fh = (cutW * sin(r) + cutH * cos(r)).toFloat()
            val n = ceil(fw).toInt()
            if (n > tops.size) return false
            var bestX = -1
            var bestY = Float.MAX_VALUE
            for (x in 0..tops.size - n) {
                var y = 0f
                for (i in x until x + n) if (tops[i] > y) y = tops[i]
                if (y < bestY) {
                    bestY = y
                    bestX = x
                }
            }
            if (bestX < 0 || bestY + fh > minOf(usableH, maxBottom)) return false
            for (i in bestX until bestX + n) tops[i] = bestY + fh + MixGapMm
            val cx = SideMm + bestX + n / 2f
            val cy = TopMm + bestY + fh / 2
            val p = piece(cx - w / 2, cy - h / 2)
            pieces += p
            lastWasStamp = p is SheetPiece.StampPiece
            if (lastWasStamp) stampsBottom = maxOf(stampsBottom, bestY + fh)
            return true
        }
    }
}

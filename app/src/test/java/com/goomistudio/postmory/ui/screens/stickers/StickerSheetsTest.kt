package com.goomistudio.postmory.ui.screens.stickers

import com.goomistudio.postmory.ui.components.previewStamps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class StickerSheetsTest {
    private val counts = listOf(1, 5, 14, 40, 97)

    @Test
    fun everyStampIsPlacedOnce() {
        for (look in SheetLook.entries) for (size in StickerSize.entries) for (paper in SheetPaper.entries) for (n in counts) {
            val stamps = previewStamps(n)
            val placed = StickerSheets.layout(stamps, look, size, paper)
                .flatMap { it.pieces }
                .filterIsInstance<SheetPiece.StampPiece>()
                .map { it.stamp.id }
            assertEquals("$look $size $paper $n", stamps.map { it.id }, placed)
        }
    }

    @Test
    fun cutLinesStayInsideThePrintableArea() {
        for (look in SheetLook.entries) for (size in StickerSize.entries) for (paper in SheetPaper.entries) {
            StickerSheets.layout(previewStamps(40), look, size, paper).forEach { page ->
                page.pieces.forEach { p ->
                    val b = box(p)
                    val msg = "$look $size $paper $p"
                    assertTrue(msg, b[0] >= StickerSheets.SideMm - Slack && b[2] <= paper.widthMm - StickerSheets.SideMm + Slack)
                    assertTrue(msg, b[1] >= StickerSheets.TopMm - Slack && b[3] <= paper.heightMm - StickerSheets.BottomMm + Slack)
                }
            }
        }
    }

    @Test
    fun cutLinesNeverOverlap() {
        for (look in SheetLook.entries) for (size in StickerSize.entries) for (paper in SheetPaper.entries) {
            StickerSheets.layout(previewStamps(40), look, size, paper).forEach { page ->
                val boxes = page.pieces.map(::box)
                for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                    val a = boxes[i]
                    val b = boxes[j]
                    val overlap = a[0] < b[2] - Slack && b[0] < a[2] - Slack && a[1] < b[3] - Slack && b[1] < a[3] - Slack
                    assertFalse("$look $size $paper ${page.pieces[i]} / ${page.pieces[j]}", overlap)
                }
            }
        }
    }

    @Test
    fun noStampsNoPages() {
        assertTrue(StickerSheets.layout(emptyList(), SheetLook.Mix, StickerSize.Medium, SheetPaper.A4).isEmpty())
    }

    /** The tilted cut line's bounding box: left, top, right, bottom (mm). */
    private fun box(p: SheetPiece): FloatArray {
        val cw = p.w + StickerSheets.CutMm * 2
        val ch = p.h + StickerSheets.CutMm * 2
        val r = Math.toRadians(abs(p.tilt.toDouble()))
        val fw = (cw * cos(r) + ch * sin(r)).toFloat()
        val fh = (cw * sin(r) + ch * cos(r)).toFloat()
        val cx = p.x + p.w / 2
        val cy = p.y + p.h / 2
        return floatArrayOf(cx - fw / 2, cy - fh / 2, cx + fw / 2, cy + fh / 2)
    }

    private companion object {
        const val Slack = 0.01f
    }
}

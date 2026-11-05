package com.aviharez.stamp.ui.screens.scrapbook

import androidx.annotation.StringRes
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.ScrapItem
import com.aviharez.stamp.domain.model.ScrapKind
import com.aviharez.stamp.domain.model.ScrapRules
import com.aviharez.stamp.domain.model.ScrapSticker
import com.aviharez.stamp.domain.model.Stamp
import java.util.UUID

/** What a new page's layouts are filled with. [stamps] = the album's newest first. */
data class ScrapLayoutInput(
    val stamps: List<Stamp>,
    val albumName: String,
    /** Postmark sticker lines ("top\nbottom") for the newest stamp, or today's date. */
    val postmark: String,
    val sealLetter: String,
    val ticketDate: String,
)

/**
 * The starting points for a new scrapbook page (so it never starts blank). Each is a few pieces
 * placed by hand on the 360 × 480 page; slots without a stamp to fill them are left out. After
 * that, every piece moves freely.
 */
enum class ScrapLayout(@param:StringRes val label: Int, val stampSlots: Int) {
    Diary(R.string.scrap_layout_diary, 3),
    Collage(R.string.scrap_layout_collage, 6),
    Postcard(R.string.scrap_layout_postcard, 1),
    BigOne(R.string.scrap_layout_big, 1);

    fun build(input: ScrapLayoutInput): List<ScrapItem> = PageBuilder(input).apply {
        when (this@ScrapLayout) {
            Diary -> {
                title(input.albumName, 0.5f, 0.085f)
                stamp(0, cx = 0.31f, cy = 0.39f, boxW = 180f, boxH = 220f, rotation = -3f, tape = ScrapSticker.TapeRed)
                stamp(1, cx = 0.77f, cy = 0.29f, boxW = 120f, boxH = 120f, rotation = 5f)
                stamp(2, cx = 0.75f, cy = 0.56f, boxW = 120f, boxH = 120f, rotation = -4f)
                note(0.38f, 0.82f, rotation = -2f)
                sticker(ScrapSticker.Postmark, input.postmark, 0.8f, 0.8f, rotation = -12f, scale = 0.9f)
            }
            Collage -> {
                val tilts = floatArrayOf(-4f, 3f, 5f, -3f, -5f, 4f)
                for (i in 0 until stampSlots) {
                    val col = i % 2
                    val row = i / 2
                    stamp(
                        i,
                        cx = if (col == 0) 0.29f else 0.71f,
                        cy = 0.19f + row * 0.31f,
                        boxW = 150f,
                        boxH = 130f,
                        rotation = tilts[i],
                        tape = if (i == 0) ScrapSticker.TapeCobalt else null,
                    )
                }
                sticker(ScrapSticker.Heart, "", 0.5f, 0.5f, rotation = -10f)
            }
            Postcard -> {
                stamp(0, cx = 0.5f, cy = 0.31f, boxW = 300f, boxH = 230f, rotation = -2f, tape = ScrapSticker.TapeSage)
                note(0.33f, 0.74f, rotation = -1f)
                sticker(ScrapSticker.Postmark, input.postmark, 0.76f, 0.68f, rotation = -10f)
                sticker(ScrapSticker.ParAvion, "", 0.76f, 0.9f, rotation = 6f)
            }
            BigOne -> {
                val first = input.stamps.firstOrNull()
                stamp(0, cx = 0.5f, cy = 0.43f, boxW = 290f, boxH = 330f, rotation = 2f, tape = ScrapSticker.TapeCobalt)
                title(first?.title ?: first?.place?.city ?: input.albumName, 0.5f, 0.9f)
            }
        }
    }.items

    companion object {
        /** A layout that suits how many stamps the album has. */
        fun defaultFor(stampCount: Int): ScrapLayout = if (stampCount <= 1) BigOne else Diary
    }
}

private class PageBuilder(private val input: ScrapLayoutInput) {
    val items = mutableListOf<ScrapItem>()

    fun stamp(index: Int, cx: Float, cy: Float, boxW: Float, boxH: Float, rotation: Float, tape: ScrapSticker? = null) {
        val stamp = input.stamps.getOrNull(index) ?: return
        val baseW = StampBaseWidth.value
        val baseH = baseW / stamp.shape.aspectRatio
        val scale = ScrapRules.clampScale(minOf(boxW / baseW, boxH / baseH))
        items += ScrapItem(newId(), ScrapKind.Stamp, ref = stamp.id, x = cx, y = cy, scale = scale, rotation = rotation)
        if (tape != null) {
            val top = cy - baseH * scale / 2 / PageHeight
            items += ScrapItem(newId(), ScrapKind.Sticker, ref = tape.name, x = cx - 0.04f, y = top + TapeDrop, rotation = rotation - TapeTilt)
        }
    }

    fun sticker(sticker: ScrapSticker, text: String, x: Float, y: Float, rotation: Float, scale: Float = 1f) {
        items += ScrapItem(newId(), ScrapKind.Sticker, ref = sticker.name, text = text, x = x, y = y, scale = scale, rotation = rotation)
    }

    fun note(x: Float, y: Float, rotation: Float) {
        items += ScrapItem(newId(), ScrapKind.Note, x = x, y = y, rotation = rotation)
    }

    fun title(text: String, x: Float, y: Float) {
        items += ScrapItem(newId(), ScrapKind.Title, text = text.take(ScrapRules.MaxTitleLength), x = x, y = y)
    }
}

internal fun newScrapId(): String = UUID.randomUUID().toString()

private fun newId() = newScrapId()

/** Page height in page dp (360 wide, 3 : 4). */
private val PageHeight = ScrapDesignWidth.value / ScrapRules.Aspect
private const val TapeDrop = 0.005f
private const val TapeTilt = 8f

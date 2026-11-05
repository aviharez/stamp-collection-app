package com.aviharez.stamp.domain.model

import java.time.Instant

/** What a piece on a scrapbook page is. */
enum class ScrapKind { Stamp, Sticker, Note, Title }

/** The paper stickers (tape included) a scrapbook page can hold. */
enum class ScrapSticker {
    ParAvion, Received, Fragile, WaxSeal, Ticket, Heart, Star, Postmark, TapeRed, TapeCobalt, TapeSage;

    val isTape: Boolean get() = this == TapeRed || this == TapeCobalt || this == TapeSage
}

/**
 * One piece on a scrapbook page. Positions are fractions of the page (centre of the piece), so the
 * page draws the same in the editor, on the album page and in the export.
 *
 * @param ref the stamp id ([ScrapKind.Stamp]) or the [ScrapSticker] name; empty for writing.
 * @param text the words of a note or title; the letter of a wax seal; postmark lines ("top\nbottom").
 * @param scale 1 = the kind's base size on the page.
 * @param rotation degrees, clockwise.
 */
data class ScrapItem(
    val id: String,
    val kind: ScrapKind,
    val ref: String = "",
    val text: String = "",
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
) {
    val sticker: ScrapSticker? get() = if (kind == ScrapKind.Sticker) ScrapSticker.entries.firstOrNull { it.name == ref } else null

    /** A note or title nobody wrote in: shown as a hint in the editor, left out everywhere else. */
    val isBlankWriting: Boolean get() = (kind == ScrapKind.Note || kind == ScrapKind.Title) && text.isBlank()
}

/** A free-form page in an album. Pieces later in [items] lie on top. */
data class ScrapPage(
    val id: String,
    val albumId: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val items: List<ScrapItem>,
) {
    val stampIds: Set<String> get() = items.mapNotNullTo(HashSet()) { it.ref.takeIf { _ -> it.kind == ScrapKind.Stamp } }
}

object ScrapRules {
    /** Page aspect, width : height (same 3 : 4 as the album page export). */
    const val Aspect = 3f / 4f
    const val MaxItems = 40
    const val MinScale = 0.35f
    const val MaxScale = 3.5f
    const val MaxNoteLength = 80
    const val MaxTitleLength = 40

    fun clampScale(scale: Float) = scale.coerceIn(MinScale, MaxScale)

    /** Keeps a piece's centre on the page so it can always be grabbed again. */
    fun clampPosition(v: Float) = v.coerceIn(0f, 1f)
}

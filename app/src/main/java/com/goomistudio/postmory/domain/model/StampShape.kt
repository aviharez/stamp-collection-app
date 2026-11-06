package com.goomistudio.postmory.domain.model

/** Outline of a stamp. [aspectRatio] is width / height of the stamp paper. */
enum class StampShape(val aspectRatio: Float) {
    Portrait(3f / 4f),
    Landscape(4f / 3f),
    Square(1f),
    Circle(1f),
    ;

    /** Shape after turning the stamp a quarter turn (photo taken with the phone sideways). */
    fun quarterTurned(): StampShape = when (this) {
        Portrait -> Landscape
        Landscape -> Portrait
        else -> this
    }
}

/**
 * Perforation ("edge") style. Sizes are in dp at on-screen size; export scales them with the
 * stamp so the rendered PNG looks like what the user saw.
 *
 * @param holeRadiusDp radius of each perforation hole (holes are centred on the edge).
 * @param pitchDp target distance between hole centres; the real pitch is adjusted so holes
 *   fit each edge evenly.
 * @param free part of the free tier; the others are Pro (CLAUDE.md §4 "all edges/filters"),
 *   or unlocked for a day with a short ad.
 */
enum class PerforationStyle(val holeRadiusDp: Float, val pitchDp: Float, val free: Boolean) {
    Classic(holeRadiusDp = 3.5f, pitchDp = 12f, free = true),
    Fine(holeRadiusDp = 2.5f, pitchDp = 8f, free = false),
    Bold(holeRadiusDp = 4.5f, pitchDp = 16f, free = false),
    Scallop(holeRadiusDp = 4.5f, pitchDp = 10.5f, free = false),
    ;

    /** White paper between the perforated edge and the picture: a hole's diameter + a little. */
    val paperMarginDp: Float get() = holeRadiusDp * 2f + PAPER_MARGIN_EXTRA_DP

    private companion object {
        const val PAPER_MARGIN_EXTRA_DP = 2f
    }
}

/** How a stamp's picture is finished: its colour filter and its perforated edge. */
data class StampLook(
    val filter: StampFilter = StampFilter.Original,
    val edge: PerforationStyle = PerforationStyle.Classic,
)

package com.aviharez.stamp.domain.model

/** Outline of a stamp. [aspectRatio] is width / height of the stamp paper. */
enum class StampShape(val aspectRatio: Float) {
    Portrait(3f / 4f),
    Landscape(4f / 3f),
    Square(1f),
    Circle(1f),
}

/**
 * Perforation ("edge") style. Sizes are in dp at on-screen size; export scales them with the
 * stamp so the rendered PNG looks like what the user saw.
 *
 * @param holeRadiusDp radius of each perforation hole (holes are centred on the edge).
 * @param pitchDp target distance between hole centres; the real pitch is adjusted so holes
 *   fit each edge evenly.
 */
enum class PerforationStyle(val holeRadiusDp: Float, val pitchDp: Float) {
    Classic(holeRadiusDp = 3.5f, pitchDp = 12f),
    Fine(holeRadiusDp = 2.5f, pitchDp = 8f),
    Bold(holeRadiusDp = 4.5f, pitchDp = 16f),
    Scallop(holeRadiusDp = 4.5f, pitchDp = 10.5f),
}

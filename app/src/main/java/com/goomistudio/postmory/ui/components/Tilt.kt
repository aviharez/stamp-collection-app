package com.goomistudio.postmory.ui.components

import kotlin.random.Random

/**
 * Deterministic "hand-placed" rotations. Seeded (e.g. by stamp id) so an item keeps the same
 * tilt across recompositions, scrolling and app restarts.
 */
object Tilt {
    /** Stamps in album / collage contexts: −6°..+6° (0° in editors — just don't apply it). */
    fun stamp(seed: Long): Float = Random(seed).nextFloatIn(-6f, 6f)

    /** Postmarks: −15°..−8°. */
    fun postmark(seed: Long): Float = Random(seed xor POSTMARK_SALT).nextFloatIn(-15f, -8f)

    /** Hand-written notes: a gentle −4°..+3°. */
    fun note(seed: Long): Float = Random(seed xor NOTE_SALT).nextFloatIn(-4f, 3f)

    /** Washi tape strips: −12°..+12°. */
    fun tape(seed: Long): Float = Random(seed xor TAPE_SALT).nextFloatIn(-12f, 12f)

    private fun Random.nextFloatIn(from: Float, until: Float) = from + nextFloat() * (until - from)

    private const val POSTMARK_SALT = 0x5EED_0001L
    private const val NOTE_SALT = 0x5EED_0002L
    private const val TAPE_SALT = 0x5EED_0003L
}

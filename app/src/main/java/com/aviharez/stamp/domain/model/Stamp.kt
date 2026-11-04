package com.aviharez.stamp.domain.model

import java.time.Instant
import java.time.LocalDate

/** Where a stamp's photo came from. */
enum class StampSource { Camera, Gallery }

/** City · country for the postmark. Both null → date-only postmark. */
data class Place(
    val city: String?,
    val country: String?,
    val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
) {
    val hasName: Boolean get() = city != null || country != null

    companion object {
        val Unknown = Place(null, null, null, null, null)
    }
}

/**
 * One collected stamp.
 *
 * @param day the calendar day the stamp counts for, fixed at capture time in the user's time zone
 *   then (a stamp taken in Tokyo stays on its Tokyo date after flying home).
 * @param imagePath rendered stamp PNG (perforated, transparent outside).
 * @param originalPath full-quality source photo.
 */
data class Stamp(
    val id: String,
    val albumId: Long,
    val shape: StampShape,
    val perforation: PerforationStyle,
    val imagePath: String,
    val originalPath: String,
    val takenAt: Instant,
    val day: LocalDate,
    val place: Place,
    val title: String?,
    val caption: String?,
    val source: StampSource,
)

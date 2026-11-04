package com.aviharez.stamp.ui.components

import androidx.compose.runtime.Composable
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.Place
import java.time.LocalDate

/** The two lines inside a [Postmark]. */
data class PostmarkLines(val top: String, val bottom: String)

/**
 * Postmark text for [place] on [date]: "KYOTO / 04 NOV 26 · JP", or date-only
 * ("04 NOV / 2026") when the place has no name (location off, offline, …).
 */
@Composable
fun rememberPostmarkLines(place: Place, date: LocalDate = LocalDate.now()): PostmarkLines {
    val name = place.city ?: place.country
    return if (name == null) {
        PostmarkLines(
            top = rememberDateText(R.string.pattern_postmark_day, date),
            bottom = rememberDateText(R.string.pattern_postmark_year, date),
        )
    } else {
        val day = rememberDateText(R.string.pattern_postmark_date, date)
        val code = place.countryCode?.takeIf { place.city != null }
        PostmarkLines(top = name, bottom = if (code != null) "$day · $code" else day)
    }
}

package com.aviharez.stamp.ui.components

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.Place
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The two lines inside a [Postmark]. */
data class PostmarkLines(val top: String, val bottom: String)

/**
 * Postmark text for [place] on [date]: "KYOTO / 04 NOV 26 · JP", or date-only
 * ("04 NOV / 2026") when the place has no name (location off, offline, …).
 */
fun postmarkLines(place: Place, date: LocalDate, resources: Resources, locale: Locale): PostmarkLines {
    fun format(pattern: Int) = DateTimeFormatter.ofPattern(resources.getString(pattern), locale).format(date)
    val name = place.city ?: place.country
    return if (name == null) {
        PostmarkLines(top = format(R.string.pattern_postmark_day), bottom = format(R.string.pattern_postmark_year))
    } else {
        val day = format(R.string.pattern_postmark_date)
        val code = place.countryCode?.takeIf { place.city != null }
        PostmarkLines(top = name, bottom = if (code != null) "$day · $code" else day)
    }
}

@Composable
fun rememberPostmarkLines(place: Place, date: LocalDate = LocalDate.now()): PostmarkLines {
    val locale = LocalConfiguration.current.locales[0]
    val resources = LocalResources.current
    return remember(place, date, locale, resources) { postmarkLines(place, date, resources, locale) }
}

package com.aviharez.stamp.ui.screens.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.aviharez.stamp.data.map.WorldOutline
import com.aviharez.stamp.domain.model.Stamp
import java.time.LocalDate
import java.util.Locale

/** One place on the map: the stamps whose postmark names the same city in the same country. */
data class MapPlace(
    /** Lower-case city + "|" + country code; stable across stamps. */
    val key: String,
    /** Null when the postmark names only the country. */
    val city: String?,
    val countryCode: String,
    /** Average of the stamps' coordinates; null if none of them has any. */
    val latitude: Double?,
    val longitude: Double?,
    /** Newest first; never empty. */
    val stamps: List<Stamp>,
) {
    val newest: Stamp get() = stamps.first()
    val firstDay: LocalDate get() = stamps.last().day
}

/** One country on the map with its places (most stamps first). */
data class MapCountry(
    val code: String,
    /** The postmark's own country name, used when the code has no name on this phone. */
    val postmarkName: String?,
    val places: List<MapPlace>,
) {
    val stampCount: Int = places.sumOf { it.stamps.size }
    val newest: Stamp = places.maxBy { it.newest.takenAt }.newest
    val firstDay: LocalDate = places.minOf { it.firstDay }

    /** Where its pin goes: the busiest place that has coordinates. */
    val anchor: MapPlace? get() = places.firstOrNull { it.latitude != null }
}

/**
 * Groups stamps (newest first) into countries and places. Countries with the most stamps come
 * first (newest stamp breaks ties); inside a country, places with the most stamps first.
 */
fun groupByPlace(stamps: List<Stamp>): List<MapCountry> =
    stamps
        .filter { !it.place.countryCode.isNullOrBlank() }
        .groupBy { it.place.countryCode!!.trim().uppercase(Locale.ROOT) }
        .map { (code, inCountry) ->
            val places = inCountry
                .groupBy { it.place.city?.trim()?.takeIf(String::isNotEmpty)?.lowercase(Locale.ROOT) }
                .map { (city, list) ->
                    val located = list.filter { it.place.latitude != null && it.place.longitude != null }
                    MapPlace(
                        key = "${city.orEmpty()}|$code",
                        city = list.first().place.city?.trim()?.takeIf { city != null },
                        countryCode = code,
                        latitude = located.takeIf { it.isNotEmpty() }?.map { it.place.latitude!! }?.average(),
                        longitude = located.takeIf { it.isNotEmpty() }?.map { it.place.longitude!! }?.average(),
                        stamps = list,
                    )
                }
                .sortedWith(compareByDescending<MapPlace> { it.stamps.size }.thenByDescending { it.newest.takenAt })
            MapCountry(code, inCountry.first().place.country, places)
        }
        .sortedWith(compareByDescending<MapCountry> { it.stampCount }.thenByDescending { it.newest.takenAt })

/** Country codes that are on the bundled map (for "visited" colouring). */
fun List<MapCountry>.codesOn(outline: WorldOutline): Set<String> = mapNotNullTo(HashSet()) { c -> c.code.takeIf { outline[it] != null } }

/** The country's name in the phone's language ("Japan", "Jepang"…), else the postmark's own. */
@Composable
fun countryName(code: String, postmarkName: String?): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(code, postmarkName, locale) {
        val name = runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(locale) }.getOrNull()
        name?.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) } ?: postmarkName ?: code
    }
}

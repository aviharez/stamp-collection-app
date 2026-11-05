package com.aviharez.stamp.ui.screens.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aviharez.stamp.R
import com.aviharez.stamp.data.map.EqualEarth
import com.aviharez.stamp.data.map.WorldOutline
import com.aviharez.stamp.domain.model.Place
import com.aviharez.stamp.ui.components.CircleIconButton
import com.aviharez.stamp.ui.components.GrainedSurface
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.MapWindow
import com.aviharez.stamp.ui.components.PageStamp
import com.aviharez.stamp.ui.components.PaperChip
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.components.TapeColor
import com.aviharez.stamp.ui.components.Tilt
import com.aviharez.stamp.ui.components.TitleWithNote
import com.aviharez.stamp.ui.components.WorldMap
import com.aviharez.stamp.ui.components.previewStamps
import com.aviharez.stamp.ui.components.rememberDateText
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import java.time.Instant
import kotlin.math.roundToInt

/** Map tab: a kraft world map with a stamp pinned on each country, then countries or places. */
@Composable
fun MapScreen(
    contentPadding: PaddingValues,
    onOpenCountry: (String) -> Unit,
    onStampIt: () -> Unit,
    onGetPro: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sharing by rememberSaveable { mutableStateOf(false) }
    MapContent(state, contentPadding, onOpenCountry, onStampIt, onShare = { sharing = true }, modifier = modifier)
    if (sharing && state.countries.isNotEmpty()) {
        MapExportSheet(state, onDismiss = { sharing = false }, onGetPro = onGetPro)
    }
}

@Composable
fun MapContent(
    state: MapUiState,
    contentPadding: PaddingValues,
    onOpenCountry: (String) -> Unit,
    onStampIt: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = StampTheme.spacing
    var showPlaces by rememberSaveable { mutableStateOf(false) }
    val empty = state.loaded && state.countries.isEmpty()
    LazyColumn(
        modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = s.screen,
            end = s.screen,
            top = s.l,
            bottom = contentPadding.calculateBottomPadding() + s.l,
        ),
    ) {
        item(contentType = "header") { MapHeader(state, onShare) }
        item(contentType = "map") {
            WorldCard(state, onOpenCountry, Modifier.padding(top = s.m))
        }
        if (empty) {
            item(contentType = "empty") { EmptyMap(state.placeOn, onStampIt) }
        } else if (state.loaded) {
            item(contentType = "tabs") {
                Row(Modifier.padding(top = s.m, bottom = s.xs), horizontalArrangement = Arrangement.spacedBy(s.xs)) {
                    PaperChip(stringResource(R.string.map_tab_countries), !showPlaces, { showPlaces = false })
                    PaperChip(stringResource(R.string.map_tab_places), showPlaces, { showPlaces = true })
                }
            }
            if (showPlaces) {
                items(state.places, key = { "p" + it.key }, contentType = { "place" }) { place ->
                    PlaceRow(place, onClick = { onOpenCountry(place.countryCode) })
                }
            } else {
                items(state.countries, key = { "c" + it.code }, contentType = { "country" }) { country ->
                    CountryRow(country, onClick = { onOpenCountry(country.code) })
                }
            }
        }
        if (state.unplaced > 0 && !empty) {
            item(contentType = "unplaced") {
                HandNote(
                    pluralStringResource(R.plurals.map_unplaced, state.unplaced, state.unplaced),
                    Modifier.padding(top = s.m),
                    style = StampTheme.type.noteSmall,
                    strength = InkStrength.Soft,
                    rotation = Tilt.note(UnplacedSeed),
                )
            }
        }
    }
}

@Composable
private fun MapHeader(state: MapUiState, onShare: () -> Unit) {
    val countries = state.countries.size
    val places = state.places.size
    val stamps = state.stampCount
    Row(verticalAlignment = Alignment.Top) {
        TitleWithNote(
            modifier = Modifier.weight(1f),
            note = if (countries > 0) {
                {
                    HandNote(
                        stringResource(
                            R.string.map_stats,
                            pluralStringResource(R.plurals.map_country_count, countries, countries),
                            pluralStringResource(R.plurals.map_place_count, places, places),
                            pluralStringResource(R.plurals.album_stamp_count, stamps, stamps),
                        ),
                        style = StampTheme.type.noteSmall,
                        rotation = Tilt.note(StatsSeed),
                        modifier = Modifier.padding(bottom = StampTheme.spacing.xxs),
                    )
                }
            } else {
                null
            },
        ) {
            Text(stringResource(R.string.map_title), style = StampTheme.type.screenTitle, color = StampTheme.colors.textHigh)
        }
        if (countries > 0) {
            CircleIconButton(
                R.drawable.ic_share,
                stringResource(R.string.map_share_cd),
                onShare,
                Modifier.padding(start = StampTheme.spacing.xs, top = StampTheme.spacing.xxs),
            )
        }
    }
}

/** The kraft card: the world, a dot on every place, a taped stamp on the busiest countries. */
@Composable
internal fun WorldCard(state: MapUiState, onOpenCountry: (String) -> Unit, modifier: Modifier = Modifier) {
    val window = MapWindow.World
    val names = state.countries.take(DescribedCountries).map { countryName(it.code, it.postmarkName) }
    val description = if (state.countries.isEmpty()) {
        stringResource(R.string.map_cd_empty)
    } else {
        stringResource(R.string.map_cd, names.joinToString())
    }
    GrainedSurface(StampTheme.colors.kraft, modifier.fillMaxWidth(), shape = StampTheme.shapes.card) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(window.aspect)
                .padding(StampTheme.spacing.xs),
        ) {
            val outline = state.outline ?: return@Box
            val dot = StampTheme.colors.ink
            val ring = StampTheme.colors.paperWhite
            WorldMap(
                outline,
                window,
                state.visited,
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = description },
            ) {
                state.places.forEach { place ->
                    val lat = place.latitude ?: return@forEach
                    val o = window.place(lat, place.longitude!!, size)
                    drawCircle(ring, DotRing.toPx(), o)
                    drawCircle(dot, Dot.toPx(), o)
                }
            }
            CountryPins(state.countries.take(MaxPins), outline, window, onOpenCountry)
        }
    }
}

/**
 * The newest stamp of each country, taped just above its busiest place (or the middle of the
 * country when no stamp there has coordinates). Busiest countries are drawn on top.
 */
@Composable
private fun CountryPins(countries: List<MapCountry>, outline: WorldOutline, window: MapWindow, onOpenCountry: (String) -> Unit) {
    val pinned = remember(countries, outline) { countries.mapNotNull { pinSpot(it, outline) }.asReversed() }
    Layout(
        content = {
            pinned.forEachIndexed { i, pin ->
                val country = pin.country
                val name = countryName(country.code, country.postmarkName)
                val count = pluralStringResource(R.plurals.album_stamp_count, country.stampCount, country.stampCount)
                val description = stringResource(R.string.map_row_cd, name, count)
                Box(
                    Modifier
                        .size(StampTheme.spacing.touchTarget)
                        .clickable(role = Role.Button) { onOpenCountry(country.code) }
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    PageStamp(
                        country.newest,
                        Modifier.width(PinWidth),
                        tape = TapeColor.forSeed(i.toLong()),
                        tilt = Tilt.stamp(country.code.hashCode().toLong()),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val size = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val placeables = measurables.map { it.measure(Constraints()) }
        val gap = PinGap.toPx()
        val maxLift = PinMaxLift.toPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, p ->
                val pin = pinned[i]
                val o = window.toScreen(pin.anchor.x, pin.anchor.y, size)
                val landTop = window.toScreen(pin.anchor.x, pin.landTop, size).y
                val landBottom = window.toScreen(pin.anchor.x, pin.landBottom, size).y
                // The pin rests on the top edge of the land it marks (not too far up on big
                // countries), so the red country stays visible; with no room above (far north),
                // it hangs just below the land instead. Always inside the card.
                val above = minOf(o.y - gap, maxOf(landTop, o.y - maxLift)) - p.height
                val below = maxOf(o.y + gap, minOf(landBottom, o.y + maxLift))
                val x = (o.x - p.width / 2f).roundToInt().coerceIn(0, (constraints.maxWidth - p.width).coerceAtLeast(0))
                val y = (if (above >= 0) above else below).roundToInt().coerceIn(0, (constraints.maxHeight - p.height).coerceAtLeast(0))
                p.place(x, y)
            }
        }
    }
}

/** A country's pin (projected): the spot it marks and the top and bottom edges of the land around it. */
private class PinSpot(val country: MapCountry, val anchor: Offset, val landTop: Float, val landBottom: Float)

/** The spot is its busiest located place, else the middle of its biggest land. */
private fun pinSpot(country: MapCountry, outline: WorldOutline): PinSpot? {
    val shape = outline[country.code]
    val anchor = country.anchor?.let { Offset(EqualEarth.x(it.longitude!!, it.latitude!!).toFloat(), EqualEarth.y(it.latitude).toFloat()) }
        ?: shape?.center
        ?: return null
    val land = shape?.ringBounds
        ?.filter { b -> anchor.x in b[0]..b[2] && anchor.y in b[1]..b[3] }
        ?.maxByOrNull { (it[2] - it[0]) * (it[3] - it[1]) }
    return PinSpot(country, anchor, maxOf(anchor.y, land?.get(3) ?: anchor.y), minOf(anchor.y, land?.get(1) ?: anchor.y))
}

@Composable
private fun EmptyMap(placeOn: Boolean, onStampIt: () -> Unit) {
    val s = StampTheme.spacing
    Column(Modifier.fillMaxWidth().padding(top = s.m), verticalArrangement = Arrangement.spacedBy(s.m)) {
        HandNote(stringResource(R.string.map_empty), rotation = Tilt.note(EmptySeed))
        if (!placeOn) {
            Text(stringResource(R.string.map_empty_place_off), style = StampTheme.type.body, color = StampTheme.colors.textMedium)
        }
        PillButton(stringResource(R.string.home_stamp_it), onClick = onStampIt, leadingIcon = R.drawable.ic_camera)
    }
}

@Composable
private fun CountryRow(country: MapCountry, onClick: () -> Unit) {
    val name = countryName(country.code, country.postmarkName)
    val cities = country.places.mapNotNull { it.city }.joinToString(" · ")
    MapRow(
        stamp = { PageStamp(country.newest, Modifier.width(RowStampWidth), tilt = Tilt.stamp(country.code.hashCode().toLong()) / 2) },
        title = name,
        subtitle = cities.ifEmpty { null },
        count = country.stampCount,
        onClick = onClick,
    )
}

@Composable
private fun PlaceRow(place: MapPlace, onClick: () -> Unit) {
    val country = countryName(place.countryCode, place.newest.place.country)
    val since = rememberDateText(R.string.pattern_passport_since, place.firstDay)
    MapRow(
        stamp = { PageStamp(place.newest, Modifier.width(RowStampWidth), tilt = Tilt.stamp(place.key.hashCode().toLong()) / 2) },
        title = place.city ?: stringResource(R.string.map_somewhere_in, country),
        subtitle = stringResource(R.string.map_place_line, country, since),
        count = place.stamps.size,
        onClick = onClick,
    )
}

/** A list line: mini stamp, a name in Fraunces, a small line under it, the count in Caveat. */
@Composable
private fun MapRow(stamp: @Composable () -> Unit, title: String, subtitle: String?, count: Int, onClick: () -> Unit) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val t = StampTheme.type
    val countText = pluralStringResource(R.plurals.album_stamp_count, count, count)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = s.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$title, ${subtitle.orEmpty()}, $countText" }
            .padding(vertical = s.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(RowStampSlot), contentAlignment = Alignment.Center) { stamp() }
        Spacer(Modifier.width(s.s))
        Column(Modifier.weight(1f)) {
            Text(title, style = t.albumName, color = c.textHigh, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = t.label, color = c.textMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(s.xs))
        HandNote(stringResource(R.string.map_count_note, count), style = t.noteLarge)
    }
}

private const val StatsSeed = 41L
private const val EmptySeed = 42L
private const val UnplacedSeed = 43L
/** More pins than this crowd the world map; the rest still show as red countries and dots. */
internal const val MaxPins = 12
private const val DescribedCountries = 8
private val Dot = 2.5.dp
private val DotRing = 3.5.dp
private val PinWidth = 30.dp
private val PinGap = 2.dp
/** How far above its spot a pin may rest (big countries: Russia, Canada…). */
private val PinMaxLift = 24.dp
private val RowStampWidth = 40.dp
private val RowStampSlot = 52.dp

/* ---------------------------------------------------------------- previews */

internal fun previewMapCountries(): List<MapCountry> {
    val base = previewStamps(6)
    fun at(i: Int, city: String, country: String, cc: String, lat: Double, lon: Double) =
        base[i].copy(id = "m$i$city", place = Place(city, country, cc, lat, lon), takenAt = Instant.ofEpochSecond(i * 1000L))
    return groupByPlace(
        listOf(
            at(0, "Kyoto", "Japan", "JP", 35.01, 135.77),
            at(1, "Tokyo", "Japan", "JP", 35.69, 139.69),
            at(2, "Lisbon", "Portugal", "PT", 38.72, -9.14),
            at(3, "Jakarta", "Indonesia", "ID", -6.2, 106.85),
            at(4, "Jakarta", "Indonesia", "ID", -6.2, 106.85),
        ),
    )
}

@PaperPreview
@Composable
private fun MapContentPreview() {
    StampAppTheme {
        MapContent(
            MapUiState(loaded = true, countries = previewMapCountries(), unplaced = 3),
            PaddingValues(),
            onOpenCountry = {},
            onStampIt = {},
            onShare = {},
        )
    }
}

@PaperPreview
@Composable
private fun MapEmptyPreview() {
    StampAppTheme {
        MapContent(MapUiState(loaded = true, placeOn = false), PaddingValues(), onOpenCountry = {}, onStampIt = {}, onShare = {})
    }
}

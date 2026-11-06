package com.goomistudio.postmory.ui.screens.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goomistudio.postmory.R
import com.goomistudio.postmory.data.map.EqualEarth
import com.goomistudio.postmory.data.map.WorldOutline
import com.goomistudio.postmory.ui.components.CircleIconButton
import com.goomistudio.postmory.ui.components.GrainedSurface
import com.goomistudio.postmory.ui.components.HandNote
import com.goomistudio.postmory.ui.components.InkStrength
import com.goomistudio.postmory.ui.components.MapWindow
import com.goomistudio.postmory.ui.components.PageStamp
import com.goomistudio.postmory.ui.components.Tilt
import com.goomistudio.postmory.ui.components.TitleWithNote
import com.goomistudio.postmory.ui.components.VisaStamp
import com.goomistudio.postmory.ui.components.WorldMap
import com.goomistudio.postmory.ui.components.rememberDateText
import com.goomistudio.postmory.ui.theme.PaperPreview
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme

/** One country (Map tab): its visa over a close-up map, then its places, each with its stamps. */
@Composable
fun CountryScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenStamp: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CountryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CountryContent(state, contentPadding, onBack, onOpenStamp, modifier, onRenamePlace = viewModel::renamePlace)
}

@Composable
fun CountryContent(
    state: CountryUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenStamp: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRenamePlace: (MapPlace, String) -> Unit = { _, _ -> },
) {
    val s = StampTheme.spacing
    val country = state.country
    val name = countryName(state.code, country?.postmarkName)
    // Keyed by the place, so the sheet survives rotation / Android closing the app.
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    country?.places?.firstOrNull { it.key == renaming }?.let { place ->
        PlaceRenameSheet(place, name, onSave = { onRenamePlace(place, it) }, onDismiss = { renaming = null })
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(Columns),
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = s.screen,
            end = s.screen,
            top = s.l,
            // No nav bar on this page: clear the system gesture bar instead.
            bottom = maxOf(contentPadding.calculateBottomPadding(), WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()) + s.l,
        ),
        horizontalArrangement = Arrangement.spacedBy(s.s),
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "header") { CountryHeader(name, country, onBack) }
        if (country == null) {
            if (state.loaded) {
                item(span = { GridItemSpan(maxLineSpan) }, contentType = "gone") {
                    HandNote(stringResource(R.string.map_country_gone), strength = InkStrength.Soft, rotation = Tilt.note(GoneSeed))
                }
            }
            return@LazyVerticalGrid
        }
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "map") { CountryMap(name, country, state.outline) }
        country.places.forEach { place ->
            item(key = "h" + place.key, span = { GridItemSpan(maxLineSpan) }, contentType = "place") {
                PlaceHeading(place, name, onRename = { renaming = place.key })
            }
            items(place.stamps, key = { it.id }, contentType = { "stamp" }) { stamp ->
                PageStamp(
                    stamp,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(CellAspect)
                        .clickable(role = Role.Button) { onOpenStamp(stamp.id) }
                        .padding(s.xxs),
                )
            }
        }
    }
}

@Composable
private fun CountryHeader(name: String, country: MapCountry?, onBack: () -> Unit) {
    val s = StampTheme.spacing
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(s.xs)) {
        Row(Modifier.padding(bottom = s.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.s)) {
            CircleIconButton(R.drawable.ic_back, stringResource(R.string.cd_back), onBack)
            Text(stringResource(R.string.map_title).uppercase(locale), style = StampTheme.type.overline, color = StampTheme.colors.textMedium)
        }
        TitleWithNote(
            note = country?.let {
                {
                    HandNote(
                        pluralStringResource(R.plurals.album_stamp_count, it.stampCount, it.stampCount),
                        style = StampTheme.type.noteSmall,
                        rotation = Tilt.note(it.code.hashCode().toLong()),
                        modifier = Modifier.padding(bottom = s.xxs),
                    )
                }
            },
        ) {
            Text(name, style = StampTheme.type.screenTitle, color = StampTheme.colors.textHigh)
        }
    }
}

/** Close-up of the country on kraft (its places as red dots), its visa stamped over the corner. */
@Composable
private fun CountryMap(name: String, country: MapCountry, outline: WorldOutline?) {
    val s = StampTheme.spacing
    val since = rememberDateText(R.string.pattern_passport_since, country.firstDay)
    val stamps = pluralStringResource(R.plurals.album_stamp_count, country.stampCount, country.stampCount)
    val places = pluralStringResource(R.plurals.map_place_count, country.places.size, country.places.size)
    val window = remember(country, outline) { outline?.let { windowFor(country, it) } }
    val mapDescription = stringResource(R.string.map_country_cd, name)
    Box(Modifier.fillMaxWidth().padding(top = s.m, bottom = VisaOverhang)) {
        GrainedSurface(StampTheme.colors.kraft, Modifier.fillMaxWidth().aspectRatio(MapAspect), shape = StampTheme.shapes.card) {
            if (outline != null && window != null) {
                val dot = StampTheme.colors.ink
                val ring = StampTheme.colors.paperWhite
                WorldMap(
                    outline,
                    window,
                    visited = setOf(country.code),
                    modifier = Modifier.fillMaxSize().semantics { contentDescription = mapDescription },
                ) {
                    country.places.forEach { place ->
                        val lat = place.latitude ?: return@forEach
                        val o = window.place(lat, place.longitude!!, size)
                        drawCircle(ring, DotRing.toPx(), o)
                        drawCircle(dot, Dot.toPx(), o)
                    }
                }
            }
        }
        VisaStamp(
            code = country.code,
            name = name,
            lines = listOf(stringResource(R.string.visa_first, since), "$stamps · $places"),
            description = stringResource(R.string.visa_cd, name, since, stamps, places),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = -s.xs, y = VisaOverhang),
            tilt = Tilt.postmark(country.code.hashCode().toLong()) / 2,
        )
    }
}

/**
 * The close-up: the land the stamps were made on (the country's islands or parts that hold a
 * place; all of it when no place has coordinates), with a margin, at the card's shape.
 */
private fun windowFor(country: MapCountry, outline: WorldOutline): MapWindow {
    val shape = outline[country.code]
    val points = country.places.mapNotNull { p ->
        p.latitude?.let { lat -> EqualEarth.x(p.longitude!!, lat).toFloat() to EqualEarth.y(lat).toFloat() }
    }
    val boxes = buildList {
        if (shape != null) {
            val holding = shape.ringBounds.filter { b -> points.any { (x, y) -> x in b[0]..b[2] && y in b[1]..b[3] } }
            addAll(holding.ifEmpty { if (points.isEmpty()) shape.ringBounds else emptyList() })
        }
        points.forEach { (x, y) -> add(floatArrayOf(x, y, x, y)) }
    }
    if (boxes.isEmpty()) return MapWindow.World
    val union = floatArrayOf(boxes.minOf { it[0] }, boxes.minOf { it[1] }, boxes.maxOf { it[2] }, boxes.maxOf { it[3] })
    return MapWindow.around(union, MapAspect)
}

@Composable
private fun PlaceHeading(place: MapPlace, countryName: String, onRename: () -> Unit) {
    val s = StampTheme.spacing
    val since = rememberDateText(R.string.pattern_passport_since, place.firstDay)
    val title = place.city ?: stringResource(R.string.map_somewhere_in, countryName)
    Row(Modifier.fillMaxWidth().padding(top = s.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.xs)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = StampTheme.type.albumName, color = StampTheme.colors.textHigh)
            Text(stringResource(R.string.map_since, since), style = StampTheme.type.label, color = StampTheme.colors.textMedium)
        }
        HandNote(stringResource(R.string.map_count_note, place.stamps.size), style = StampTheme.type.noteLarge)
        CircleIconButton(R.drawable.ic_edit, stringResource(R.string.map_rename_cd, title), onRename)
    }
}

private const val Columns = 3
private const val CellAspect = 0.85f
private const val MapAspect = 1.5f
private const val GoneSeed = 44L
private val VisaOverhang = 56.dp
private val Dot = 3.dp
private val DotRing = 4.5.dp

@PaperPreview
@Composable
private fun CountryContentPreview() {
    StampAppTheme {
        val country = groupByPlace(
            com.goomistudio.postmory.ui.components.previewStamps(4).map {
                it.copy(place = com.goomistudio.postmory.domain.model.Place("Kyoto", "Japan", "JP", 35.01, 135.77))
            },
        ).first()
        CountryContent(CountryUiState(loaded = true, code = "JP", country = country), PaddingValues(), onBack = {}, onOpenStamp = {})
    }
}

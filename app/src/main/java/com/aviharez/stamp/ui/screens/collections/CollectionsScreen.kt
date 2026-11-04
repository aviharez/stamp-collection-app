package com.aviharez.stamp.ui.screens.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.Album
import com.aviharez.stamp.domain.model.AlbumCategory
import com.aviharez.stamp.domain.model.AlbumCoverColor
import com.aviharez.stamp.domain.model.AlbumSummary
import com.aviharez.stamp.domain.model.CoverStamp
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.ui.components.TitleWithNote
import com.aviharez.stamp.ui.components.AlbumCover
import com.aviharez.stamp.ui.components.CircleIconButton
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.PaperChip
import com.aviharez.stamp.ui.components.RenderedStamp
import com.aviharez.stamp.ui.components.TapeColor
import com.aviharez.stamp.ui.components.Tilt
import com.aviharez.stamp.ui.components.WashiTape
import com.aviharez.stamp.ui.components.dashedOutline
import com.aviharez.stamp.ui.components.label
import com.aviharez.stamp.ui.components.pressScale
import com.aviharez.stamp.ui.components.rememberDateText
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Collections list (Album tab): filter chips, a 2-column shelf of covers, "New album". */
@Composable
fun CollectionsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onNeedPro: () -> Unit,
    modifier: Modifier = Modifier,
    /** True once after "+1 album" was earned on the paywall: open the new-album form right away. */
    openNewAlbum: Boolean = false,
    onNewAlbumOpened: () -> Unit = {},
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openNewAlbum) {
        if (openNewAlbum) {
            creating = true
            onNewAlbumOpened()
        }
    }

    CollectionsContent(
        state = state,
        contentPadding = contentPadding,
        onFilter = viewModel::setFilter,
        onBack = onBack,
        onOpenAlbum = onOpenAlbum,
        onNewAlbum = { if (state.canCreateAlbum) creating = true else onNeedPro() },
        modifier = modifier,
    )

    if (creating) {
        AlbumFormSheet(onSubmit = viewModel::create, onDismiss = { creating = false })
    }
}

@Composable
fun CollectionsContent(
    state: CollectionsUiState,
    contentPadding: PaddingValues,
    onFilter: (AlbumCategory?) -> Unit,
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onNewAlbum: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = StampTheme.spacing
    val locale = LocalConfiguration.current.locales[0]
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = s.screen,
            end = s.screen,
            top = s.l,
            bottom = contentPadding.calculateBottomPadding() + s.l,
        ),
        horizontalArrangement = Arrangement.spacedBy(s.m),
        verticalArrangement = Arrangement.spacedBy(s.l),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(s.xs)) {
                // Back to Home (Collections is opened from Home's "All albums").
                Row(
                    Modifier.padding(bottom = s.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s.s),
                ) {
                    CircleIconButton(
                        icon = R.drawable.ic_back,
                        contentDescription = stringResource(R.string.cd_back),
                        onClick = onBack,
                    )
                    Text(stringResource(R.string.collections_overline).uppercase(locale), style = StampTheme.type.overline)
                }
                TitleWithNote(
                    note = if (state.loaded && state.albums.isNotEmpty()) {
                        {
                        HandNote(
                            stringResource(
                                R.string.collections_stats,
                                pluralStringResource(R.plurals.collections_album_count, state.albums.size, state.albums.size),
                                pluralStringResource(R.plurals.album_stamp_count, state.stampCount, state.stampCount),
                            ),
                            style = StampTheme.type.noteSmall,
                            rotation = Tilt.note(StatsSeed),
                            modifier = Modifier.padding(bottom = s.xxs),
                        )
                        }
                    } else {
                        null
                    },
                ) {
                    Text(stringResource(R.string.collections_title), style = StampTheme.type.screenTitle)
                }
                Row(
                    Modifier
                        .padding(top = s.s)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(s.xs),
                ) {
                    PaperChip(stringResource(R.string.collections_filter_all), state.filter == null, { onFilter(null) })
                    state.filters.forEach { category ->
                        PaperChip(stringResource(category.label), state.filter == category, { onFilter(category) })
                    }
                }
            }
        }

        items(state.shown, key = { it.album.id }, contentType = { "cover" }) { summary ->
            CollectionCover(summary, state.today, onClick = { onOpenAlbum(summary.album.id) })
        }

        if (state.loaded && state.shown.isEmpty() && state.filter != null) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                HandNote(
                    stringResource(R.string.collections_empty_filter),
                    strength = InkStrength.Soft,
                    rotation = Tilt.note(EmptySeed),
                )
            }
        }

        if (state.loaded) {
            item(contentType = "new") { NewAlbumTile(onNewAlbum) }
        }
    }
}

@Composable
private fun CollectionCover(summary: AlbumSummary, today: LocalDate, onClick: () -> Unit) {
    val s = StampTheme.spacing
    val album = summary.album
    val source = remember { MutableInteractionSource() }
    val scale by pressScale(source)
    val count = pluralStringResource(R.plurals.album_stamp_count, summary.stampCount, summary.stampCount)
    val latest = summary.latest
    val note = if (latest == null) {
        stringResource(R.string.collections_empty_cover)
    } else {
        stringResource(R.string.collections_cover_note, count, updatedText(latest.takenAt, today))
    }

    Column(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(s.xs),
    ) {
        AlbumCover(album.cover, Modifier.fillMaxWidth().aspectRatio(CoverAspect), name = album.name) {
            if (latest != null) {
                val seed = album.id
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = s.m, end = s.m)
                        .fillMaxWidth(CoverStampWidth),
                ) {
                    RenderedStamp(
                        file = File(latest.imagePath),
                        shape = latest.shape,
                        perforation = latest.perforation,
                        tilt = Tilt.stamp(seed),
                        contentDescription = null,
                    )
                    if (seed % 2 == 0L) {
                        WashiTape(
                            Modifier.align(Alignment.TopCenter).offset(y = -TapeOverlap),
                            color = TapeColor.forSeed(seed / 2),
                            rotation = Tilt.tape(seed),
                            width = CoverTapeWidth,
                        )
                    }
                }
            }
        }
        HandNote(note, style = StampTheme.type.noteSmall, strength = InkStrength.Soft, maxLines = 2)
    }
}

@Composable
private fun updatedText(takenAt: Instant, today: LocalDate): String {
    val day = remember(takenAt) { takenAt.atZone(ZoneId.systemDefault()).toLocalDate() }
    return when (day) {
        today -> stringResource(R.string.collections_updated_today)
        today.minusDays(1) -> stringResource(R.string.collections_updated_yesterday)
        else -> stringResource(R.string.collections_updated_on, rememberDateText(R.string.pattern_short_date, day))
    }
}

@Composable
private fun NewAlbumTile(onClick: () -> Unit) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(CoverAspect)
            .clip(StampTheme.shapes.tile)
            .dashedOutline(c.textLow)
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(s.xs, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_plus), null, tint = c.textHigh, modifier = Modifier.size(s.l))
        Text(stringResource(R.string.album_new), style = StampTheme.type.button, color = c.textHigh)
    }
}

private const val CoverAspect = 0.82f
private const val CoverStampWidth = 0.46f
private val TapeOverlap = 5.dp
private val CoverTapeWidth = 40.dp
private const val StatsSeed = 11L
private const val EmptySeed = 5L

@Preview(showBackground = true, backgroundColor = 0xFFF6F0E6, heightDp = 800)
@Composable
private fun CollectionsScreenPreview() {
    val today = LocalDate.of(2026, 11, 4)
    fun summary(id: Long, name: String, category: AlbumCategory, cover: AlbumCoverColor, count: Int, daysAgo: Long?) =
        AlbumSummary(
            Album(id, name, category, cover, Instant.EPOCH),
            count,
            daysAgo?.let {
                CoverStamp(
                    "preview-$id.png",
                    if (id % 3 == 0L) StampShape.Square else StampShape.Portrait,
                    PerforationStyle.Classic,
                    today.minusDays(it).atStartOfDay(ZoneId.systemDefault()).toInstant(),
                )
            },
        )
    StampAppTheme {
        CollectionsContent(
            state = CollectionsUiState(
                loaded = true,
                albums = listOf(
                    summary(1, "Japan trip", AlbumCategory.Trips, AlbumCoverColor.Ink, 42, 0),
                    summary(2, "Little things", AlbumCategory.Everyday, AlbumCoverColor.Kraft, 86, 1),
                    summary(3, "Mochi the cat", AlbumCategory.Pets, AlbumCoverColor.Sage, 27, 20),
                    summary(4, "Bali 2025", AlbumCategory.Trips, AlbumCoverColor.Red, 0, null),
                ),
                today = today,
            ),
            contentPadding = PaddingValues(),
            onFilter = {},
            onBack = {},
            onOpenAlbum = {},
            onNewAlbum = {},
        )
    }
}

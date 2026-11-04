package com.aviharez.stamp.ui.screens.album

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.Album
import com.aviharez.stamp.domain.model.AlbumCategory
import com.aviharez.stamp.domain.model.AlbumCoverColor
import com.aviharez.stamp.domain.model.AlbumPages
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.ui.components.CircleIconButton
import com.aviharez.stamp.ui.components.GrainedSurface
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.PageStamp
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.components.PillStyle
import com.aviharez.stamp.ui.components.Postmark
import com.aviharez.stamp.ui.components.TapeColor
import com.aviharez.stamp.ui.components.Tilt
import com.aviharez.stamp.ui.components.dashedOutline
import com.aviharez.stamp.ui.components.pageNote
import com.aviharez.stamp.ui.components.previewStamps
import com.aviharez.stamp.ui.components.rememberPostmarkLines
import com.aviharez.stamp.ui.components.seed
import com.aviharez.stamp.ui.screens.collections.AlbumDraft
import com.aviharez.stamp.ui.screens.collections.AlbumFormSheet
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.tintedShadow
import java.time.Instant

/** One album: its pages of stamps (swipe between pages), edit via "…", next-stamp slot. */
@Composable
fun AlbumPageScreen(
    onBack: () -> Unit,
    onNextStamp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumPageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }

    AlbumPageContent(
        state = state,
        onBack = onBack,
        onEdit = { editing = true },
        onNextStamp = { viewModel.takeNextStamp(onNextStamp) },
        modifier = modifier,
    )

    val album = state.album
    if (editing && album != null) {
        AlbumFormSheet(
            onSubmit = viewModel::edit,
            onDismiss = { editing = false },
            initial = AlbumDraft(album.name, album.category, album.cover),
        )
    }
}

@Composable
fun AlbumPageContent(
    state: AlbumPageUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onNextStamp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(vertical = s.m),
        verticalArrangement = Arrangement.spacedBy(s.m),
    ) {
        // Wait for the data, so the pager can open on the newest page.
        if (state.loaded) {
            val pagerState = rememberPagerState(initialPage = state.pages.lastIndex) { state.pages.size }

            Box(Modifier.fillMaxWidth().padding(horizontal = s.screenFullBleed)) {
                CircleIconButton(
                    icon = R.drawable.ic_back,
                    contentDescription = stringResource(R.string.cd_back),
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Text(
                    stringResource(R.string.album_page_of, pagerState.currentPage + 1, state.pages.size).uppercase(locale),
                    style = StampTheme.type.overline,
                    color = c.textMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
                if (state.album != null) {
                    CircleIconButton(
                        icon = R.drawable.ic_more,
                        contentDescription = stringResource(R.string.album_more_cd),
                        onClick = onEdit,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }

            Row(
                Modifier.padding(horizontal = s.screen),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(s.s),
            ) {
                Text(
                    state.album?.name.orEmpty(),
                    style = StampTheme.type.screenTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                HandNote(
                    stringResource(
                        R.string.album_stats,
                        pluralStringResource(R.plurals.album_stamp_count, state.stampCount, state.stampCount),
                        pluralStringResource(R.plurals.album_day_count, state.dayCount, state.dayCount),
                    ),
                    style = StampTheme.type.noteLarge,
                    rotation = Tilt.note(state.album?.id ?: 0L),
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = s.xxs),
                )
            }

            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = s.screenFullBleed),
                pageSpacing = s.s,
                beyondViewportPageCount = 1,
                modifier = Modifier.weight(1f),
            ) { index ->
                AlbumSheet(
                    stamps = state.pages[index],
                    hasNextSlot = index == state.pages.lastIndex,
                    onNextStamp = onNextStamp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        // Map (milestone 5) and page export (milestone 3) are shown but not live yet.
        Row(
            Modifier
                .padding(horizontal = s.screenFullBleed)
                .alpha(DisabledAlpha),
            horizontalArrangement = Arrangement.spacedBy(s.s),
        ) {
            PillButton(
                stringResource(R.string.album_map),
                onClick = {},
                enabled = false,
                style = PillStyle.Outline,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                stringResource(R.string.album_share_page),
                onClick = {},
                enabled = false,
                style = PillStyle.Ink,
                modifier = Modifier.weight(2f),
            )
        }
    }
}

/** One kraft page: a 3 × 3 grid of stamps with hand-written dates and one postmark. */
@Composable
private fun AlbumSheet(
    stamps: List<Stamp>,
    hasNextSlot: Boolean,
    onNextStamp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val shape = StampTheme.shapes.card
    GrainedSurface(
        color = c.kraft,
        shape = shape,
        modifier = modifier.tintedShadow(Elevation.Card, shape, c.shadowWarm),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = s.xs, vertical = s.m)) {
            val cellW = maxWidth / Columns
            val cellH = maxHeight / Rows
            Column(Modifier.fillMaxSize()) {
                repeat(Rows) { row ->
                    Row(Modifier.weight(1f)) {
                        repeat(Columns) { col ->
                            val i = row * Columns + col
                            val cell = Modifier.weight(1f).fillMaxSize()
                            when {
                                i < stamps.size -> StampCell(stamps[i], cell)
                                i == stamps.size && hasNextSlot -> NextStampCell(onNextStamp, cell)
                                else -> Spacer(cell)
                            }
                        }
                    }
                }
            }
            PagePostmark(stamps, cellW, cellH)
        }
    }
}

@Composable
private fun StampCell(stamp: Stamp, modifier: Modifier) {
    Column(modifier.padding(horizontal = StampTheme.spacing.xxs), horizontalAlignment = Alignment.CenterHorizontally) {
        PageStamp(
            stamp,
            Modifier
                .weight(1f)
                .fillMaxWidth(StampWidthFraction)
                .padding(top = StampTheme.spacing.xs),
            tape = if (Math.floorMod(stamp.seed, TapeEvery) == 0L) TapeColor.forSeed(stamp.seed / TapeEvery) else null,
            // Sit on the date line, like a stamp stuck just above its note.
            alignment = Alignment.BottomCenter,
        )
        HandNote(
            stamp.pageNote(),
            style = StampTheme.type.noteSmall,
            rotation = Tilt.note(stamp.seed),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = StampTheme.spacing.xxs),
        )
    }
}

@Composable
private fun NextStampCell(onClick: () -> Unit, modifier: Modifier) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val description = stringResource(R.string.album_next_stamp_cd)
    Column(modifier.padding(horizontal = s.xxs), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(StampWidthFraction)
                .padding(top = s.xs),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .aspectRatio(NextSlotAspect)
                    .clip(StampTheme.shapes.plate)
                    .dashedOutline(c.textLow, cornerRadius = 4.dp)
                    .clickable(role = Role.Button, onClick = onClick)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_plus), null, tint = c.textMedium, modifier = Modifier.size(s.l))
            }
        }
        HandNote(
            stringResource(R.string.album_next_stamp),
            style = StampTheme.type.noteSmall,
            strength = InkStrength.Soft,
            maxLines = 1,
            modifier = Modifier.padding(top = s.xxs),
        )
    }
}

/**
 * One postmark per page, cancelling a stamp that has a neighbour on its right (the waves run
 * over that neighbour, never off the page or over the "next stamp…" slot) — preferably one with
 * a city. Without such a pair, the first stamp gets a ring without waves.
 */
@Composable
private fun PagePostmark(stamps: List<Stamp>, cellW: Dp, cellH: Dp) {
    if (stamps.isEmpty()) return
    val paired = stamps.indices.filter { it % Columns < Columns - 1 && it + 1 < stamps.size }
    val index = paired.firstOrNull { stamps[it].place.hasName } ?: paired.firstOrNull() ?: 0
    val waves = index in paired
    val stamp = stamps[index]
    val row = index / Columns
    val col = index % Columns
    val d = min(cellW * PostmarkCellFraction, PostmarkMax)
    val lines = rememberPostmarkLines(stamp.place, stamp.day)
    Postmark(
        city = lines.top,
        date = lines.bottom,
        diameter = d,
        showWaves = waves,
        rotation = Tilt.postmark(stamp.seed),
        modifier = Modifier.offset(
            x = cellW * (col + 1) - d * PostmarkOverlap - (if (waves) 0.dp else d * PostmarkInset),
            y = cellH * row + cellH * PostmarkCellY - d / 2,
        ),
    )
}

private const val Columns = 3
private const val Rows = AlbumPages.SlotsPerPage / Columns
private const val StampWidthFraction = 0.84f
private const val NextSlotAspect = 3f / 4f
private const val TapeEvery = 3L
private const val PostmarkCellFraction = 0.62f
private val PostmarkMax = 72.dp
private const val PostmarkOverlap = 0.5f

/** Without waves, pull the ring back onto its own stamp. */
private const val PostmarkInset = 0.3f
private const val PostmarkCellY = 0.74f
private const val DisabledAlpha = 0.48f

@Preview(showBackground = true, backgroundColor = 0xFFF6F0E6, heightDp = 800)
@Composable
private fun AlbumPageScreenPreview() {
    val stamps = previewStamps(8)
    StampAppTheme {
        AlbumPageContent(
            state = AlbumPageUiState(
                loaded = true,
                album = Album(1, "Japan trip", AlbumCategory.Trips, AlbumCoverColor.Ink, Instant.EPOCH),
                pages = listOf(stamps),
                stampCount = stamps.size,
                dayCount = 8,
            ),
            onBack = {},
            onEdit = {},
            onNextStamp = {},
        )
    }
}

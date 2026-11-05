package com.aviharez.stamp.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.Album
import com.aviharez.stamp.domain.model.AlbumCategory
import com.aviharez.stamp.domain.model.AlbumCoverColor
import com.aviharez.stamp.domain.model.AlbumSummary
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.domain.usecase.Streak
import com.aviharez.stamp.ui.components.AirmailEnvelope
import com.aviharez.stamp.ui.components.CircleIconButton
import com.aviharez.stamp.ui.components.GrainedSurface
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.PageStamp
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.components.Postmark
import com.aviharez.stamp.ui.components.StreakChip
import com.aviharez.stamp.ui.components.TapeColor
import com.aviharez.stamp.ui.components.Tilt
import com.aviharez.stamp.ui.components.pageNote
import com.aviharez.stamp.ui.components.previewStamps
import com.aviharez.stamp.ui.components.rememberDateText
import com.aviharez.stamp.ui.components.rememberPostmarkLines
import com.aviharez.stamp.ui.components.seed
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.tintedShadow
import java.time.Instant

/** Home (Album tab): date + streak, the newest album page, and today's stamp card. */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onStampIt: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(state, contentPadding, onStampIt, onOpenCollections, onOpenAlbum, modifier)
}

@Composable
fun HomeContent(
    state: HomeUiState,
    contentPadding: PaddingValues,
    onStampIt: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = StampTheme.spacing
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(contentPadding)
            .padding(top = s.l, bottom = s.m),
        verticalArrangement = Arrangement.spacedBy(s.l),
    ) {
        Column(Modifier.padding(horizontal = s.screen), verticalArrangement = Arrangement.spacedBy(s.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rememberDateText(R.string.pattern_overline_date).uppercase(locale),
                    style = StampTheme.type.overline,
                    modifier = Modifier.weight(1f),
                )
                if (state.streak.current > 0) StreakChip(state.streak, compact = true)
            }
            Text(stringResource(R.string.home_title), style = StampTheme.type.screenTitle)
        }

        AlbumPage(
            state = state,
            onOpenCollections = onOpenCollections,
            onOpenAlbum = onOpenAlbum,
            modifier = Modifier
                .padding(horizontal = s.screenFullBleed)
                .fillMaxWidth()
                .weight(1f),
        )

        AirmailEnvelope(Modifier.padding(horizontal = s.screenFullBleed).fillMaxWidth()) {
            val today = state.today
            if (today == null) {
                WaitingCard(onStampIt)
            } else {
                DoneCard(today, state.pageAlbum?.album?.name.orEmpty(), onStampIt, onOpen = { onOpenAlbum(today.albumId) })
            }
        }
    }
}

/** The kraft page: the newest album's latest stamps in a hand-placed collage. */
@Composable
private fun AlbumPage(
    state: HomeUiState,
    onOpenCollections: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val locale = LocalConfiguration.current.locales[0]
    val album = state.pageAlbum?.album
    val shape = StampTheme.shapes.card
    val openLabel = album?.let { stringResource(R.string.home_page_cd, it.name) }

    GrainedSurface(
        color = c.kraft,
        shape = shape,
        modifier = modifier
            .rotate(PageTilt)
            .tintedShadow(Elevation.Card, shape, c.shadowWarm)
            .then(
                if (album != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = openLabel) { onOpenAlbum(album.id) }
                } else {
                    Modifier
                },
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = s.m, end = s.xs, top = s.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    album?.let { stringResource(R.string.home_page_label, it.name, state.pageNumber).uppercase(locale) }.orEmpty(),
                    style = StampTheme.type.overline,
                    color = c.textMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AllAlbumsLink(onOpenCollections)
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(start = s.xs, end = s.xs, bottom = s.m)) {
                if (state.loaded && state.pageStamps.isEmpty()) {
                    HandNote(
                        stringResource(R.string.home_empty_page),
                        rotation = Tilt.note(EmptySeed),
                        strength = InkStrength.Soft,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    Collage(state.pageStamps)
                }
            }
        }
    }
}

/** Fractions of the collage area: where each stamp (newest first) sits. */
private data class Slot(val x: Float, val y: Float, val w: Float, val h: Float, val tape: TapeColor? = null)

private val Slots = listOf(
    Slot(0.03f, 0.02f, 0.44f, 0.56f, TapeColor.Red),
    Slot(0.54f, 0.04f, 0.34f, 0.44f),
    Slot(0.33f, 0.56f, 0.30f, 0.38f, TapeColor.Cobalt),
    Slot(0.66f, 0.54f, 0.30f, 0.34f),
)

@Composable
private fun Collage(stamps: List<Stamp>) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        stamps.take(Slots.size).forEachIndexed { i, stamp ->
            val slot = Slots[i]
            PageStamp(
                stamp,
                Modifier
                    .offset(x = w * slot.x, y = h * slot.y)
                    .size(w * slot.w, h * slot.h),
                tape = slot.tape,
            )
        }

        // Hand notes under the newest stamp and the fourth one, as in the design.
        stamps.getOrNull(0)?.let { newest ->
            HandNote(
                newest.pageNote(withDate = false),
                rotation = Tilt.note(newest.seed),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .offset(x = w * NoteX, y = h * (Slots[0].y + Slots[0].h) + StampTheme.spacing.xs)
                    .width(w * (Slots[2].x - NoteX)),
            )
            val d = min(w * PostmarkWidthFraction, PostmarkMax)
            val lines = rememberPostmarkLines(newest.place, newest.day)
            Postmark(
                city = lines.top,
                date = lines.bottom,
                diameter = d,
                rotation = Tilt.postmark(newest.seed),
                modifier = Modifier.offset(x = w * PostmarkCentreX - d / 2, y = h * PostmarkCentreY - d / 2),
            )
        }
        stamps.getOrNull(3)?.let { fourth ->
            HandNote(
                fourth.pageNote(),
                style = StampTheme.type.noteSmall,
                rotation = Tilt.note(fourth.seed),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .offset(x = w * Slots[3].x, y = h * (Slots[3].y + Slots[3].h))
                    .width(w * Slots[3].w),
            )
        }
    }
}

@Composable
private fun AllAlbumsLink(onClick: () -> Unit) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val locale = LocalConfiguration.current.locales[0]
    Row(
        Modifier
            .clip(StampTheme.shapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = s.touchTarget)
            .padding(horizontal = s.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.xxs),
    ) {
        Text(stringResource(R.string.home_all_albums).uppercase(locale), style = StampTheme.type.overline, color = c.textMedium)
        Icon(painterResource(R.drawable.ic_arrow_right), null, tint = c.textMedium, modifier = Modifier.size(s.m))
    }
}

@Composable
private fun WaitingCard(onStampIt: () -> Unit) {
    val s = StampTheme.spacing
    Row(
        Modifier.padding(s.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_today_title), style = StampTheme.type.cardTitle)
            Text(
                stringResource(R.string.home_today_body, rememberDateText(R.string.pattern_weekday)),
                style = StampTheme.type.body,
                color = StampTheme.colors.textMedium,
            )
        }
        PillButton(text = stringResource(R.string.home_stamp_it), onClick = onStampIt, leadingIcon = R.drawable.ic_camera)
    }
}

/** Once today is stamped, the envelope holds today's stamp instead. Tap it to open its album. */
@Composable
private fun DoneCard(today: Stamp, albumName: String, onStampIt: () -> Unit, onOpen: () -> Unit) {
    val s = StampTheme.spacing
    Row(
        Modifier
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = s.m, vertical = s.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.m),
    ) {
        PageStamp(today, Modifier.size(TodayStampSize), tilt = TodayStampTilt)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_today_done_title), style = StampTheme.type.cardTitle)
            Text(
                stringResource(R.string.home_today_done_body, albumName),
                style = StampTheme.type.body,
                color = StampTheme.colors.textMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CircleIconButton(
            icon = R.drawable.ic_camera,
            contentDescription = stringResource(R.string.home_stamp_another),
            onClick = onStampIt,
            containerColor = StampTheme.colors.airmailRed,
            contentColor = StampTheme.colors.textOnDarkHigh,
        )
    }
}

private const val PageTilt = -1f
private const val EmptySeed = 7L
private const val NoteX = 0.05f
private const val PostmarkCentreX = 0.47f
private const val PostmarkCentreY = 0.47f
private const val PostmarkWidthFraction = 0.21f
private val PostmarkMax = 76.dp
private val TodayStampSize = 64.dp
private const val TodayStampTilt = -4f

private fun previewState(stamps: List<Stamp>, today: Boolean) = HomeUiState(
    loaded = true,
    pageStamps = stamps,
    pageAlbum = AlbumSummary(Album(1, "Japan trip", AlbumCategory.Trips, AlbumCoverColor.Ink, Instant.EPOCH), 21, null),
    streak = Streak(12, 12, today),
    today = stamps.firstOrNull()?.takeIf { today },
)

@Preview(showBackground = true, backgroundColor = 0xFFF6F0E6, heightDp = 800)
@Composable
private fun HomeScreenPreview() {
    StampAppTheme { HomeContent(previewState(previewStamps(4), today = false), PaddingValues(), {}, {}, {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F0E6, heightDp = 800)
@Composable
private fun HomeScreenStampedTodayPreview() {
    StampAppTheme { HomeContent(previewState(previewStamps(2), today = true), PaddingValues(), {}, {}, {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F0E6, heightDp = 800)
@Composable
private fun HomeScreenEmptyPreview() {
    StampAppTheme { HomeContent(HomeUiState(loaded = true), PaddingValues(), {}, {}, {}) }
}

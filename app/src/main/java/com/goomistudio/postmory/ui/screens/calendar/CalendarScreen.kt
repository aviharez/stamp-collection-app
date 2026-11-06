package com.goomistudio.postmory.ui.screens.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goomistudio.postmory.R
import com.goomistudio.postmory.domain.model.Stamp
import com.goomistudio.postmory.domain.usecase.Streak
import com.goomistudio.postmory.ui.components.CircleIconButton
import com.goomistudio.postmory.ui.components.GrainedSurface
import com.goomistudio.postmory.ui.components.HandNote
import com.goomistudio.postmory.ui.components.InkStrength
import com.goomistudio.postmory.ui.components.PillButton
import com.goomistudio.postmory.ui.components.RenderedStamp
import com.goomistudio.postmory.ui.components.Tilt
import com.goomistudio.postmory.ui.components.dashedOutline
import com.goomistudio.postmory.ui.components.description
import com.goomistudio.postmory.ui.components.handLabel
import com.goomistudio.postmory.ui.components.pageNote
import com.goomistudio.postmory.ui.components.previewStamps
import com.goomistudio.postmory.ui.components.rememberDateText
import com.goomistudio.postmory.ui.components.seed
import com.goomistudio.postmory.ui.theme.Elevation
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme
import com.goomistudio.postmory.ui.theme.rememberReduceMotion
import com.goomistudio.postmory.ui.theme.tintedShadow
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

/** Calendar tab: the streak, and the month as a sheet of mini stamps (one per stamped day). */
@Composable
fun CalendarScreen(
    contentPadding: PaddingValues,
    onStampIt: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CalendarContent(
        state = state,
        contentPadding = contentPadding,
        onPrevious = viewModel::previousMonth,
        onNext = viewModel::nextMonth,
        onSelect = viewModel::select,
        onStampIt = onStampIt,
        onOpenAlbum = onOpenAlbum,
        modifier = modifier,
    )
}

@Composable
fun CalendarContent(
    state: CalendarUiState,
    contentPadding: PaddingValues,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onStampIt: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = StampTheme.spacing
    val locale = LocalConfiguration.current.locales[0]
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(top = s.l, bottom = s.m),
        verticalArrangement = Arrangement.spacedBy(s.m),
    ) {
        MonthHeader(state, onPrevious, onNext, Modifier.padding(horizontal = s.screen))
        StreakHero(state.streak, Modifier.padding(horizontal = s.screen))
        MonthSheet(state, firstDay, onPrevious, onNext, onSelect, Modifier.padding(horizontal = s.screenFullBleed))
        if (state.loaded) {
            DayDetail(state, onStampIt, onOpenAlbum, Modifier.padding(horizontal = s.screen))
        }
    }
}

@Composable
private fun MonthHeader(state: CalendarUiState, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val s = StampTheme.spacing
    val locale = LocalConfiguration.current.locales[0]
    val pattern = stringResource(R.string.pattern_month)
    val month = state.month
    val title = remember(month, locale, pattern) {
        DateTimeFormatter.ofPattern(pattern, locale).format(month).replaceFirstChar { it.titlecase(locale) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(s.xxs)) {
        Text(
            stringResource(R.string.calendar_overline, month.year.toString()).uppercase(locale),
            style = StampTheme.type.overline,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.xs)) {
            Text(
                title,
                style = StampTheme.type.screenTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            CircleIconButton(R.drawable.ic_back, stringResource(R.string.calendar_prev), onPrevious, enabled = state.canGoBack)
            CircleIconButton(R.drawable.ic_chevron_right, stringResource(R.string.calendar_next), onNext, enabled = state.canGoForward)
        }
    }
}

/** "🔥 12 days in a row · best 15". */
@Composable
private fun StreakHero(streak: Streak, modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val unit = pluralStringResource(R.plurals.calendar_streak_unit, streak.current)
    val sub = when {
        streak.current == 0 -> stringResource(R.string.calendar_streak_start)
        !streak.stampedToday -> stringResource(R.string.calendar_streak_keep, streak.best)
        streak.current == 1 -> stringResource(R.string.calendar_streak_tomorrow)
        streak.current >= streak.best -> stringResource(R.string.calendar_streak_best_now)
        else -> stringResource(R.string.calendar_streak_best, streak.best)
    }
    val spoken = "${streak.current} $unit, $sub"
    Row(
        modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.xs),
    ) {
        Icon(painterResource(R.drawable.ic_flame), null, tint = c.ember, modifier = Modifier.size(s.xl))
        Text(streak.current.toString(), style = StampTheme.type.screenTitle)
        Column(Modifier.padding(start = s.xxs)) {
            Text(unit, style = StampTheme.type.body, color = c.textHigh)
            Text(sub, style = StampTheme.type.label, color = c.textLow)
        }
    }
}

/** The kraft page holding the month. Swipe sideways (or use the arrows) to change month. */
@Composable
private fun MonthSheet(
    state: CalendarUiState,
    firstDay: DayOfWeek,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val shape = StampTheme.shapes.card
    val reduceMotion = rememberReduceMotion()
    val swipe = with(LocalDensity.current) { SwipeThreshold.toPx() }
    val previous by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)

    GrainedSurface(
        color = c.kraft,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .rotate(PageTilt)
            .tintedShadow(Elevation.Card, shape, c.shadowWarm)
            .pointerInput(swipe) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total > swipe) previous() else if (total < -swipe) next()
                    },
                ) { _, dx -> total += dx }
            },
    ) {
        Column(Modifier.padding(s.s), verticalArrangement = Arrangement.spacedBy(s.xs)) {
            WeekdayRow(firstDay)
            AnimatedContent(
                targetState = state.page,
                contentKey = { it.month },
                transitionSpec = { monthTransition(initialState.month, targetState.month, reduceMotion) },
                label = "month",
            ) { page ->
                MonthGrid(page, state.today, state.selected, firstDay, onSelect)
            }
        }
    }
}

private fun monthTransition(from: YearMonth, to: YearMonth, reduceMotion: Boolean): ContentTransform {
    if (reduceMotion) return fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    val dir = if (to > from) 1 else -1
    return (slideInHorizontally(tween(MonthMillis)) { it / SlideFraction * dir } + fadeIn(tween(MonthMillis))) togetherWith
        (slideOutHorizontally(tween(MonthMillis)) { -it / SlideFraction * dir } + fadeOut(tween(MonthMillis)))
}

@Composable
private fun WeekdayRow(firstDay: DayOfWeek) {
    val locale = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
        repeat(DaysInWeek) { i ->
            Text(
                firstDay.plus(i.toLong()).getDisplayName(TextStyle.NARROW, locale),
                style = StampTheme.type.label,
                color = StampTheme.colors.textMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(page: MonthPage, today: LocalDate, selected: LocalDate?, firstDay: DayOfWeek, onSelect: (LocalDate) -> Unit) {
    val weeks = remember(page.month, firstDay) { CalendarMonth.weeks(page.month, firstDay) }
    Column(verticalArrangement = Arrangement.spacedBy(StampTheme.spacing.xxs)) {
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        stamps = page.stampsByDay[day].orEmpty(),
                        inMonth = YearMonth.from(day) == page.month,
                        today = today,
                        selected = day == selected,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    stamps: List<Stamp>,
    inMonth: Boolean,
    today: LocalDate,
    selected: Boolean,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val future = day.isAfter(today)
    val isToday = day == today
    val dayText = day.dayOfMonth.toString()
    val spokenDay = rememberDateText(R.string.pattern_day_cd, day)
    val spokenCount = if (stamps.isEmpty()) {
        stringResource(R.string.calendar_no_stamps)
    } else {
        pluralStringResource(R.plurals.album_stamp_count, stamps.size, stamps.size)
    }
    val spoken = stringResource(R.string.calendar_day_cd, spokenDay, spokenCount)
        .let { if (isToday) stringResource(R.string.calendar_today_cd, it) else it }

    Box(
        modifier
            .aspectRatio(1f)
            .clip(StampTheme.shapes.tile)
            .then(if (selected) Modifier.background(c.surface2) else Modifier)
            .clickable(enabled = !future, role = Role.Button) { onSelect(day) }
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                this.selected = selected
            }
            .alpha(if (inMonth) 1f else SpillAlpha),
        contentAlignment = Alignment.Center,
    ) {
        val newest = stamps.firstOrNull()
        when {
            newest != null -> StampedDay(newest, stamps.size, dayText, isToday)
            future -> Text(dayText, style = StampTheme.type.noteSmall, color = c.textLow)
            else -> Box(
                Modifier.size(MiniStamp).dashedOutline(if (isToday) c.airmailRed else c.outline),
                contentAlignment = Alignment.Center,
            ) {
                Text(dayText, style = StampTheme.type.noteSmall, color = if (isToday) c.airmailRed else c.textLow)
            }
        }
    }
}

/** The day's newest stamp, its handwritten date, a count badge and (today) a postmark ring. */
@Composable
private fun BoxScope.StampedDay(newest: Stamp, count: Int, dayText: String, isToday: Boolean) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    Box(Modifier.size(MiniStamp), contentAlignment = Alignment.Center) {
        RenderedStamp(
            file = File(newest.imagePath),
            shape = newest.shape,
            perforation = newest.perforation,
            contentDescription = null,
            tilt = Tilt.stamp(newest.seed) * MiniTiltScale,
            elevation = Elevation.Low,
        )
    }
    if (isToday) {
        PostmarkRing(c.postmarkOnLight, Modifier.size(MiniStamp).align(Alignment.Center).offset(x = s.xs, y = -s.xxs))
    }
    Text(
        dayText,
        style = StampTheme.type.noteSmall,
        color = c.handInk,
        modifier = Modifier.align(Alignment.BottomStart).offset(y = s.xxs).rotate(Tilt.note(newest.seed)),
    )
    if (count > 1) {
        Text(
            stringResource(R.string.calendar_count, count),
            style = StampTheme.type.label,
            color = c.textOnDarkHigh,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(c.ink, StampTheme.shapes.pill)
                .padding(horizontal = s.xxs),
        )
    }
}

/** A tiny double postmark ring (outer solid, inner dashed) marking today. */
@Composable
private fun PostmarkRing(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = RingStroke.toPx()
        val r = size.minDimension / 2 - stroke
        if (r <= 0f) return@Canvas
        drawCircle(color, radius = r, style = Stroke(stroke))
        drawCircle(
            color,
            radius = r * InnerRing,
            style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 2, stroke * 2))),
        )
    }
}

/** Under the sheet: the selected day's stamps (tap one to open its album), or a nudge. */
@Composable
private fun DayDetail(state: CalendarUiState, onStampIt: () -> Unit, onOpenAlbum: (Long) -> Unit, modifier: Modifier = Modifier) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    val day = state.selected
    if (day == null) {
        HandNote(
            stringResource(R.string.calendar_pick_day),
            strength = InkStrength.Soft,
            style = StampTheme.type.noteSmall,
            rotation = Tilt.note(state.month.monthValue.toLong()),
            modifier = modifier,
        )
        return
    }
    val stamps = state.selectedStamps
    val date = rememberDateText(R.string.pattern_day_title, day)
    val title = if (stamps.isEmpty()) {
        date
    } else {
        stringResource(R.string.calendar_day_title, date, pluralStringResource(R.plurals.album_stamp_count, stamps.size, stamps.size))
    }
    Column(modifier.fillMaxWidth()) {
        Text(title, style = StampTheme.type.albumName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(
            Modifier.fillMaxWidth().height(DetailHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s.m),
        ) {
            Box(Modifier.weight(1f)) {
                when {
                    stamps.isNotEmpty() -> {
                        // Title, note or city of the newest stamp that has one (the date is already above).
                        val labelled = stamps.firstOrNull { it.handLabel != null }
                        if (labelled != null) {
                            HandNote(
                                labelled.pageNote(withDate = false),
                                strength = InkStrength.Soft,
                                style = StampTheme.type.noteSmall,
                                rotation = Tilt.note(labelled.seed),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    day == state.today -> Text(stringResource(R.string.calendar_empty_today), style = StampTheme.type.body, color = c.textMedium)
                    else -> HandNote(
                        stringResource(R.string.calendar_empty_day),
                        strength = InkStrength.Soft,
                        style = StampTheme.type.noteSmall,
                        rotation = Tilt.note(day.toEpochDay()),
                    )
                }
            }
            if (stamps.isEmpty() && day == state.today) {
                PillButton(stringResource(R.string.home_stamp_it), onStampIt, leadingIcon = R.drawable.ic_camera)
            }
            stamps.take(DetailMax).forEach { stamp ->
                RenderedStamp(
                    file = File(stamp.imagePath),
                    shape = stamp.shape,
                    perforation = stamp.perforation,
                    contentDescription = stamp.description(),
                    tilt = Tilt.stamp(stamp.seed),
                    modifier = Modifier
                        .height(DetailStamp)
                        .clickable(role = Role.Button) { onOpenAlbum(stamp.albumId) },
                )
            }
            if (stamps.size > DetailMax) {
                HandNote(stringResource(R.string.calendar_more, stamps.size - DetailMax), style = StampTheme.type.noteSmall)
            }
        }
    }
}

private const val DaysInWeek = 7
private const val PageTilt = -0.6f
private const val SpillAlpha = 0.45f
private const val MiniTiltScale = 0.5f
private const val InnerRing = 0.76f
private const val DetailMax = 3
private const val MonthMillis = 260
private const val SlideFraction = 3
private val MiniStamp = 40.dp
private val RingStroke = 1.5.dp
private val DetailStamp = 64.dp
private val DetailHeight = 80.dp
private val SwipeThreshold = 56.dp

private fun previewState(today: Boolean): CalendarUiState {
    val now = LocalDate.of(2026, 11, 4)
    val stamps = previewStamps(5).let { if (today) it else it.drop(1) }
    val page = MonthPage(YearMonth.of(2026, 11), stamps.groupBy { it.day })
    return CalendarUiState(
        loaded = true,
        today = now,
        page = page,
        selected = now,
        streak = Streak(if (today) 5 else 4, 15, today),
        firstMonth = YearMonth.of(2026, 9),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F0E6, heightDp = 800)
@Composable
private fun CalendarScreenPreview() {
    StampAppTheme { CalendarContent(previewState(today = true), PaddingValues(), {}, {}, {}, {}, {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F0E6, heightDp = 800)
@Composable
private fun CalendarScreenNotYetTodayPreview() {
    StampAppTheme { CalendarContent(previewState(today = false), PaddingValues(), {}, {}, {}, {}, {}) }
}

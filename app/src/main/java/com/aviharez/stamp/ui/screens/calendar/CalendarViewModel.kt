package com.aviharez.stamp.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aviharez.stamp.data.repo.StampRepository
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.domain.usecase.Streak
import com.aviharez.stamp.domain.usecase.StreakCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** One month of the calendar with its own stamps, so a sliding-out month keeps its content. */
data class MonthPage(
    val month: YearMonth,
    /** Stamps per day for the month and its spill-over days, newest first. */
    val stampsByDay: Map<LocalDate, List<Stamp>> = emptyMap(),
)

data class CalendarUiState(
    val loaded: Boolean = false,
    val today: LocalDate,
    val page: MonthPage,
    /** Day whose stamps are listed under the grid; null = nothing to show for this month. */
    val selected: LocalDate? = null,
    val streak: Streak = Streak.None,
    /** Month of the very first stamp (can't go back further). */
    val firstMonth: YearMonth,
) {
    val month: YearMonth get() = page.month
    val canGoBack: Boolean get() = month > firstMonth
    val canGoForward: Boolean get() = month < YearMonth.from(today)
    val selectedStamps: List<Stamp> get() = selected?.let { page.stampsByDay[it] }.orEmpty()
}

/** What the user picked: a month, and optionally a day (null = the month's default day). */
private data class Pick(val month: YearMonth, val day: LocalDate? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    stamps: StampRepository,
    private val clock: Clock,
) : ViewModel() {

    private val pick = MutableStateFlow(Pick(YearMonth.now(clock)))

    private val page = pick
        .map { it.month }
        .flatMapLatest { month ->
            stamps.observeBetween(CalendarMonth.range(month)).map { list ->
                MonthPage(month, list.groupBy { it.day }.mapValues { (_, day) -> day.reversed() })
            }
        }

    val state: StateFlow<CalendarUiState> = combine(page, pick, stamps.observeDays()) { page, pick, days ->
        val today = LocalDate.now(clock)
        val selected = pick.day?.takeIf { pick.month == page.month }
            ?: CalendarMonth.defaultSelection(page.month, today, page.stampsByDay.keys)
        CalendarUiState(
            loaded = true,
            today = today,
            page = page,
            selected = selected,
            streak = StreakCalculator.calculate(days, today),
            firstMonth = days.minOrNull()?.let(YearMonth::from)?.coerceAtMost(YearMonth.from(today)) ?: YearMonth.from(today),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState())

    private fun initialState(): CalendarUiState {
        val today = LocalDate.now(clock)
        return CalendarUiState(today = today, page = MonthPage(YearMonth.from(today)), firstMonth = YearMonth.from(today))
    }

    fun previousMonth() {
        val s = state.value
        if (s.canGoBack) pick.value = Pick(s.month.minusMonths(1))
    }

    fun nextMonth() {
        val s = state.value
        if (s.canGoForward) pick.value = Pick(s.month.plusMonths(1))
    }

    /** Selects [day]; a spill-over day from a neighbouring month opens that month. Future days are ignored. */
    fun select(day: LocalDate) {
        if (day.isAfter(LocalDate.now(clock))) return
        pick.update { Pick(YearMonth.from(day), day) }
    }
}

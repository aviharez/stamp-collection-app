package com.aviharez.stamp.ui.screens.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Pure calendar maths for the Calendar tab (unit-tested). */
object CalendarMonth {
    /** Most days a month grid can show from the neighbouring months on either side. */
    const val MaxSpill = 6L

    /**
     * Whole weeks covering [month], starting on [firstDay] (from the phone's region), including the
     * spill-over days of the previous and next month.
     */
    fun weeks(month: YearMonth, firstDay: DayOfWeek): List<List<LocalDate>> {
        val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDay))
        val end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(firstDay.minus(1)))
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .toList()
            .chunked(DaysInWeek)
    }

    /** Days whose stamps a month grid may need, whatever the first day of the week is. */
    fun range(month: YearMonth): ClosedRange<LocalDate> =
        month.atDay(1).minusDays(MaxSpill)..month.atEndOfMonth().plusDays(MaxSpill)

    /** The day shown under the grid when a month opens: today, else its last stamped day, else none. */
    fun defaultSelection(month: YearMonth, today: LocalDate, stampedDays: Collection<LocalDate>): LocalDate? =
        if (YearMonth.from(today) == month) today else stampedDays.filter { YearMonth.from(it) == month }.maxOrNull()

    private const val DaysInWeek = 7
}

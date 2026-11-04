package com.aviharez.stamp.ui

import com.aviharez.stamp.ui.screens.calendar.CalendarMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class CalendarMonthTest {
    private val november = YearMonth.of(2026, 11) // 1 Nov 2026 is a Sunday

    @Test fun mondayStartSpillsIntoOctoberAndDecember() {
        val weeks = CalendarMonth.weeks(november, DayOfWeek.MONDAY)
        assertEquals(6, weeks.size)
        assertEquals(LocalDate.of(2026, 10, 26), weeks.first().first())
        assertEquals(LocalDate.of(2026, 12, 6), weeks.last().last())
        assertTrue(weeks.all { it.size == 7 && it.first().dayOfWeek == DayOfWeek.MONDAY })
    }

    @Test fun sundayStartBeginsOnTheFirst() {
        val weeks = CalendarMonth.weeks(november, DayOfWeek.SUNDAY)
        assertEquals(5, weeks.size)
        assertEquals(LocalDate.of(2026, 11, 1), weeks.first().first())
        assertEquals(LocalDate.of(2026, 12, 5), weeks.last().last())
    }

    @Test fun rangeCoversAnyFirstDay() {
        val range = CalendarMonth.range(november)
        DayOfWeek.entries.forEach { first ->
            CalendarMonth.weeks(november, first).flatten().forEach { assertTrue("$it for $first", it in range) }
        }
    }

    @Test fun defaultSelection() {
        val today = LocalDate.of(2026, 11, 4)
        val days = listOf(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 1))
        assertEquals(today, CalendarMonth.defaultSelection(november, today, days))
        assertEquals(LocalDate.of(2026, 10, 20), CalendarMonth.defaultSelection(YearMonth.of(2026, 10), today, days))
        assertNull(CalendarMonth.defaultSelection(YearMonth.of(2026, 9), today, days))
    }
}

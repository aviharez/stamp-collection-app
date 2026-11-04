package com.aviharez.stamp.domain

import com.aviharez.stamp.domain.usecase.Streak
import com.aviharez.stamp.domain.usecase.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {
    private val today = LocalDate.of(2026, 11, 4)
    private fun daysAgo(vararg n: Long) = n.map { today.minusDays(it) }

    @Test fun noStamps() = assertEquals(Streak.None, StreakCalculator.calculate(emptyList(), today))

    @Test fun stampedTodayCountsToday() =
        assertEquals(Streak(3, 3, true), StreakCalculator.calculate(daysAgo(0, 1, 2), today))

    @Test fun notYetStampedTodayKeepsYesterdaysStreak() =
        assertEquals(Streak(2, 2, false), StreakCalculator.calculate(daysAgo(1, 2), today))

    @Test fun missedYesterdayBreaksStreak() =
        assertEquals(Streak(0, 2, false), StreakCalculator.calculate(daysAgo(2, 3), today))

    @Test fun bestIsLongestRunAnywhere() =
        assertEquals(Streak(1, 4, true), StreakCalculator.calculate(daysAgo(0, 5, 6, 7, 8, 10), today))

    @Test fun acrossMonthAndYearBoundaries() {
        val newYear = LocalDate.of(2027, 1, 1)
        val days = listOf(LocalDate.of(2026, 12, 30), LocalDate.of(2026, 12, 31), newYear)
        assertEquals(Streak(3, 3, true), StreakCalculator.calculate(days, newYear))
    }
}

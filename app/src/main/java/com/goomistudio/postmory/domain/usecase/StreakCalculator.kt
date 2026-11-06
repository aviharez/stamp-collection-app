package com.goomistudio.postmory.domain.usecase

import java.time.LocalDate

/** Current and best run of consecutive stamped days. */
data class Streak(val current: Int, val best: Int, val stampedToday: Boolean) {
    companion object {
        val None = Streak(current = 0, best = 0, stampedToday = false)
    }
}

/**
 * Streak rules: a day counts if it has at least one stamp. Today not stamped *yet* does not
 * break the streak; it breaks once a whole day passes without a stamp.
 */
object StreakCalculator {
    fun calculate(days: Collection<LocalDate>, today: LocalDate): Streak {
        if (days.isEmpty()) return Streak.None
        val sorted = days.toSortedSet()

        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in sorted) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }

        val stampedToday = today in sorted
        var cursor = if (stampedToday) today else today.minusDays(1)
        var current = 0
        while (cursor in sorted) {
            current++
            cursor = cursor.minusDays(1)
        }
        return Streak(current = current, best = best, stampedToday = stampedToday)
    }
}

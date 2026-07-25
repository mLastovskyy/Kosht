package by.mlastovsky.kosht.util

import java.time.LocalDate

object Streak {

    /**
     * Consecutive days with at least one logged record, ending today
     * (or yesterday, so the streak is not broken before the day is over).
     */
    fun compute(createdStamps: List<Long>, today: LocalDate = LocalDate.now()): Int {
        if (createdStamps.isEmpty()) return 0
        val days = createdStamps.map { Dates.toLocalDate(it) }.toHashSet()
        var cursor = today
        if (cursor !in days) cursor = cursor.minusDays(1)
        var streak = 0
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}

package by.mlastovsky.kosht.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Date helpers. All persistence uses epoch millis; conversion happens in the
 * device's current time zone.
 */
object Dates {

    fun today(): LocalDate = LocalDate.now()

    fun toEpochMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    /**
     * A sensible moment for a record the user dated [date]: today gets the
     * current time, a record kept on its own day keeps the time it had, and any
     * other day lands on noon, which no time zone can push into a neighbour.
     */
    fun momentFor(date: LocalDate, previous: Long? = null): Long {
        if (previous != null && toLocalDate(previous) == date) return previous
        return if (date == today()) {
            System.currentTimeMillis()
        } else {
            date.atTime(java.time.LocalTime.NOON).atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        }
    }

    fun monthRange(month: YearMonth): LongRange {
        val start = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        return start until end
    }
}

package by.mlastovsky.kosht.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

object Dates {

    fun today(): LocalDate = LocalDate.now()

    fun toEpochMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

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

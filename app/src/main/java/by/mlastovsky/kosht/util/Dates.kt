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

    fun monthRange(month: YearMonth): LongRange {
        val start = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        return start until end
    }
}

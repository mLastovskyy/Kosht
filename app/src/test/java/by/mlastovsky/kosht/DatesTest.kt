package by.mlastovsky.kosht

import by.mlastovsky.kosht.util.Dates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DatesTest {

    @Test
    fun `epoch millis round trip preserves the date`() {
        val date = LocalDate.of(2026, 7, 25)
        val millis = Dates.toEpochMillis(date)
        assertEquals(date, Dates.toLocalDate(millis))
    }

    @Test
    fun `month range covers every day of the month`() {
        val month = YearMonth.of(2026, 2)
        val range = Dates.monthRange(month)

        assertEquals(LocalDate.of(2026, 2, 1), Dates.toLocalDate(range.first))
        assertEquals(LocalDate.of(2026, 2, 28), Dates.toLocalDate(range.last))
    }

    @Test
    fun `month ranges do not overlap`() {
        val july = Dates.monthRange(YearMonth.of(2026, 7))
        val august = Dates.monthRange(YearMonth.of(2026, 8))
        assertEquals(july.last + 1, august.first)
    }

    @Test
    fun `first day of month maps into its own month range`() {
        val month = YearMonth.of(2026, 7)
        val range = Dates.monthRange(month)
        val firstDayMillis = Dates.toEpochMillis(month.atDay(1))
        assertTrue(firstDayMillis in range)
    }
}

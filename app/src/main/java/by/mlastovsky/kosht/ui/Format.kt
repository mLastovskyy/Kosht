package by.mlastovsky.kosht.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.util.Dates
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Today" / "Yesterday" / "25 July" / "25 July 2025". */
@Composable
fun relativeDate(date: LocalDate): String {
    val today = Dates.today()
    return when (date) {
        today -> stringResource(R.string.date_today)
        today.minusDays(1) -> stringResource(R.string.date_yesterday)
        else -> date.format(
            DateTimeFormatter.ofPattern(
                if (date.year == today.year) "d MMMM" else "d MMMM yyyy",
                Locale.getDefault()
            )
        )
    }
}

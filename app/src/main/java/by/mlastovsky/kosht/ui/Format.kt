package by.mlastovsky.kosht.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.ui.editor.formatQuantity
import by.mlastovsky.kosht.util.Dates
import by.mlastovsky.kosht.util.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

fun countedAt(quantity: Double?, amountMinor: Long, currencyCode: String): String? {
    if (quantity == null || quantity <= 0.0 || amountMinor <= 0) return null
    val unit = (amountMinor / quantity).roundToLong()
    return formatQuantity(quantity) + " × " + Money.format(unit, currencyCode)
}

@Composable
fun relativeDate(date: LocalDate): String {
    val today = Dates.today()
    val locale = LocalLocale.current.platformLocale
    return when (date) {
        today -> stringResource(R.string.date_today)
        today.minusDays(1) -> stringResource(R.string.date_yesterday)
        else -> date.format(
            DateTimeFormatter.ofPattern(
                if (date.year == today.year) "d MMMM" else "d MMMM yyyy",
                locale
            )
        )
    }
}

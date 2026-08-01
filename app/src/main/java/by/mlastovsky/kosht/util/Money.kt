package by.mlastovsky.kosht.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {

    fun format(amountMinor: Long, currencyCode: String, withSign: Boolean = false): String {
        val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
        val fractionDigits = currency?.defaultFractionDigits?.takeIf { it >= 0 } ?: 2
        val value = BigDecimal(amountMinor).movePointLeft(fractionDigits)
        val format = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }
        val symbol = currency?.getSymbol(Locale.getDefault()) ?: currencyCode
        val sign = if (withSign && amountMinor != 0L) {
            if (value.signum() >= 0) "+" else ""
        } else {
            ""
        }
        return "$sign${format.format(value)} $symbol"
    }

    fun editableText(amountMinor: Long, currencyCode: String): String {
        val digits = fractionDigits(currencyCode)
        val format = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
            isGroupingUsed = false
        }
        return format.format(BigDecimal(amountMinor).movePointLeft(digits))
    }

    fun parseToMinor(input: String, currencyCode: String): Long? {
        if (input.isBlank()) return null
        val normalized = input.replace(',', '.').trim()
        val value = normalized.toBigDecimalOrNull() ?: return null
        val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
        val fractionDigits = currency?.defaultFractionDigits?.takeIf { it >= 0 } ?: 2
        return value.movePointRight(fractionDigits)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    fun fractionDigits(currencyCode: String): Int {
        val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
        return currency?.defaultFractionDigits?.takeIf { it >= 0 } ?: 2
    }

    fun rateText(rate: Double): String =
        BigDecimal(rate).setScale(RATE_DIGITS, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    private const val RATE_DIGITS = 4
}

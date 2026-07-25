package by.mlastovsky.kosht.data.receipt

import java.time.LocalDate

data class ParsedReceipt(
    val amountMinor: Long?,
    val date: LocalDate?,
    val merchant: String?
)

/**
 * Extracts the total, date and merchant from raw receipt OCR text.
 * Pure logic — unit-testable without Android.
 */
object ReceiptParser {

    private val totalKeywords = listOf(
        "к оплате", "итого", "итог", "усяго", "всего", "сумма", "total"
    )

    private val amountRegex = Regex("""(\d[\d ]{0,8})[.,]\s?(\d{2})\b""")

    private val dateRegex = Regex("""\b(\d{2})[./-](\d{2})[./-](\d{2,4})\b""")

    fun parse(text: String): ParsedReceipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return ParsedReceipt(
            amountMinor = findTotal(lines),
            date = findDate(text),
            merchant = findMerchant(lines)
        )
    }

    private fun findTotal(lines: List<String>): Long? {
        for (keyword in totalKeywords) {
            lines.forEachIndexed { index, line ->
                if (line.contains(keyword, ignoreCase = true)) {
                    // The amount is usually on the same line, sometimes on the next.
                    val candidate = lastAmountIn(line) ?: lines.getOrNull(index + 1)
                        ?.let { lastAmountIn(it) }
                    if (candidate != null) return candidate
                }
            }
        }
        // Fallback: the largest decimal amount on the receipt.
        return lines.asSequence()
            .flatMap { amountRegex.findAll(it) }
            .map { it.toMinor() }
            .maxOrNull()
    }

    private fun lastAmountIn(line: String): Long? =
        amountRegex.findAll(line).lastOrNull()?.toMinor()

    private fun MatchResult.toMinor(): Long {
        val whole = groupValues[1].replace(" ", "")
        val fraction = groupValues[2]
        return (whole + fraction).toLong()
    }

    private fun findDate(text: String): LocalDate? {
        val today = LocalDate.now()
        return dateRegex.findAll(text)
            .mapNotNull { match ->
                val (day, month, yearRaw) = match.destructured
                val year = when (yearRaw.length) {
                    2 -> 2000 + yearRaw.toInt()
                    else -> yearRaw.toInt()
                }
                runCatching { LocalDate.of(year, month.toInt(), day.toInt()) }.getOrNull()
            }
            .firstOrNull { it <= today && it >= today.minusYears(1) }
    }

    private fun findMerchant(lines: List<String>): String? =
        lines.take(5).firstOrNull { line ->
            val letters = line.count { it.isLetter() }
            letters >= 4 &&
                letters > line.count { it.isDigit() } &&
                !line.contains(dateRegex) &&
                line.none { it in "‹›<>|" }
        }?.take(40)
}

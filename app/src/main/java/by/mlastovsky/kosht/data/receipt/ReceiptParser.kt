package by.mlastovsky.kosht.data.receipt

import java.time.LocalDate

data class ParsedReceipt(
    val amountMinor: Long?,
    val date: LocalDate?,
    val merchant: String?
)

/**
 * Extracts the total, date and merchant from receipt text — either OCR output
 * or an electronic receipt page flattened to lines.
 *
 * Pure logic, unit-testable without Android.
 */
object ReceiptParser {

    /**
     * Strongest wording first. "К оплате" is what the customer actually pays;
     * "итого" can still be a subtotal, and "сумма" appears on every line item.
     */
    private val totalKeywords = listOf(
        listOf("итого к оплате", "к оплате", "да аплаты", "к аплаце"),
        listOf("итого", "итог", "усяго", "всего"),
        listOf("сума", "сумма", "total")
    )

    /**
     * Lines that name an amount which is not the total. Without these the
     * discount, the VAT share or the cash handed over gets picked instead.
     */
    private val notTheTotal = listOf(
        "скидк", "зніжк", "знижк", "сдача", "рэшта", "наличн", "гатоўк",
        "бонус", "кэшбэк", "кешбэк", "аванс", "предоплат", "эконом", "эканом",
        "внесено", "получено", "change"
    )

    /**
     * Amounts only: two decimals, thousands optionally spaced. The guards on
     * both sides are what keep 26.07.2026 from being read as 26 rubles 07.
     */
    private val amountRegex = Regex(
        """(?<![\d.,/\-:])(\d{1,3}(?:[  ]\d{3})+|\d{1,9})[.,](\d{2})(?![\d.,/\-:])"""
    )

    private val dateRegex = Regex("""\b(\d{2})[./-](\d{2})[./-](\d{2,4})\b""")

    /**
     * Chains print their name in wildly different ways — logo line, legal
     * entity, a branch address — but the name itself is stable, so matching
     * it anywhere in the text beats guessing which line is the header.
     */
    private val knownChains = mapOf(
        "евроопт" to "Евроопт",
        "eurospar" to "EUROSPAR",
        "hit!" to "Хит!",
        "хит!" to "Хит!",
        "санта" to "Санта",
        "green" to "Green",
        "грин" to "Green",
        "гиппо" to "Гиппо",
        "корона" to "Корона",
        "виталюр" to "Виталюр",
        "алми" to "Алми",
        "соседи" to "Соседи",
        "копеечка" to "Копеечка",
        "рублёвский" to "Рублёвский",
        "рублевский" to "Рублёвский",
        "белмаркет" to "Белмаркет",
        "доброном" to "Доброном",
        "остров чистоты" to "Остров чистоты",
        "мила" to "Мила",
        "prostore" to "ProStore",
        "просторе" to "ProStore",
        "буслік" to "Буслік",
        "буслик" to "Буслік",
        "5 элемент" to "5 элемент",
        "электросила" to "Электросила"
    )

    /** Header lines that describe the document rather than name the shop. */
    private val notAMerchant = listOf(
        "кассовый чек", "касавы чэк", "чек", "чэк", "унп", "у н п", "инн",
        "скно", "номер", "смена", "кассир", "касір", "магазин №", "объект",
        "адрес", "адрас", "ул.", "пр-т", "просп", "г.минск", "тел", "фискальный",
        "фіскальны", "платежный", "терминал", "копия", "приход", "продажа"
    )

    private val legalForms = Regex(
        """^\s*(ООО|ОАО|ЗАО|ИП|ЧУП|ЧТУП|ТЧУП|УП|СООО|ОДО|ТДА|ГП|ЧПТУП)[\s"«'.:]*""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String): ParsedReceipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return ParsedReceipt(
            amountMinor = findTotal(lines),
            date = findDate(text),
            merchant = findMerchant(lines)
        )
    }

    // ---- Total ------------------------------------------------------------

    private fun findTotal(lines: List<String>): Long? {
        for (group in totalKeywords) {
            keywordTotal(lines, group)?.let { return it }
        }
        return fallbackTotal(lines)
    }

    private fun keywordTotal(lines: List<String>, keywords: List<String>): Long? {
        lines.forEachIndexed { index, line ->
            val lower = line.lowercase()
            if (notTheTotal.any { lower.contains(it) }) return@forEachIndexed
            val at = keywords.firstNotNullOfOrNull { keyword ->
                lower.indexOf(keyword).takeIf { it >= 0 }?.let { it + keyword.length }
            } ?: return@forEachIndexed
            // The first amount after the wording — the tail of the line often
            // carries the VAT share, which is not what was paid.
            firstAmountAfter(line, at)?.let { return it }
            // Wide receipts wrap the figure onto the following line.
            lines.getOrNull(index + 1)
                ?.takeIf { next -> notTheTotal.none { next.lowercase().contains(it) } }
                ?.let { next -> firstAmountAfter(next, 0)?.let { return it } }
        }
        return null
    }

    private fun firstAmountAfter(line: String, from: Int): Long? = amountRegex
        .findAll(line)
        .firstOrNull { it.range.first >= from }
        ?.toMinor()

    /**
     * Nothing was labelled, so fall back on where a total sits: near the end,
     * and larger than the items above it.
     */
    private fun fallbackTotal(lines: List<String>): Long? {
        val plausible = lines.withIndex()
            .filter { (_, line) -> notTheTotal.none { line.lowercase().contains(it) } }
        val tailStart = (lines.size * 0.6).toInt()
        val tail = plausible.filter { it.index >= tailStart }
        return tail.amounts().maxOrNull() ?: plausible.amounts().maxOrNull()
    }

    private fun List<IndexedValue<String>>.amounts(): List<Long> =
        flatMap { (_, line) -> amountRegex.findAll(line).map { it.toMinor() } }

    private fun MatchResult.toMinor(): Long {
        val whole = groupValues[1].replace(" ", "").replace(" ", "")
        return whole.toLong() * 100 + groupValues[2].toLong()
    }

    // ---- Date -------------------------------------------------------------

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

    // ---- Merchant ---------------------------------------------------------

    private fun findMerchant(lines: List<String>): String? =
        knownChain(lines) ?: headerLine(lines)

    private fun knownChain(lines: List<String>): String? {
        // Only the top of the receipt: "Санта" further down is a product name.
        val header = lines.take(8).joinToString(" ").lowercase()
        return knownChains.entries
            .filter { header.contains(it.key) }
            // Longest match wins, so "остров чистоты" beats a stray "мила".
            .maxByOrNull { it.key.length }
            ?.value
    }

    private fun headerLine(lines: List<String>): String? = lines.take(6)
        .asSequence()
        .map { quotedName(it) ?: it }
        .map { it.replace(legalForms, "").trim(' ', '"', '«', '»', '\'', ',', '.', ':') }
        .firstOrNull { candidate ->
            val lower = candidate.lowercase()
            val letters = candidate.count { it.isLetter() }
            letters >= 3 &&
                letters > candidate.count { it.isDigit() } &&
                notAMerchant.none { lower.contains(it) } &&
                !candidate.contains(dateRegex) &&
                candidate.none { it in "‹›<>|" }
        }
        ?.take(40)

    /** `ООО "Евроопт"` — the trade name is what is inside the quotes. */
    private fun quotedName(line: String): String? =
        Regex("""["«]([^"»]{2,40})["»]""").find(line)?.groupValues?.get(1)?.trim()
}

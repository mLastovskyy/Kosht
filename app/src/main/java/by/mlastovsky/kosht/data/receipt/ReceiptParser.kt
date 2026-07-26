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
        "фіскальны", "платежный", "терминал", "копия", "приход", "продажа",
        // Everything below sits around the total or in the footer, and each
        // one of them has been picked as a shop name at some point.
        "оплат", "аплат", "итог", "усяго", "сдача", "рэшта", "наличн", "гатоўк",
        "ндс", "пдв", "нсп", "скидк", "зніжк", "бонус", "кэшбэк", "кешбэк",
        "белкарт", "visa", "mastercard", "maestro", "эквайер", "эквайринг",
        "rrn", "код авториз", "карта", "картка", "банк", "режим работы",
        "спасибо", "дзякуй", "приятн", "гарант", "обмен", "возврат",
        "лиц.", "св-во", "www", "http", ".by", "@"
    )

    /**
     * Words that name a kind of shop rather than a shop. As a note they say
     * nothing the category does not already say.
     */
    private val genericNames = setOf(
        "магазин", "крама", "гастроном", "супермаркет", "минимаркет",
        "маркет", "товар", "товары", "тавары", "чек", "продавец"
    )

    private val legalForms = Regex(
        """^\s*(ООО|ОАО|ЗАО|ИП|ЧУП|ЧТУП|ТЧУП|УП|СООО|ОДО|ТДА|ГП|ЧПТУП)[\s"«'.:]*""",
        RegexOption.IGNORE_CASE
    )

    /** The tax number a legal entity is always printed next to. */
    private val unpRegex = Regex("""\b(УНП|УНН|ИНН)\b""", RegexOption.IGNORE_CASE)

    private const val VOWELS = "аеёиоуыэюяіўaeiouy"

    /** How far down a shop still counts as printing its own name. */
    private const val HEADER_LINES = 12

    fun parse(text: String): ParsedReceipt = parse(ReceiptLine.of(text))

    /**
     * Same reading, but told how big each line was printed — which is what
     * decides the shop name when nothing else on the slip gives it away.
     */
    fun parse(lines: List<ReceiptLine>): ParsedReceipt {
        val cleaned = lines
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotEmpty() }
        val texts = cleaned.map { it.text }
        return ParsedReceipt(
            amountMinor = findTotal(texts),
            date = findDate(texts.joinToString("\n")),
            merchant = findMerchant(cleaned)
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

    private fun findMerchant(lines: List<ReceiptLine>): String? =
        knownChain(lines.map { it.text }) ?: bestNamedLine(lines)

    private fun knownChain(lines: List<String>): String? {
        // Only the top of the receipt: "Санта" further down is a product name.
        val header = lines.take(8).joinToString(" ").lowercase()
        return knownChains.entries
            .filter { header.contains(it.key) }
            // Longest match wins, so "остров чистоты" beats a stray "мила".
            .maxByOrNull { it.key.length }
            ?.value
    }

    private data class Candidate(val name: String, val score: Int, val index: Int)

    /**
     * The shop is not on the known list, so the header is judged rather than
     * trusted: taking the first line that merely looks like text is what used
     * to put an address, a cashier's name or OCR noise into the note. Points
     * go to the things that actually mark a name — the big print at the top,
     * a trade name in quotes, a legal form, the line beside the tax number —
     * and the best-scoring line wins, or none does.
     */
    private fun bestNamedLine(lines: List<ReceiptLine>): String? {
        val header = lines.take(HEADER_LINES)
        val unpAt = header.indexOfFirst { unpRegex.containsMatchIn(it.text) }
        return header
            .mapIndexedNotNull { index, line -> candidate(line, index, unpAt) }
            .filter { it.score >= MIN_MERCHANT_SCORE }
            // Equal scores: whichever was printed higher up.
            .maxWithOrNull(compareBy({ it.score }, { -it.index }))
            ?.name
    }

    private fun candidate(line: ReceiptLine, index: Int, unpAt: Int): Candidate? {
        val raw = line.text
        val lower = raw.lowercase()
        if (notAMerchant.any { lower.contains(it) }) return null
        if (raw.contains(dateRegex) || amountRegex.containsMatchIn(raw)) return null
        if (raw.any { it in "‹›<>|" }) return null

        val quoted = quotedName(raw)
        val hasLegalForm = legalForms.containsMatchIn(raw)
        val name = normalize(quoted ?: raw.replace(legalForms, "")) ?: return null

        var score = when {
            // `ООО "Евроторг"` — as explicit as a receipt ever gets.
            quoted != null && hasLegalForm -> 6
            quoted != null -> 4
            hasLegalForm -> 3
            else -> 0
        }
        // Shops print their own name large and everything else small.
        score += ((line.emphasis - 1f) * 8f).toInt().coerceIn(0, 8)
        // The entity behind the counter is printed right by its tax number.
        if (unpAt >= 0 && kotlin.math.abs(index - unpAt) == 1) score += 2
        if (index < 3) score += 1
        return Candidate(name, score, index)
    }

    /**
     * Trims the decoration off a candidate and rejects what cannot be a name:
     * too few letters, more digits than letters, punctuation soup left by OCR,
     * no vowel at all, or a word that just means "shop".
     */
    private fun normalize(candidate: String): String? {
        val trimmed = candidate
            .trim(' ', '"', '«', '»', '\'', ',', '.', ':', ';', '-', '—', '*', '=')
            .replace(Regex("""\s{2,}"""), " ")
        val letters = trimmed.count { it.isLetter() }
        if (letters < 3 || letters <= trimmed.count { it.isDigit() }) return null
        if (letters * 2 < trimmed.length) return null
        if (trimmed.none { it.lowercaseChar() in VOWELS }) return null
        if (trimmed.lowercase() in genericNames) return null
        return prettyCase(trimmed.take(40))
    }

    /** Receipts shout in capitals; a note reads better in ordinary case. */
    private fun prettyCase(name: String): String {
        if (name.any { it.isLowerCase() }) return name
        return name.split(' ').joinToString(" ") { word ->
            if (word.length <= 2) word else word.take(1) + word.drop(1).lowercase()
        }
    }

    /** `ООО "Евроопт"` — the trade name is what is inside the quotes. */
    private fun quotedName(line: String): String? =
        Regex("""["«]([^"»]{2,40})["»]""").find(line)?.groupValues?.get(1)?.trim()

    /** Below this nothing on the line suggests a name, so none is reported. */
    private const val MIN_MERCHANT_SCORE = 1
}

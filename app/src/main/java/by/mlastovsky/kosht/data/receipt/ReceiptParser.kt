package by.mlastovsky.kosht.data.receipt

import by.mlastovsky.kosht.util.Notes
import java.time.LocalDate

data class ParsedReceipt(
    val amountMinor: Long?,
    val date: LocalDate?,
    val merchant: String?,
    /** What the slip says was bought, when its lines can be read that way. */
    val items: List<ParsedItem> = emptyList()
)

/** One line of a receipt read as a purchase. */
data class ParsedItem(
    val name: String,
    val amountMinor: Long,
    /** 2 pieces, 0.756 kg — whatever the slip printed before the price. */
    val quantity: Double? = null
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
     *
     * The lines go through [OcrDigits] first: a price printed as `1,4О` is a
     * price, and repairing the handful of letters OCR mistakes for digits is
     * what turns an unreadable slip into a readable one — with no service,
     * no key and nothing leaving the phone.
     */
    fun parse(lines: List<ReceiptLine>): ParsedReceipt {
        val cleaned = OcrDigits.repair(lines)
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotEmpty() }
        val texts = cleaned.map { it.text }
        val total = findTotal(texts)
        return ParsedReceipt(
            amountMinor = total,
            date = findDate(texts.joinToString("\n")),
            merchant = findMerchant(cleaned),
            items = reconciled(findItems(texts), total)
        )
    }

    /**
     * The figures have to agree with the slip. Lines adding up to far more than
     * the receipt total mean something that is not a purchase was read as one —
     * and a wrong list is worse than none, so it is dropped whole.
     *
     * Adding up to *less* is normal and kept: a line the OCR could not read is
     * simply missing, and the app says as much rather than inventing a product
     * to make the arithmetic work. Slightly more is normal too — a discount
     * applied at the end sits below the items it came off.
     */
    private fun reconciled(items: List<ParsedItem>, total: Long?): List<ParsedItem> {
        if (total == null || total <= 0 || items.isEmpty()) return items
        val sum = items.sumOf { it.amountMinor }
        return if (sum > total * OVER_TOTAL_LIMIT) emptyList() else items
    }

    /** How far above the receipt total a list of lines may still be believed. */
    private const val OVER_TOTAL_LIMIT = 1.5

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

    // ---- Items ------------------------------------------------------------

    /**
     * Reads the shopping itself off the slip: everything above the total that
     * looks like "a name and what it cost".
     *
     * Two layouts cover nearly every receipt around here. Either the name and
     * the price share a line — `Хлеб 2 x 1,25 2,50` — or the name is printed on
     * its own with the arithmetic underneath it. Anything that reads as a
     * document header, a discount, a card or a footer is left out, and a line
     * that cannot be read confidently is skipped rather than guessed at: the
     * product list is optional, so half of one is worse than none.
     */
    private fun findItems(lines: List<String>): List<ParsedItem> {
        val end = lines.indexOfFirst { line ->
            val lower = line.lowercase()
            totalKeywords.take(2).any { group -> group.any { lower.contains(it) } }
        }.takeIf { it >= 0 } ?: lines.size
        val items = mutableListOf<ParsedItem>()
        var index = 0
        while (index < end && items.size < MAX_ITEMS) {
            val line = lines[index]
            if (skipAsItem(line)) {
                index++
                continue
            }
            val sameLine = itemOnOneLine(line)
            if (sameLine != null) {
                items += sameLine
                index++
                continue
            }
            // A name of its own, with the figures on the line below it.
            val next = lines.getOrNull(index + 1)
            val split = next?.takeIf { !skipAsItem(it) }?.let { itemFromPair(line, it) }
            if (split != null) {
                items += split
                index += 2
                continue
            }
            index++
        }
        return items
    }

    /** Lines that name an amount but never a purchase. */
    private fun skipAsItem(line: String): Boolean {
        val lower = line.lowercase()
        if (notTheTotal.any { lower.contains(it) }) return true
        if (notAnItem.any { lower.contains(it) }) return true
        return dateRegex.containsMatchIn(line)
    }

    private fun itemOnOneLine(line: String): ParsedItem? {
        val amount = amountRegex.findAll(line).lastOrNull() ?: return null
        // The name is what comes before the figures start.
        val head = line.take(firstFigureAt(line, amount.range.first))
        return item(head, amount.toMinor(), quantityIn(line))
    }

    private fun itemFromPair(nameLine: String, figuresLine: String): ParsedItem? {
        if (amountRegex.containsMatchIn(nameLine)) return null
        val amount = amountRegex.findAll(figuresLine).lastOrNull() ?: return null
        // The figures line must be figures: a sentence with a price in it is
        // somebody else's line, not the price of the name above.
        if (figuresLine.count { it.isLetter() } > MAX_LETTERS_IN_FIGURES) return null
        return item(nameLine, amount.toMinor(), quantityIn(figuresLine))
    }

    /**
     * Where the numbers of a line begin — the quantity if it has one, the price
     * otherwise. Everything before it is the name.
     */
    private fun firstFigureAt(line: String, priceAt: Int): Int {
        val quantity = quantityRegex.find(line)
        if (quantity != null && quantity.range.first < priceAt) return quantity.range.first
        return priceAt
    }

    private fun quantityIn(line: String): Double? = quantityRegex.find(line)
        ?.groupValues?.get(1)
        ?.replace(',', '.')
        ?.toDoubleOrNull()
        ?.takeIf { it > 0 && it != 1.0 }

    /**
     * The name as it will be shown, or nothing: too short, mostly digits, or
     * longer than a name is ever printed all mean this was not a purchase.
     */
    private fun item(rawName: String, amountMinor: Long, quantity: Double?): ParsedItem? {
        if (amountMinor <= 0) return null
        val name = rawName
            .trim(' ', '"', '«', '»', '\'', ',', '.', ':', ';', '-', '—', '*', '=', '№', '/')
            .replace(Regex("""\s{2,}"""), " ")
        val letters = name.count { it.isLetter() }
        if (letters < 3 || letters <= name.count { it.isDigit() }) return null
        if (name.length > MAX_ITEM_NAME) return null
        return ParsedItem(name = name, amountMinor = amountMinor, quantity = quantity)
    }

    /** `2 x`, `0,756 ×`, `3 *` — the count printed before a price. */
    private val quantityRegex = Regex("""(\d+(?:[.,]\d{1,3})?)\s*[xх×*]\s*(?=\d)""")

    /**
     * Wording that belongs to the document rather than to the shopping. The
     * discount and change lines are covered by [notTheTotal] already.
     */
    private val notAnItem = listOf(
        "наименование", "кол-во", "колич", "цена", "стоимость", "ндс", "пдв", "нсп",
        "унп", "инн", "скно", "кассир", "касір", "смена", "чек", "чэк", "фискальн",
        "фіскальны", "терминал", "эквайер", "эквайринг", "rrn", "код авториз",
        // How it was paid for, which every slip prints next to a sum.
        "оплат", "аплат", "белкарт", "belkart", "visa", "mastercard", "maestro",
        "карта", "картка", "банк",
        "адрес", "адрас", "ул.", "пр-т", "просп", "тел", "www", "http", ".by",
        "спасибо", "дзякуй", "режим работы", "лиц.", "св-во", "объект", "магазин №"
    )

    /** Beyond this a "figures" line is prose that merely contains a price. */
    private const val MAX_LETTERS_IN_FIGURES = 6

    /** A receipt longer than this is a wholesale invoice, not a shopping trip. */
    private const val MAX_ITEMS = 60

    /** Long enough for "Молоко Савушкин продукт 3,2% 900 г". */
    private const val MAX_ITEM_NAME = 60

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
     * no vowel at all, a word that just means "shop", or a line too long to be
     * a name — the note is then left empty rather than filled with half a line.
     */
    private fun normalize(candidate: String): String? {
        val trimmed = candidate
            .trim(' ', '"', '«', '»', '\'', ',', '.', ':', ';', '-', '—', '*', '=')
            .replace(Regex("""\s{2,}"""), " ")
        val letters = trimmed.count { it.isLetter() }
        if (letters < 3 || letters <= trimmed.count { it.isDigit() }) return null
        if (letters * 2 < trimmed.length) return null
        if (trimmed.length > Notes.MAX_SCANNED_LENGTH) return null
        if (trimmed.none { it.lowercaseChar() in VOWELS }) return null
        if (trimmed.lowercase() in genericNames) return null
        return prettyCase(trimmed)
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

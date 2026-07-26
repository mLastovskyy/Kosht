package by.mlastovsky.kosht.data.receipt

import by.mlastovsky.kosht.util.Notes
import java.time.LocalDate

data class ParsedReceipt(
    val amountMinor: Long?,
    val date: LocalDate?,
    val merchant: String?,

    val items: List<ParsedItem> = emptyList()
)

data class ParsedItem(
    val name: String,
    val amountMinor: Long,

    val quantity: Double? = null
)

object ReceiptParser {

    private val totalKeywords = listOf(
        listOf("итого к оплате", "к оплате", "да аплаты", "к аплаце"),
        listOf("итого", "итог", "усяго", "всего"),
        listOf("сума", "сумма", "total")
    )

    private val notTheTotal = listOf(
        "скидк", "зніжк", "знижк", "сдача", "рэшта", "наличн", "гатоўк",
        "бонус", "кэшбэк", "кешбэк", "аванс", "предоплат", "эконом", "эканом",
        "внесено", "получено", "change"
    )

    private val amountRegex = Regex(
        """(?<!\d)(?<!\d[.,/\-:])(\d{1,3}(?:[  ]\d{3})+|\d{1,9})[.,](\d{2})(?![\d.,/\-:])"""
    )

    private val dateRegex = Regex("""\b(\d{2})[./-](\d{2})[./-](\d{2,4})\b""")

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

    private val notAMerchant = listOf(
        "кассовый чек", "касавы чэк", "чек", "чэк", "унп", "у н п", "инн",
        "скно", "номер", "смена", "кассир", "касір", "магазин №", "объект",
        "адрес", "адрас", "ул.", "пр-т", "просп", "г.минск", "тел", "фискальный",
        "фіскальны", "платежный", "терминал", "копия", "приход", "продажа",

        "оплат", "аплат", "итог", "усяго", "сдача", "рэшта", "наличн", "гатоўк",
        "ндс", "пдв", "нсп", "скидк", "зніжк", "бонус", "кэшбэк", "кешбэк",
        "белкарт", "visa", "mastercard", "maestro", "эквайер", "эквайринг",
        "rrn", "код авториз", "карта", "картка", "банк", "режим работы",
        "спасибо", "дзякуй", "приятн", "гарант", "обмен", "возврат",
        "лиц.", "св-во", "www", "http", ".by", "@"
    )

    private val genericNames = setOf(
        "магазин", "крама", "гастроном", "супермаркет", "минимаркет",
        "маркет", "товар", "товары", "тавары", "чек", "продавец"
    )

    private val legalForms = Regex(
        """^\s*(ООО|ОАО|ЗАО|ИП|ЧУП|ЧТУП|ТЧУП|УП|СООО|ОДО|ТДА|ГП|ЧПТУП)[\s"«'.:]*""",
        RegexOption.IGNORE_CASE
    )

    private val unpRegex = Regex("""\b(УНП|УНН|ИНН)\b""", RegexOption.IGNORE_CASE)

    private const val VOWELS = "аеёиоуыэюяіўaeiouy"

    private const val HEADER_LINES = 12

    fun parse(text: String): ParsedReceipt = parse(ReceiptLine.of(text))

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

    private fun reconciled(items: List<ParsedItem>, total: Long?): List<ParsedItem> {
        if (total == null || total <= 0 || items.isEmpty()) return items
        val sum = items.sumOf { it.amountMinor }
        return if (sum > total * OVER_TOTAL_LIMIT) emptyList() else items
    }

    private const val OVER_TOTAL_LIMIT = 1.5

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

            firstAmountAfter(line, at)?.let { return it }

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

    private fun skipAsItem(line: String): Boolean {
        val lower = line.lowercase()
        if (notTheTotal.any { lower.contains(it) }) return true
        if (notAnItem.any { lower.contains(it) }) return true
        return dateRegex.containsMatchIn(line)
    }

    private fun itemOnOneLine(line: String): ParsedItem? {
        val amount = amountRegex.findAll(line).lastOrNull() ?: return null

        val head = line.take(firstFigureAt(line, amount.range.first))
        return item(head, amount.toMinor(), quantityIn(line))
    }

    private fun itemFromPair(nameLine: String, figuresLine: String): ParsedItem? {
        if (amountRegex.containsMatchIn(nameLine)) return null
        val amount = amountRegex.findAll(figuresLine).lastOrNull() ?: return null

        if (figuresLine.count { it.isLetter() } > MAX_LETTERS_IN_FIGURES) return null
        return item(nameLine, amount.toMinor(), quantityIn(figuresLine))
    }

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

    private val quantityRegex = Regex("""(\d+(?:[.,]\d{1,3})?)\s*[xх×*]\s*(?=\d)""")

    private val notAnItem = listOf(
        "наименование", "кол-во", "колич", "цена", "стоимость", "ндс", "пдв", "нсп",
        "унп", "инн", "скно", "кассир", "касір", "смена", "чек", "чэк", "фискальн",
        "фіскальны", "терминал", "эквайер", "эквайринг", "rrn", "код авториз",

        "оплат", "аплат", "белкарт", "belkart", "visa", "mastercard", "maestro",
        "карта", "картка", "банк",
        "адрес", "адрас", "ул.", "пр-т", "просп", "тел", "www", "http", ".by",
        "спасибо", "дзякуй", "режим работы", "лиц.", "св-во", "объект", "магазин №"
    )

    private const val MAX_LETTERS_IN_FIGURES = 6

    private const val MAX_ITEMS = 60

    private const val MAX_ITEM_NAME = 60

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

    private fun findMerchant(lines: List<ReceiptLine>): String? =
        knownChain(lines.map { it.text }) ?: bestNamedLine(lines)

    private fun knownChain(lines: List<String>): String? {

        val header = lines.take(8).joinToString(" ").lowercase()
        return knownChains.entries
            .filter { header.contains(it.key) }

            .maxByOrNull { it.key.length }
            ?.value
    }

    private data class Candidate(val name: String, val score: Int, val index: Int)

    private fun bestNamedLine(lines: List<ReceiptLine>): String? {
        val header = lines.take(HEADER_LINES)
        val unpAt = header.indexOfFirst { unpRegex.containsMatchIn(it.text) }
        return header
            .mapIndexedNotNull { index, line -> candidate(line, index, unpAt) }
            .filter { it.score >= MIN_MERCHANT_SCORE }

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

            quoted != null && hasLegalForm -> 6
            quoted != null -> 4
            hasLegalForm -> 3
            else -> 0
        }

        score += ((line.emphasis - 1f) * 8f).toInt().coerceIn(0, 8)

        if (unpAt >= 0 && kotlin.math.abs(index - unpAt) == 1) score += 2
        if (index < 3) score += 1
        return Candidate(name, score, index)
    }

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

    private fun prettyCase(name: String): String {
        if (name.any { it.isLowerCase() }) return name
        return name.split(' ').joinToString(" ") { word ->
            if (word.length <= 2) word else word.take(1) + word.drop(1).lowercase()
        }
    }

    private fun quotedName(line: String): String? =
        Regex("""["«]([^"»]{2,40})["»]""").find(line)?.groupValues?.get(1)?.trim()

    private const val MIN_MERCHANT_SCORE = 1
}

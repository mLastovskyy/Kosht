package by.mlastovsky.kosht.data.receipt

import by.mlastovsky.kosht.data.receipt.ml.LineFeatures
import by.mlastovsky.kosht.data.receipt.ml.LineKind
import by.mlastovsky.kosht.data.receipt.ml.LineModel
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

internal val ParsedReceipt.tellsEverything: Boolean
    get() = amountMinor != null && items.isNotEmpty()

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

    internal val amountRegex = Regex(
        """(?<!\d)(?<!\d[.,/\-:])(\d{1,3}(?:[  ]\d{3})+|\d{1,9})[.,](\d{2})(?![\d.,/\-:])"""
    )

    private val dateRegex = Regex("""\b(\d{2})[./-](\d{2})[./-](\d{2,4})\b""")

    private val knownChains = mapOf(
        "евроопт" to "Евроопт",
        "евроторг" to "Евроопт",
        "белвиллесден" to "Гиппо",
        "табак-инвест" to "Корона",
        "либретик" to "Соседи",
        "простормаркет" to "ProStore",
        "электросервис" to "Электросила",
        "белоруснефть" to "Белоруснефть",
        "а-100" to "А-100",
        "wildberries" to "Wildberries",
        "вайлдберриз" to "Wildberries",
        "белпочта" to "Белпочта",
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
        "электросила" to "Электросила",
        "грошык" to "Грошык",
        "радзивилловский" to "Радзивилловский",
        "верас" to "Верас",
        "планета здоровья" to "Планета здоровья",
        "белфармация" to "Белфармация",
        "материк" to "Материк",
        "oma" to "OMA"
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
        "лиц.", "св-во", "www", "http", ".by", "@",
        "пользуетесь", "электронн", "qr", "отскан", "касса"
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

    private val addressRegex = Regex(
        """(^|\s)(г|гор|обл|мкр|пр|ул|д|пос)\s*\.\s*\S|,\s*д\s*\.?\s*\d""",
        RegexOption.IGNORE_CASE
    )

    private const val VOWELS = "аеёиоуыэюяіўaeiouy"

    private const val HEADER_LINES = 12

    fun parse(text: String, model: LineModel? = null): ParsedReceipt =
        parse(ReceiptLine.of(text), model)

    fun combined(electronic: ParsedReceipt?, paper: ParsedReceipt?): ParsedReceipt? {
        if (electronic == null) return paper
        if (paper == null) return electronic
        val amount = electronic.amountMinor ?: paper.amountMinor
        return ParsedReceipt(
            amountMinor = amount,
            date = electronic.date ?: paper.date,
            merchant = electronic.merchant ?: paper.merchant,
            items = reconciled(electronic.items.ifEmpty { paper.items }, amount)
        )
    }

    fun parse(lines: List<ReceiptLine>, model: LineModel? = null): ParsedReceipt {
        val cleaned = OcrDigits.repair(lines)
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotEmpty() }
        val texts = cleaned.map { it.text }
        val judged = model?.let { Judged(cleaned, it) }
        val total = findTotal(texts) ?: judged?.total() ?: fallbackTotal(texts)
        val above = headerEnd(texts)
        return ParsedReceipt(
            amountMinor = total,
            date = findDate(texts.joinToString("\n")),
            merchant = findMerchant(cleaned, judged, above),
            items = reconciled(kept(findItems(texts, judged?.totalAt), judged), total)
        )
    }

    private fun headerEnd(lines: List<String>): Int {
        val total = lines.indexOfFirst { line ->
            val lower = line.lowercase()
            totalKeywords.take(2).any { group -> group.any { lower.contains(it) } }
        }
        return if (total > 0) minOf(total, HEADER_LINES) else HEADER_LINES
    }

    private class Judged(private val lines: List<ReceiptLine>, private val model: LineModel) {

        private val chances = lines.mapIndexed { index, line ->
            model.chances(
                LineFeatures.of(line.text, line.emphasis, index, lines.size)
            )
        }

        fun chance(kind: LineKind, index: Int): Float =
            chances.getOrNull(index)?.get(kind) ?: 0f

        var totalAt: Int? = null
            private set

        fun total(): Long? = lines.indices
            .filter { index ->
                val lower = lines[index].text.lowercase()
                notTheTotal.none { lower.contains(it) } &&
                    amountRegex.containsMatchIn(lines[index].text)
            }
            .maxByOrNull { chance(LineKind.TOTAL, it) }
            ?.takeIf { chance(LineKind.TOTAL, it) >= MIN_TOTAL_CHANCE }
            ?.also { totalAt = it }
            ?.let { index -> amountRegex.findAll(lines[index].text).lastOrNull()?.toMinor() }

        fun merchant(above: Int): String? = lines.indices
            .take(above)
            .maxByOrNull { chance(LineKind.MERCHANT, it) }
            ?.takeIf { chance(LineKind.MERCHANT, it) >= MIN_MERCHANT_CHANCE }
            ?.let { index -> quotedName(lines[index].text) ?: lines[index].text }
            ?.let { normalize(it.replace(legalForms, "")) }

        fun looksBought(index: Int): Boolean =
            chance(LineKind.ITEM, index) >= MIN_ITEM_CHANCE ||
                chance(LineKind.OTHER, index) < chance(LineKind.ITEM, index)
    }

    private fun kept(found: List<FoundItem>, judged: Judged?): List<ParsedItem> {
        if (judged == null) return found.map { it.item }
        return found.filter { judged.looksBought(it.index) }.map { it.item }
    }

    private const val MIN_TOTAL_CHANCE = 0.4f

    private const val MIN_MERCHANT_CHANCE = 0.5f

    private const val MIN_ITEM_CHANCE = 0.25f

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
        return null
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

    private data class FoundItem(val item: ParsedItem, val index: Int)

    private fun findItems(lines: List<String>, totalAt: Int?): List<FoundItem> {
        val end = lines.indexOfFirst { line ->
            val lower = line.lowercase()
            totalKeywords.take(2).any { group -> group.any { lower.contains(it) } }
        }.takeIf { it >= 0 } ?: lines.size
        val items = mutableListOf<FoundItem>()
        val naming = mutableListOf<IndexedValue<String>>()
        var index = 0
        while (index < end && items.size < MAX_ITEMS) {
            val line = lines[index]
            if (index == totalAt || skipAsItem(line)) {
                naming.clear()
                index++
                continue
            }
            val amount = amountRegex.findAll(line).lastOrNull()
            if (amount == null) {
                if (readsLikeName(line)) {
                    if (naming.size == MAX_NAME_LINES) naming.removeAt(0)
                    naming += IndexedValue(index, line)
                }
                index++
                continue
            }
            val minor = amount.toMinor()
            val quantity = quantityIn(line)
            val onThisLine = item(line.take(firstFigureAt(line, amount.range.first)), minor, quantity)
            if (onThisLine != null) {
                items += FoundItem(onThisLine, index)
            } else {
                nameAbove(naming)?.let { above ->
                    item(above.value, minor, quantity)?.let {
                        items += FoundItem(it, above.index)
                    }
                }
            }
            naming.clear()
            index++
        }
        return items
    }

    private fun readsLikeName(line: String): Boolean {
        val letters = line.count { it.isLetter() }
        return letters >= MIN_NAME_LETTERS && letters > line.count { it.isDigit() }
    }

    private fun nameAbove(naming: List<IndexedValue<String>>): IndexedValue<String>? =
        naming.maxWithOrNull(
            compareBy({ line -> line.value.count { it.isLetter() } }, { -it.index })
        )

    private fun skipAsItem(line: String): Boolean {
        val lower = line.lowercase()
        if (notTheTotal.any { lower.contains(it) }) return true
        if (notAnItem.any { lower.contains(it) }) return true
        return dateRegex.containsMatchIn(line)
    }

    private fun firstFigureAt(line: String, priceAt: Int): Int = listOfNotNull(
        timesRegex.find(line)?.range?.first,
        countAfterUnitRegex.find(line)?.range?.first
    ).filter { it < priceAt }.minOrNull() ?: priceAt

    private fun quantityIn(line: String): Double? {
        val afterUnit = countAfterUnitRegex.find(line)?.groupValues?.get(1)
        val counted = afterUnit ?: timesRegex.find(line)?.let { times ->
            theCount(times.groupValues[1], times.groupValues[2])
        }
        return counted
            ?.replace(',', '.')
            ?.toDoubleOrNull()
            ?.takeIf { it > 0 && it != 1.0 }
    }

    private fun theCount(left: String, right: String): String {
        val here = decimalsIn(left)
        val there = decimalsIn(right)
        return when {
            there == 3 && here != 3 -> right
            here == 3 && there != 3 -> left
            here == 0 && there > 0 -> left
            there == 0 && here > 0 -> right
            else -> left
        }
    }

    private fun decimalsIn(number: String): Int =
        number.substringAfter(',').substringAfter('.').length.takeIf {
            number.any { char -> char == ',' || char == '.' }
        } ?: 0

    private fun item(rawName: String, amountMinor: Long, quantity: Double?): ParsedItem? {
        if (amountMinor <= 0) return null
        val name = rawName
            .trim(*NAME_TRIM)
            .replace(articleRegex, "")
            .trim(*NAME_TRIM)
            .replace(Regex("""\s{2,}"""), " ")
            .let(::shortened)
        val letters = name.count { it.isLetter() }
        if (letters < 3 || letters <= name.count { it.isDigit() }) return null
        return ParsedItem(name = name, amountMinor = amountMinor, quantity = quantity)
    }

    private fun shortened(name: String): String {
        if (name.length <= MAX_ITEM_NAME) return name
        val cut = name.take(MAX_ITEM_NAME)
        val space = cut.lastIndexOf(' ')
        return (if (space >= MAX_ITEM_NAME / 2) cut.take(space) else cut)
            .trimEnd(' ', ',', '.', '-', '(')
    }

    private val timesRegex =
        Regex("""(\d+(?:[.,]\d{1,3})?)\s*[xх×*]\s*(\d+(?:[.,]\d{1,3})?)""")

    private val countAfterUnitRegex = Regex(
        """(?:шт|кг|мл|уп|пач|г|л)\.?\s*[*xх×]\s*(\d+(?:[.,]\d{1,3})?)""",
        RegexOption.IGNORE_CASE
    )

    private val articleRegex = Regex("""^\s*(?:\d{1,3}\s*[.)]|\d{4,})\s+""")

    private val NAME_TRIM = charArrayOf(
        ' ', '"', '«', '»', '\'', '‘', '’', '`', ',', '.', ':', ';',
        '-', '—', '*', '=', '№', '/', '|', '!'
    )

    private val notAnItem = listOf(
        "наименование", "кол-во", "колич", "цена", "стоимость", "ндс", "пдв", "нсп",
        "унп", "инн", "скно", "кассир", "касір", "смена", "чек", "чэк", "фискальн",
        "фіскальны", "терминал", "эквайер", "эквайринг", "rrn", "код авториз",

        "оплат", "аплат", "белкарт", "belkart", "visa", "mastercard", "maestro",
        "карта", "картка", "банк",
        "адрес", "адрас", "ул.", "пр-т", "просп", "тел", "www", "http", ".by",
        "спасибо", "дзякуй", "режим работы", "лиц.", "св-во", "объект", "магазин №",
        "платежн", "плацеж", "скко", "отзыв"
    )

    private const val MAX_ITEMS = 60

    private const val MAX_ITEM_NAME = 60

    private const val MAX_NAME_LINES = 4

    private const val MIN_NAME_LETTERS = 3

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

    private fun findMerchant(
        lines: List<ReceiptLine>,
        judged: Judged?,
        above: Int
    ): String? = knownChain(lines.map { it.text }, above)
        ?: judged?.merchant(above)
        ?: bestNamedLine(lines, above)

    private fun knownChain(lines: List<String>, above: Int): String? =
        chainIn(lines.take(above).joinToString(" "))

    private fun chainIn(text: String): String? {
        val folded = folded(text)
        return knownChains.entries
            .filter { nearlyContains(folded, folded(it.key)) }

            .maxByOrNull { it.key.length }
            ?.value
    }

    private val lookAlikes = mapOf(
        'a' to 'а', 'b' to 'в', 'c' to 'с', 'e' to 'е', 'h' to 'н', 'k' to 'к',
        'm' to 'м', 'o' to 'о', 'p' to 'р', 't' to 'т', 'x' to 'х', 'y' to 'у',
        '0' to 'о', '3' to 'з', '6' to 'б', '8' to 'в'
    )

    private fun folded(text: String): String {
        val builder = StringBuilder(text.length)
        text.lowercase().forEach { symbol ->
            val letter = lookAlikes[symbol] ?: symbol
            when {
                letter.isLetterOrDigit() -> builder.append(letter)
                builder.isNotEmpty() && builder.last() != ' ' -> builder.append(' ')
            }
        }
        return builder.toString().trim()
    }

    private fun nearlyContains(header: String, name: String): Boolean {
        if (name.length < FUZZY_FROM) return " $header ".contains(" $name ")
        if (header.contains(name)) return true
        for (start in header.indices) {
            for (span in name.length - 1..name.length + 1) {
                val end = start + span
                if (end > header.length) continue
                if (oneEditApart(header.substring(start, end), name)) return true
            }
        }
        return false
    }

    private fun oneEditApart(left: String, right: String): Boolean {
        if (left == right) return true
        val (shorter, longer) = if (left.length <= right.length) left to right else right to left
        if (longer.length - shorter.length > 1) return false
        var here = 0
        var there = 0
        var edits = 0
        while (here < shorter.length && there < longer.length) {
            if (shorter[here] == longer[there]) {
                here++
                there++
                continue
            }
            if (++edits > 1) return false
            there++
            if (shorter.length == longer.length) here++
        }
        return true
    }

    private const val FUZZY_FROM = 6

    private data class Candidate(val name: String, val score: Int, val index: Int)

    private fun bestNamedLine(lines: List<ReceiptLine>, above: Int): String? {
        val header = lines.take(above)
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
        if (addressRegex.containsMatchIn(raw)) return null
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

    private fun quotedName(line: String): String? {
        val quoted = quotedRegex.findAll(line)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()

        return quoted.firstOrNull { chainIn(it) != null } ?: quoted.lastOrNull()
    }

    private val quotedRegex = Regex("""["«]([^"»]{2,40})["»]""")

    private const val MIN_MERCHANT_SCORE = 2
}

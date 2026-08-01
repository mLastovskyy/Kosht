package by.mlastovsky.kosht.data.receipt

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

sealed interface QrPayload {

    data class Link(val url: String) : QrPayload

    data class Fields(val values: Map<String, String>) : QrPayload

    data class Uid(val value: String) : QrPayload
}

object ReceiptQr {

    private val ignoredSchemes = listOf(
        "wifi:", "mailto:", "tel:", "sms:", "geo:", "bitcoin:", "otpauth:", "begin:vcard"
    )

    private val fiscalTime = DateTimeFormatter.ofPattern("yyyyMMdd")

    private val uidOnly = Regex("""^[0-9A-Fa-f]{24}$""")

    internal val uidInText = Regex("""(?:^|\s)[Уу]\s?[ИиIi]\s*[:.]?\s*([0-9A-Fa-f]{24})(?:\s|$)""")

    private val eplusLink = Regex(
        """^https://r\.eplus\.by/((?:[0-9A-Fa-f]{6}-)?[0-9A-Fa-f]{8}(?:-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12})/?$""",
        RegexOption.IGNORE_CASE
    )

    private val ikassaLink = Regex(
        """^https://receipts\.cloud\.ikassa\.by/render/([0-9A-Fa-f]{24})/?$""",
        RegexOption.IGNORE_CASE
    )

    fun classify(raw: String): QrPayload? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (ignoredSchemes.any { text.startsWith(it, ignoreCase = true) }) return null
        if (isPaymentRequest(text)) return null

        if (text.startsWith("http://", true) || text.startsWith("https://", true)) {
            ikassaLink.find(text)?.let { return QrPayload.Uid(it.groupValues[1]) }
            return QrPayload.Link(text)
        }

        if (uidOnly.matches(text)) return QrPayload.Uid(text)

        val values = text.split('&')
            .mapNotNull { pair ->
                val key = pair.substringBefore('=', "").trim().lowercase()
                val value = pair.substringAfter('=', "").trim()
                if (key.isEmpty() || value.isEmpty()) null else key to value
            }
            .toMap()

        return if (values.containsKey("s") && values.containsKey("t")) {
            QrPayload.Fields(values)
        } else {
            null
        }
    }

    private fun isPaymentRequest(text: String): Boolean = text.startsWith("000201") &&
        (text.contains("by.raschet", true) || text.contains("rtpraschet", true))

    fun uidIn(text: String): String? = uidInText.find(text)?.groupValues?.get(1)

    fun ikassaUrl(uid: String): String = "https://receipts.cloud.ikassa.by/render/$uid"

    fun eplusReceiptId(url: String): String? = eplusLink.find(url.trim())?.groupValues?.get(1)

    fun fromFields(values: Map<String, String>): ParsedReceipt? {
        val amount = values["s"]?.let(::toMinor) ?: return null
        return ParsedReceipt(amountMinor = amount, date = values["t"]?.let(::toDate), merchant = null)
    }

    private fun toMinor(raw: String): Long? {
        val cleaned = raw.replace(',', '.').replace(" ", "")
        val whole = cleaned.substringBefore('.').toLongOrNull() ?: return null
        val fraction = cleaned.substringAfter('.', "").padEnd(2, '0').take(2)
        return whole * 100 + (fraction.toLongOrNull() ?: 0)
    }

    private fun toDate(raw: String): LocalDate? =
        runCatching { LocalDate.parse(raw.take(8), fiscalTime) }.getOrNull()

    fun linesFromHtml(html: String): List<ReceiptLine> {
        val shown = shownLines(html)
        if (figuresIn(shown) >= SHOWN_FIGURES) return shown
        val carried = carriedLines(html)
        if (carried.isEmpty()) return shown
        return shown.filterNot { ReceiptParser.amountRegex.containsMatchIn(it.text) }
            .take(HEADER_LINES) + carried
    }

    fun showsReceipt(html: String): Boolean = figuresIn(shownLines(html)) >= SHOWN_FIGURES

    private fun figuresIn(lines: List<ReceiptLine>): Int =
        lines.count { ReceiptParser.amountRegex.containsMatchIn(it.text) }

    private fun shownLines(html: String): List<ReceiptLine> {
        val marked = html
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<(h[1-3]|b|strong|em)(\\s[^>]*)?>"), EMPHASIS_MARK)

            .replace(Regex("(?i)</(h[1-3]|b|strong|em)>"), EMPHASIS_MARK + "\n")
        return textFromHtml(marked)
            .lines()
            .map { line ->
                ReceiptLine(
                    text = line.replace(EMPHASIS_MARK, " ").replace(Regex(" {2,}"), " ").trim(),
                    emphasis = if (EMPHASIS_MARK in line) EMPHASIZED else 1f
                )
            }
            .filter { it.text.isNotEmpty() }
    }

    fun carriedLines(html: String): List<ReceiptLine> = scriptBodies.findAll(html)
        .mapNotNull { match -> jsonIn(match.groupValues[1]) }
        .flatMap { json -> receiptLines(json).asSequence() }
        .take(MAX_CARRIED_LINES)
        .toList()

    private val scriptBodies = Regex("(?is)<script[^>]*>(.*?)</script>")

    private fun jsonIn(body: String): Any? {
        val opens = listOf('{' to '}', '[' to ']')
        for ((open, close) in opens) {
            val from = body.indexOf(open)
            val to = body.lastIndexOf(close)
            if (from < 0 || to <= from) continue
            val slice = body.substring(from, to + 1)
            runCatching {
                if (open == '{') JSONObject(slice) else JSONArray(slice)
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun receiptLines(node: Any?): List<ReceiptLine> {
        val found = Found()
        walk(node, found)
        return found.shops.take(1).map { ReceiptLine(it, EMPHASIZED) } +
            found.bought +
            found.totals
    }

    private class Found {
        val shops = mutableListOf<String>()
        val bought = mutableListOf<ReceiptLine>()
        val totals = mutableListOf<ReceiptLine>()
    }

    private fun walk(node: Any?, found: Found) {
        when (node) {
            is JSONArray -> (0 until node.length()).forEach { walk(node.opt(it), found) }
            is JSONObject -> {
                val keys = node.keys().asSequence().toList()
                keys.filter { key -> shopKeys.any { key.lowercase().contains(it) } }
                    .mapNotNull { key -> namedIn(node.opt(key)) }
                    .forEach { found.shops += it }
                val name = keys.firstOrNull { it.lowercase() in nameKeys }
                    ?.let { node.optString(it) }
                    ?.takeIf { it.count(Char::isLetter) >= MIN_NAME_LETTERS }
                val totalKey = keys
                    .firstOrNull { key -> totalKeys.any { key.lowercase().contains(it) } }
                val priceKey = keys
                    .firstOrNull { key -> priceKeys.any { key.lowercase().contains(it) } }
                val money = major(node.opt(totalKey ?: priceKey))
                when {
                    money == null -> Unit
                    name != null -> found.bought += ReceiptLine("$name  $money")
                    totalKey != null -> found.totals += ReceiptLine("ИТОГО  $money")
                }
                keys.forEach { key -> walk(node.opt(key), found) }
            }
        }
    }

    private fun namedIn(value: Any?): String? = when (value) {
        is String -> value.trim().takeIf { it.count(Char::isLetter) >= MIN_NAME_LETTERS }
        is JSONObject -> value.keys().asSequence()
            .firstOrNull { it.lowercase() in nameKeys }
            ?.let { value.optString(it).trim() }
            ?.takeIf { it.count(Char::isLetter) >= MIN_NAME_LETTERS }

        else -> null
    }

    private fun major(value: Any?): String? {
        if (value == null) return null
        val raw = when (value) {
            is Number -> value.toString()
            is String -> value.trim()
            else -> null
        } ?: return null
        val cleaned = raw.replace(',', '.').replace(" ", "")
        if ('.' !in cleaned) return null
        val whole = cleaned.substringBefore('.').toLongOrNull() ?: return null
        val fraction = cleaned.substringAfter('.').filter { it.isDigit() }.padEnd(2, '0').take(2)
        return "$whole,$fraction"
    }

    private val nameKeys = setOf(
        "name", "title", "product", "goods", "item", "наименование", "товар", "название"
    )

    private val shopKeys = listOf(
        "shop", "store", "seller", "merchant", "organization", "orgname", "retail",
        "магазин", "продавец", "организац", "торгов"
    )

    private val totalKeys = listOf("total", "итог", "topay", "к оплате", "sumtotal")

    private val priceKeys = listOf("sum", "amount", "price", "cost", "сумм", "стоим", "цена")

    private const val MAX_CARRIED_LINES = 120

    private const val SHOWN_FIGURES = 2

    private const val HEADER_LINES = 12

    private const val MIN_NAME_LETTERS = 3

    private const val EMPHASIS_MARK = "\u0001"

    private const val EMPHASIZED = 1.5f

    fun textFromHtml(html: String): String = html
        .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?i)<(br|/tr|/p|/div|/h[1-6]|/li)[^>]*>"), "\n")
        .replace(Regex("(?s)<[^>]+>"), " ")
        .let(::decodeEntities)
        .lines()
        .joinToString("\n") { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }
        .replace(Regex("\n{2,}"), "\n")
        .trim()

    private fun decodeEntities(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
}

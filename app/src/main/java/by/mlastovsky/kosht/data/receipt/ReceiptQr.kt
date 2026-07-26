package by.mlastovsky.kosht.data.receipt

import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed interface QrPayload {

    data class Link(val url: String) : QrPayload

    data class Fields(val values: Map<String, String>) : QrPayload
}

object ReceiptQr {

    private val ignoredSchemes = listOf(
        "wifi:", "mailto:", "tel:", "sms:", "geo:", "bitcoin:", "otpauth:", "begin:vcard"
    )

    private val fiscalTime = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun classify(raw: String): QrPayload? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (ignoredSchemes.any { text.startsWith(it, ignoreCase = true) }) return null

        if (text.startsWith("http://", true) || text.startsWith("https://", true)) {
            return QrPayload.Link(text)
        }

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

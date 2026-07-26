package by.mlastovsky.kosht.data.receipt

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** What a QR found on a receipt turned out to be. */
sealed interface QrPayload {

    /** A link to the shop's electronic receipt, which is worth fetching. */
    data class Link(val url: String) : QrPayload

    /** Fiscal data encoded straight into the code, e.g. `t=...&s=12.50&fn=...`. */
    data class Fields(val values: Map<String, String>) : QrPayload
}

/**
 * Pure QR-payload logic, kept away from Android so it can be unit-tested.
 *
 * Belarusian chains do not publish a receipt API, and a QR on a slip may just
 * as easily be a loyalty card, a Wi-Fi password or an advert. So nothing is
 * assumed from the code alone: a payload only counts as a receipt once it
 * either carries fiscal fields or leads to a page an amount can be read from.
 */
object ReceiptQr {

    /** Codes that are definitely not receipts, whatever else they may be. */
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
        // A receipt states what was paid and when; anything less is some
        // other kind of code that happens to use key=value pairs.
        return if (values.containsKey("s") && values.containsKey("t")) {
            QrPayload.Fields(values)
        } else {
            null
        }
    }

    /** Reads the total and date out of fiscal key=value fields. */
    fun fromFields(values: Map<String, String>): ParsedReceipt? {
        val amount = values["s"]?.let(::toMinor) ?: return null
        return ParsedReceipt(amountMinor = amount, date = values["t"]?.let(::toDate), merchant = null)
    }

    /** "12.50" and "12,5" are major units; "1250" is twelve hundred fifty. */
    private fun toMinor(raw: String): Long? {
        val cleaned = raw.replace(',', '.').replace(" ", "")
        val whole = cleaned.substringBefore('.').toLongOrNull() ?: return null
        val fraction = cleaned.substringAfter('.', "").padEnd(2, '0').take(2)
        return whole * 100 + (fraction.toLongOrNull() ?: 0)
    }

    /** Fiscal timestamps look like `20260726T1930`; only the day matters here. */
    private fun toDate(raw: String): LocalDate? =
        runCatching { LocalDate.parse(raw.take(8), fiscalTime) }.getOrNull()

    /**
     * Flattens a fetched receipt page into the kind of line-per-item text the
     * OCR parser already knows how to read, so both paths share one parser.
     */
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

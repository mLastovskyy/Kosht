package by.mlastovsky.kosht.data.receipt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** An electronic receipt reached through the QR printed on a paper slip. */
data class EReceipt(
    val parsed: ParsedReceipt,
    /** Where it came from, so it can be opened again later. */
    val sourceUrl: String?,
    /** App-private copy of the page, readable with no connection. */
    val documentPath: String?
)

/**
 * Turns the QR from a receipt into the receipt itself.
 *
 * No Belarusian chain documents a public receipt API, so nothing here is
 * tied to one shop: the code is followed like a link, the page is kept for
 * later, and the same parser that reads OCR text reads the page. A code that
 * leads nowhere useful is reported as "not a receipt" and the caller falls
 * back to reading the photo.
 */
class EReceiptFetcher(private val context: Context) {

    suspend fun resolve(rawQr: String): EReceipt? = withContext(Dispatchers.IO) {
        when (val payload = ReceiptQr.classify(rawQr)) {
            null -> null

            is QrPayload.Fields -> ReceiptQr.fromFields(payload.values)
                ?.let { EReceipt(it, sourceUrl = null, documentPath = null) }

            is QrPayload.Link -> fetchLink(payload.url)
        }
    }

    private fun fetchLink(url: String): EReceipt? {
        val document = download(url) ?: return null
        val text = when {
            document.looksBinary -> ""
            document.contentType.contains("html", ignoreCase = true) ->
                ReceiptQr.textFromHtml(document.text)

            else -> document.text
        }
        val parsed = ReceiptParser.parse(text)
        if (parsed.amountMinor == null) {
            // The link went somewhere, but not to anything resembling a
            // receipt — leave no orphan file behind and let OCR try.
            document.savedPath?.let { File(it).delete() }
            return null
        }
        return EReceipt(parsed, sourceUrl = url, documentPath = document.savedPath)
    }

    private class Document(
        val text: String,
        val contentType: String,
        val savedPath: String?,
        val looksBinary: Boolean
    )

    private fun download(url: String, hop: Int = 0): Document? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            // Retail receipt pages tend to refuse unfamiliar clients.
            setRequestProperty("User-Agent", BROWSER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9")
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            // http -> https is not followed automatically; one hop is enough.
            if (code in 300..399 && hop < 2) {
                val next = connection.getHeaderField("Location") ?: return null
                return download(URL(URL(url), next).toString(), hop + 1)
            }
            if (code !in 200..299) return null

            val contentType = connection.contentType.orEmpty()
            val bytes = connection.inputStream.use { it.readAtMost(MAX_BYTES) }
            if (bytes.isEmpty()) return null

            val binary = contentType.contains("pdf", true) ||
                contentType.startsWith("image/", true)
            Document(
                text = if (binary) "" else bytes.toString(charsetOf(contentType)),
                contentType = contentType,
                savedPath = save(bytes, contentType),
                looksBinary = binary
            )
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun save(bytes: ByteArray, contentType: String): String? = runCatching {
        val extension = when {
            contentType.contains("pdf", true) -> "pdf"
            contentType.contains("json", true) -> "json"
            contentType.contains("html", true) -> "html"
            else -> "txt"
        }
        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
        val file = File(dir, "echeck_${System.currentTimeMillis()}.$extension")
        file.writeBytes(bytes)
        file.absolutePath
    }.getOrNull()

    private fun charsetOf(contentType: String): java.nio.charset.Charset {
        val name = contentType.substringAfter("charset=", "").trim().trim('"')
        return runCatching { java.nio.charset.Charset.forName(name) }
            .getOrDefault(Charsets.UTF_8)
    }

    private fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (buffer.size() < limit) {
            val read = read(chunk)
            if (read < 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private companion object {
        const val MAX_BYTES = 512 * 1024
        const val BROWSER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0 Mobile Safari/537.36"
    }
}

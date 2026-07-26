package by.mlastovsky.kosht.data.receipt

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EReceipt(
    val parsed: ParsedReceipt,

    val sourceUrl: String?,

    val documentPath: String?
)

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
        val lines = when {
            document.looksBinary -> emptyList()

            document.contentType.contains("html", ignoreCase = true) ->
                ReceiptQr.linesFromHtml(document.text)

            else -> ReceiptLine.of(document.text)
        }
        val parsed = ReceiptParser.parse(lines)
        val offered = offeredDocument(document, url)
        val documentPath = offered?.savedPath ?: document.savedPath
        if (offered != null) document.savedPath?.let { File(it).delete() }
        if (parsed.amountMinor == null) {
            documentPath?.let { File(it).delete() }
            return null
        }
        return EReceipt(parsed, sourceUrl = url, documentPath = documentPath)
    }

    private fun offeredDocument(page: Document, url: String): Document? {
        if (page.looksBinary) return null
        if (!page.contentType.contains("html", ignoreCase = true)) return null
        val link = documentLink(page.text, url) ?: return null
        val downloaded = download(link, hop = 1) ?: return null
        if (!downloaded.looksBinary) {
            downloaded.savedPath?.let { File(it).delete() }
            return null
        }
        return downloaded
    }

    private fun documentLink(html: String, base: String): String? {
        val host = runCatching { URL(base).host }.getOrNull() ?: return null
        val links = HREF.findAll(html)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("javascript:") }
            .mapNotNull { runCatching { URL(URL(base), it).toString() }.getOrNull() }
            .filter { runCatching { URL(it).host == host }.getOrDefault(false) }
            .toList()
        return links.firstOrNull { it.substringBefore('?').endsWith(".pdf", ignoreCase = true) }
            ?: links.firstOrNull { it.contains("pdf", ignoreCase = true) }
            ?: links.firstOrNull { it.contains("download", ignoreCase = true) }
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

            setRequestProperty("User-Agent", BROWSER_AGENT)
            setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml,application/json;q=0.9,application/pdf;q=0.9"
            )
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode

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
        const val MAX_BYTES = 2 * 1024 * 1024
        val HREF = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        const val BROWSER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0 Mobile Safari/537.36"
    }
}

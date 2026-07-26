package by.mlastovsky.kosht.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What a scan produced, whichever route it came by. */
data class ScannedReceipt(
    val parsed: ParsedReceipt,
    /** Set when the figures came from an electronic receipt behind a QR. */
    val sourceUrl: String? = null,
    /** Offline copy of that electronic receipt, app-private path. */
    val documentPath: String? = null
)

/**
 * Reads a photographed receipt two ways.
 *
 * A QR is tried first: shops that print one hand over exact figures, which
 * beats guessing them from a crumpled slip — and on the short slips that
 * carry nothing but a QR it is the only thing there is to read. When no code
 * is found, or it turns out not to lead to a receipt, the photo goes through
 * the offline OCR that was always here (Tesseract, Russian model in assets).
 */
class ReceiptScanner(
    private val context: Context,
    private val eReceipts: EReceiptFetcher = EReceiptFetcher(context)
) {

    suspend fun scan(uri: Uri): ScannedReceipt? = withContext(Dispatchers.Default) {
        val bitmap = decodeDownscaled(uri, MAX_DIMENSION) ?: return@withContext null
        QrReader.decode(bitmap)?.let { payload ->
            eReceipts.resolve(payload)?.let { receipt ->
                return@withContext ScannedReceipt(
                    parsed = receipt.parsed,
                    sourceUrl = receipt.sourceUrl,
                    documentPath = receipt.documentPath
                )
            }
        }
        val text = recognize(bitmap) ?: return@withContext null
        ReceiptParser.parse(text)
            .takeIf { it.amountMinor != null }
            ?.let { ScannedReceipt(it) }
    }

    private fun recognize(bitmap: Bitmap): String? {
        val dataDir = ensureTrainedData() ?: return null
        val tess = TessBaseAPI()
        return try {
            if (!tess.init(dataDir.absolutePath, LANGUAGE)) return null
            tess.setImage(bitmap)
            tess.utF8Text
        } catch (e: Exception) {
            null
        } finally {
            tess.recycle()
        }
    }

    /** Copies tessdata/rus.traineddata from assets to files dir on first use. */
    private fun ensureTrainedData(): File? = runCatching {
        val dir = File(context.filesDir, "tessdata")
        dir.mkdirs()
        val target = File(dir, "$LANGUAGE.traineddata")
        if (!target.exists() || target.length() == 0L) {
            context.assets.open("tessdata/$LANGUAGE.traineddata").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        context.filesDir
    }.getOrNull()

    private fun decodeDownscaled(uri: Uri, maxDimension: Int): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (
            bounds.outWidth / (sampleSize * 2) >= maxDimension / 2 &&
            bounds.outHeight / (sampleSize * 2) >= maxDimension / 2
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    private companion object {
        const val LANGUAGE = "rus"
        const val MAX_DIMENSION = 2200
    }
}

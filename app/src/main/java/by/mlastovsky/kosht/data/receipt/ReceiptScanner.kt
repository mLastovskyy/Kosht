package by.mlastovsky.kosht.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScannedReceipt(
    val parsed: ParsedReceipt,

    val sourceUrl: String? = null,

    val documentPath: String? = null
)

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

        val lines = recognize(prepared(bitmap)).ifEmpty { recognize(bitmap) }
        if (lines.isEmpty()) return@withContext null
        ReceiptParser.parse(lines)
            .takeIf { it.amountMinor != null }
            ?.let { ScannedReceipt(it) }
    }

    private fun prepared(source: Bitmap): Bitmap = runCatching {
        val scale = (MIN_DIMENSION.toFloat() / minOf(source.width, source.height))
            .coerceIn(1f, MAX_UPSCALE)
        val width = (source.width * scale).toInt()
        val height = (source.height * scale).toInt()
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)

        val grey = IntArray(pixels.size)
        var darkest = 255
        var lightest = 0
        for (i in pixels.indices) {
            val pixel = pixels[i]

            val value = (
                ((pixel shr 16 and 0xFF) * 299 +
                    (pixel shr 8 and 0xFF) * 587 +
                    (pixel and 0xFF) * 114) / 1000
                ).coerceIn(0, 255)
            grey[i] = value
            if (value < darkest) darkest = value
            if (value > lightest) lightest = value
        }

        val span = lightest - darkest
        if (span < MIN_SPAN) return@runCatching source
        for (i in grey.indices) {
            val stretched = (grey[i] - darkest) * 255 / span
            pixels[i] = 0xFF shl 24 or (stretched shl 16) or (stretched shl 8) or stretched
        }
        val stretched = Bitmap.createBitmap(
            pixels,
            source.width,
            source.height,
            Bitmap.Config.ARGB_8888
        )
        if (scale <= 1f) stretched else stretched.scale(width, height)
    }.getOrDefault(source)

    private fun recognize(bitmap: Bitmap): List<ReceiptLine> {
        val dataDir = ensureTrainedData() ?: return emptyList()
        val tess = TessBaseAPI()
        return try {
            if (!tess.init(dataDir.absolutePath, LANGUAGE)) return emptyList()
            tess.setImage(bitmap)

            val plain = tess.utF8Text.orEmpty()
            measuredLines(tess).ifEmpty { ReceiptLine.of(plain) }
        } catch (e: Exception) {
            emptyList()
        } finally {
            tess.recycle()
        }
    }

    private fun measuredLines(tess: TessBaseAPI): List<ReceiptLine> = runCatching {
        val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
        val iterator = tess.resultIterator ?: return emptyList()
        val measured = mutableListOf<Pair<String, Int>>()
        try {
            iterator.begin()
            do {
                val text = iterator.getUTF8Text(level)?.trim().orEmpty()
                val height = iterator.getBoundingRect(level)?.height() ?: 0
                if (text.isNotEmpty() && height > 0) measured += text to height
            } while (iterator.next(level))
        } finally {
            iterator.delete()
        }
        if (measured.isEmpty()) return emptyList()

        val median = measured.map { it.second }.sorted()[measured.size / 2].coerceAtLeast(1)
        measured.map { (text, height) ->
            ReceiptLine(text, (height.toFloat() / median).coerceIn(0.5f, 4f))
        }
    }.getOrDefault(emptyList())

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

        const val MIN_DIMENSION = 1000

        const val MAX_UPSCALE = 2f

        const val MIN_SPAN = 32
    }
}

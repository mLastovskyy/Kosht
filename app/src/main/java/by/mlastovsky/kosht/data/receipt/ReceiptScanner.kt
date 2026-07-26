package by.mlastovsky.kosht.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import by.mlastovsky.kosht.data.receipt.ml.LineModel
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

    private val model: LineModel? by lazy {
        runCatching { context.assets.open(LineModel.ASSET).use(LineModel::read) }.getOrNull()
    }

    suspend fun scan(uri: Uri): ScannedReceipt? = withContext(Dispatchers.Default) {
        val bitmap = decodeDownscaled(uri, MAX_DIMENSION) ?: return@withContext null
        QrReader.decode(bitmap)?.let { payload ->
            eReceipts.resolve(payload, model)?.let { receipt ->
                return@withContext ScannedReceipt(
                    parsed = receipt.parsed,
                    sourceUrl = receipt.sourceUrl,
                    documentPath = receipt.documentPath
                )
            }
        }

        val quick = ReceiptParser.parse(bestReading(bitmap, Engine.QUICK), model)
        // A slip the quick model cannot settle gets a second reading from the
        // slow and careful one — a few more seconds against giving up on it.
        val parsed = if (quick.amountMinor != null) {
            quick
        } else {
            ReceiptParser.parse(bestReading(bitmap, Engine.CAREFUL), model)
        }
        parsed.takeIf { it.amountMinor != null }?.let { ScannedReceipt(it) }
    }

    /**
     * Two trained models ride along: the small one reads a decent photo in a
     * couple of seconds, the large one takes far longer but gives a stubborn
     * slip its best chance.
     */
    private enum class Engine(val asset: String, val home: String, val stamp: String) {
        QUICK("tessdata", "", "rus-fast-1"),
        CAREFUL("tessdata-best", "best", "rus-best-1")
    }

    private data class Reading(val lines: List<ReceiptLine>, val score: Int)

    /**
     * Every attempt costs seconds, so they run cheapest first and stop as soon
     * as one comes back with figures it is sure about.
     */
    private fun bestReading(bitmap: Bitmap, engine: Engine): List<ReceiptLine> {
        var best = Reading(emptyList(), Int.MIN_VALUE)
        attempts(bitmap, engine).forEach { attempt ->
            val reading = read(attempt.image, attempt.pageMode, engine)
            if (reading.score > best.score) best = reading
            if (reading.score >= GOOD_ENOUGH) return best.lines
        }
        return best.lines
    }

    private data class Attempt(val image: Bitmap, val pageMode: Int)

    private fun attempts(bitmap: Bitmap, engine: Engine): Sequence<Attempt> = sequence {
        val binarised = ImagePrep.binarised(bitmap)
        yield(Attempt(binarised, TessBaseAPI.PageSegMode.PSM_AUTO))
        if (engine == Engine.QUICK) {
            yield(Attempt(binarised, TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK))
            yield(Attempt(ImagePrep.stretched(bitmap), TessBaseAPI.PageSegMode.PSM_AUTO))
            yield(Attempt(bitmap, TessBaseAPI.PageSegMode.PSM_AUTO))
        } else {
            yield(Attempt(ImagePrep.stretched(bitmap), TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK))
        }
    }

    private fun read(bitmap: Bitmap, pageMode: Int, engine: Engine): Reading {
        val dataDir = ensureTrainedData(engine) ?: return Reading(emptyList(), Int.MIN_VALUE)
        val tess = TessBaseAPI()
        return try {
            if (!tess.init(dataDir.absolutePath, LANGUAGE)) {
                return Reading(emptyList(), Int.MIN_VALUE)
            }
            tess.pageSegMode = pageMode
            tess.setVariable("preserve_interword_spaces", "1")
            tess.setImage(bitmap)

            val plain = tess.utF8Text.orEmpty()
            val lines = measuredLines(tess).ifEmpty { ReceiptLine.of(plain) }
                .map { it.copy(text = it.text.trim()) }
                .filter { it.text.isNotEmpty() }
            Reading(lines, score(lines, tess.meanConfidence()))
        } catch (e: Exception) {
            Reading(emptyList(), Int.MIN_VALUE)
        } finally {
            tess.recycle()
        }
    }

    private fun score(lines: List<ReceiptLine>, confidence: Int): Int {
        if (lines.isEmpty()) return Int.MIN_VALUE
        val amounts = lines.count { AMOUNT.containsMatchIn(it.text) }
        val letters = lines.sumOf { line -> line.text.count { it.isLetter() } }
        return confidence + amounts * AMOUNT_WEIGHT + (letters / 40).coerceAtMost(20)
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

    private fun ensureTrainedData(engine: Engine): File? = runCatching {
        // Tesseract wants the folder that holds "tessdata", not the file.
        val home = if (engine.home.isEmpty()) {
            context.filesDir
        } else {
            File(context.filesDir, engine.home).apply { mkdirs() }
        }
        val dir = File(home, "tessdata").apply { mkdirs() }
        val target = File(dir, "$LANGUAGE.traineddata")
        // An app update can bring a different model, and the copy unpacked on
        // disk has to follow it. The stamp beside it says which one is there.
        val stamp = File(dir, "model")
        val current = stamp.takeIf { it.exists() }?.readText()
        if (target.length() == 0L || current != engine.stamp) {
            context.assets.open("${engine.asset}/$LANGUAGE.traineddata").use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            stamp.writeText(engine.stamp)
        }
        home
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
        const val MAX_DIMENSION = 2600

        val AMOUNT = Regex("(?<!\\d)\\d{1,9}[.,]\\d{2}(?!\\d)")

        const val AMOUNT_WEIGHT = 6

        const val GOOD_ENOUGH = 110
    }
}

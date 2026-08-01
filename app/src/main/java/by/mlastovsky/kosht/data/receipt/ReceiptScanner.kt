package by.mlastovsky.kosht.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
        val electronic = QrReader.decode(bitmap)?.let { eReceipts.resolve(it, model) }
        if (electronic != null && electronic.parsed.tellsEverything) {
            return@withContext ScannedReceipt(
                parsed = electronic.parsed,
                sourceUrl = electronic.sourceUrl,
                documentPath = electronic.documentPath
            )
        }

        val fromDocument = electronic?.documentPath
            ?.let(::firstPage)
            ?.let(::readPaper)
        val withDocument = ReceiptParser.combined(electronic?.parsed, fromDocument)
        if (withDocument != null && withDocument.tellsEverything) {
            return@withContext ScannedReceipt(
                parsed = withDocument,
                sourceUrl = electronic?.sourceUrl,
                documentPath = electronic?.documentPath
            )
        }

        val paper = readPaper(bitmap)
        val parsed = ReceiptParser.combined(withDocument ?: electronic?.parsed, paper)
        parsed
            ?.takeIf { it.amountMinor != null }
            ?.let {
                ScannedReceipt(
                    parsed = it,
                    sourceUrl = electronic?.sourceUrl,
                    documentPath = electronic?.documentPath
                )
            }
    }

    private fun firstPage(documentPath: String): Bitmap? = runCatching {
        val file = File(documentPath)
        if (!file.name.endsWith(".pdf", ignoreCase = true) || !file.exists()) return null
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val scale = (PDF_WIDTH.toFloat() / page.width).coerceAtMost(PDF_MAX_SCALE)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }.getOrNull()

    private fun readPaper(bitmap: Bitmap): ParsedReceipt {
        val quick = ReceiptParser.parse(bestReading(bitmap, Engine.QUICK), model)
        return if (quick.amountMinor != null) {
            quick
        } else {
            ReceiptParser.parse(bestReading(bitmap, Engine.CAREFUL), model)
        }
    }

    private enum class Engine(val asset: String, val home: String, val stamp: String) {
        QUICK("tessdata", "", "rus-fast-1"),
        CAREFUL("tessdata-best", "best", "rus-best-1")
    }

    private data class Reading(val lines: List<ReceiptLine>, val score: Int)

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
        val home = if (engine.home.isEmpty()) {
            context.filesDir
        } else {
            File(context.filesDir, engine.home).apply { mkdirs() }
        }
        val dir = File(home, "tessdata").apply { mkdirs() }
        val target = File(dir, "$LANGUAGE.traineddata")
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

        const val PDF_WIDTH = 1600
        const val PDF_MAX_SCALE = 4f

        val AMOUNT = Regex("(?<!\\d)\\d{1,9}[.,]\\d{2}(?!\\d)")

        const val AMOUNT_WEIGHT = 6

        const val GOOD_ENOUGH = 110
    }
}

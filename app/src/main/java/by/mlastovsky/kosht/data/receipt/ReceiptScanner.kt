package by.mlastovsky.kosht.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device receipt OCR (Tesseract with the Russian fast model bundled in
 * assets). Fully offline: the photo never leaves the phone.
 */
class ReceiptScanner(private val context: Context) {

    suspend fun scan(uri: Uri): ParsedReceipt? = withContext(Dispatchers.Default) {
        val bitmap = decodeDownscaled(uri, MAX_DIMENSION) ?: return@withContext null
        val text = recognize(bitmap) ?: return@withContext null
        ReceiptParser.parse(text).takeIf { it.amountMinor != null }
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

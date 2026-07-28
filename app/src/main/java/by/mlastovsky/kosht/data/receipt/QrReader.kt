package by.mlastovsky.kosht.data.receipt

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer

object QrReader {

    private val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8",
        DecodeHintType.POSSIBLE_FORMATS to listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC,
            BarcodeFormat.PDF_417,
            BarcodeFormat.CODE_128
        )
    )

    fun decode(bitmap: Bitmap): String? = runCatching {
        regions(bitmap).firstNotNullOfOrNull { region -> read(region) }
    }.getOrNull()

    private fun regions(bitmap: Bitmap): Sequence<Bitmap> = sequence {
        yield(bitmap)
        halved(bitmap)?.let { yield(it) }
        val width = bitmap.width
        val height = bitmap.height
        if (width < 320 || height < 320) return@sequence

        yieldAll(tiles(bitmap, 2))
        yieldAll(tiles(bitmap, 3))
    }

    private fun halved(source: Bitmap): Bitmap? {
        if (source.width < 1400 && source.height < 1400) return null
        return runCatching {
            Bitmap.createScaledBitmap(source, source.width / 2, source.height / 2, true)
        }.getOrNull()
    }

    private fun tiles(source: Bitmap, grid: Int): List<Bitmap> {
        val step = 1f / (grid + 1)
        val span = 2f / (grid + 1)
        val cells = mutableListOf<Bitmap>()
        for (row in 0..grid) {
            for (column in 0..grid) {
                val x = (source.width * step * column).toInt()
                val y = (source.height * step * row).toInt()
                val width = (source.width * span).toInt().coerceAtMost(source.width - x)
                val height = (source.height * span).toInt().coerceAtMost(source.height - y)
                if (width < 160 || height < 160) continue
                runCatching { Bitmap.createBitmap(source, x, y, width, height) }
                    .getOrNull()
                    ?.let { cells += it }
            }
        }
        return cells
    }

    private fun read(bitmap: Bitmap): String? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        return listOf(source, source.invert()).firstNotNullOfOrNull { attempt(it) }
    }

    private fun attempt(source: LuminanceSource): String? = listOf(
        BinaryBitmap(HybridBinarizer(source)),
        BinaryBitmap(GlobalHistogramBinarizer(source))
    ).firstNotNullOfOrNull { binary ->
        runCatching { MultiFormatReader().decode(binary, hints).text }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }
}

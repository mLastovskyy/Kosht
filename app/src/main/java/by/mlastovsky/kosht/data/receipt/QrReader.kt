package by.mlastovsky.kosht.data.receipt

import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

object QrReader {

    private val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )

    fun decode(bitmap: Bitmap): String? =
        candidates(bitmap).firstNotNullOfOrNull { region -> read(region) }

    private fun candidates(bitmap: Bitmap): Sequence<Bitmap> = sequence {
        yield(bitmap)
        val height = bitmap.height
        val width = bitmap.width
        if (height < 200 || width < 100) return@sequence
        yieldAll(
            listOfNotNull(
                crop(bitmap, 0, height / 2, width, height - height / 2),
                crop(bitmap, 0, 0, width, height / 2)
            )
        )
    }

    private fun crop(source: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap? =
        runCatching { Bitmap.createBitmap(source, x, y, width, height) }.getOrNull()

    private fun read(bitmap: Bitmap): String? = runCatching {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)

        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

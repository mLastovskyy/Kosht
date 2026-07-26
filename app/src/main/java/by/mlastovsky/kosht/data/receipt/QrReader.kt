package by.mlastovsky.kosht.data.receipt

import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * Finds a QR code in a photographed receipt, entirely offline.
 *
 * Receipts come in two shapes: a full slip with a small QR printed at the
 * bottom, and a short slip that is little more than the QR itself. A single
 * whole-image pass reads the second kind but often misses the first, where
 * the code is a few hundred pixels inside a tall photo — so the crops below
 * give the decoder a second and third look at the likely spots.
 */
object QrReader {

    private val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.CHARACTER_SET to "UTF-8"
    )

    fun decode(bitmap: Bitmap): String? =
        candidates(bitmap).firstNotNullOfOrNull { region -> read(region) }

    /** Whole image first, then the halves a receipt QR usually sits in. */
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
        // A fresh reader per attempt: QRCodeReader keeps state between decodes.
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

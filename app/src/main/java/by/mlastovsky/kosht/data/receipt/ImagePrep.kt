package by.mlastovsky.kosht.data.receipt

import android.graphics.Bitmap
import androidx.core.graphics.scale

object ImagePrep {

    fun grey(source: Bitmap): IntArray {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        return IntArray(pixels.size) { at ->
            val pixel = pixels[at]
            (
                ((pixel shr 16 and 0xFF) * 299 +
                    (pixel shr 8 and 0xFF) * 587 +
                    (pixel and 0xFF) * 114) / 1000
                ).coerceIn(0, 255)
        }
    }

    fun stretched(source: Bitmap): Bitmap = runCatching {
        val grey = grey(source)
        var darkest = 255
        var lightest = 0
        grey.forEach { value ->
            if (value < darkest) darkest = value
            if (value > lightest) lightest = value
        }
        val span = lightest - darkest
        if (span < MIN_SPAN) return@runCatching source
        val pixels = IntArray(grey.size) { at ->
            val value = (grey[at] - darkest) * 255 / span
            0xFF shl 24 or (value shl 16) or (value shl 8) or value
        }
        upscaled(
            Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
        )
    }.getOrDefault(source)

    /**
     * Paper photographed by hand is never lit evenly: one half of the slip is in
     * shadow, the other blown out. A single threshold loses one of them, so every
     * pixel is compared with the average of the paper around it instead.
     */
    fun binarised(source: Bitmap): Bitmap = runCatching {
        val width = source.width
        val height = source.height
        val grey = grey(source)
        val radius = (minOf(width, height) / 24).coerceIn(6, 40)
        val mean = boxMean(grey, width, height, radius)
        val pixels = IntArray(grey.size) { at ->
            val ink = grey[at] < mean[at] - BIAS
            if (ink) BLACK else WHITE
        }
        upscaled(Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888))
    }.getOrDefault(source)

    private fun upscaled(source: Bitmap): Bitmap {
        val smallest = minOf(source.width, source.height)
        if (smallest >= MIN_DIMENSION) return source
        val scale = (MIN_DIMENSION.toFloat() / smallest).coerceAtMost(MAX_UPSCALE)
        return source.scale((source.width * scale).toInt(), (source.height * scale).toInt())
    }

    private fun boxMean(grey: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val rows = IntArray(grey.size)
        for (y in 0 until height) {
            val start = y * width
            var window = 0
            for (x in 0..minOf(radius, width - 1)) window += grey[start + x]
            for (x in 0 until width) {
                rows[start + x] = window
                val leaving = x - radius
                val entering = x + radius + 1
                if (leaving >= 0) window -= grey[start + leaving]
                if (entering < width) window += grey[start + entering]
            }
        }
        val mean = IntArray(grey.size)
        for (x in 0 until width) {
            var window = 0
            for (y in 0..minOf(radius, height - 1)) window += rows[y * width + x]
            for (y in 0 until height) {
                val left = maxOf(0, x - radius)
                val right = minOf(width - 1, x + radius)
                val top = maxOf(0, y - radius)
                val bottom = minOf(height - 1, y + radius)
                val count = (right - left + 1) * (bottom - top + 1)
                mean[y * width + x] = window / count
                val leaving = y - radius
                val entering = y + radius + 1
                if (leaving >= 0) window -= rows[leaving * width + x]
                if (entering < height) window += rows[entering * width + x]
            }
        }
        return mean
    }

    private const val BIAS = 10

    private const val MIN_SPAN = 32

    private const val MIN_DIMENSION = 1000

    private const val MAX_UPSCALE = 2f

    private const val BLACK = 0xFF000000.toInt()

    private const val WHITE = 0xFFFFFFFF.toInt()
}

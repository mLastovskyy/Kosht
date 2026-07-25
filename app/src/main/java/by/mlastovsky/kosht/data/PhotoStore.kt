package by.mlastovsky.kosht.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * App-private storage for attached receipt photos. Images are downscaled and
 * recompressed so attachments stay small.
 */
class PhotoStore(private val context: Context) {

    suspend fun saveFromUri(uri: Uri, subdir: String = "receipts"): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = decodeDownscaled(uri) ?: return@runCatching null
                val dir = File(context.filesDir, subdir).apply { mkdirs() }
                val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                file.absolutePath
            }.getOrNull()
        }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val file = File(path)
            // Only ever touch our own attachment directories.
            if (file.parentFile?.name in setOf("receipts", "profile")) file.delete()
        }
    }

    private fun decodeDownscaled(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (
            bounds.outWidth / (sampleSize * 2) >= MAX_DIMENSION / 2 &&
            bounds.outHeight / (sampleSize * 2) >= MAX_DIMENSION / 2
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private companion object {
        const val MAX_DIMENSION = 1600
        const val JPEG_QUALITY = 85
    }
}

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

    /** Stores bytes that already are a JPEG, e.g. one fetched from the cloud. */
    suspend fun save(bytes: ByteArray, subdir: String = "receipts"): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.filesDir, subdir).apply { mkdirs() }
                val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
                file.writeBytes(bytes)
                file.absolutePath
            }.getOrNull()
        }

    /**
     * Deletes attachment files nothing points at any more.
     *
     * They appear in ordinary use: a scan whose review was cancelled after the
     * photo was already saved, a record deleted while the app was killed before
     * the undo offer expired, a photo replaced by another. Each one is a few
     * hundred kilobytes of storage belonging to a record that no longer exists,
     * and nothing else in the app is in a position to notice.
     *
     * Deliberately conservative: only the app's own attachment directories, and
     * only files older than a few minutes — a path handed to the editor a second
     * ago is not in the database yet and must not be swept out from under it.
     */
    suspend fun purgeOrphans(referenced: Set<String>): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - GRACE_MILLIS
        var deleted = 0
        listOf("receipts").forEach { name ->
            val dir = File(context.filesDir, name)
            dir.listFiles()?.forEach { file ->
                if (file.isFile &&
                    file.lastModified() < cutoff &&
                    file.absolutePath !in referenced
                ) {
                    if (file.delete()) deleted++
                }
            }
        }
        deleted
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

        /** How long a fresh file is left alone before it counts as orphaned. */
        const val GRACE_MILLIS = 10 * 60 * 1000L
    }
}

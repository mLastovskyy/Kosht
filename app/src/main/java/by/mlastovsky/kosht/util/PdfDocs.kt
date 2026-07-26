package by.mlastovsky.kosht.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi

/**
 * Hands a bundled document to the phone rather than only showing it.
 *
 * The terms, the privacy policy and the manual are things a person may want to
 * keep, print or send on — so a tap writes a real PDF into Downloads, where it
 * stays after the app is gone, and then opens it. Android 9 and older have no
 * Downloads collection an app may write to without asking for storage rights,
 * so there the document is opened from the app's own cache as before.
 */
object PdfDocs {

    sealed interface Outcome {
        /** Written to Downloads under this name. */
        data class Saved(val fileName: String) : Outcome

        /** Shown in a viewer, but not kept anywhere the user can find later. */
        data object Opened : Outcome

        data object Failed : Outcome
    }

    fun download(context: Context, assetName: String, fileName: String): Outcome {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToDownloads(context, assetName, fileName)?.let { uri ->
                // Opening is best-effort: the file is already saved, and a
                // phone with no PDF viewer should not look like a failure.
                open(context, uri)
                return Outcome.Saved(fileName)
            }
        }
        return if (AssetPdf.open(context, assetName, fileName)) Outcome.Opened else Outcome.Failed
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloads(context: Context, assetName: String, fileName: String): Uri? =
        runCatching {
            val resolver = context.contentResolver
            val collection =
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            // Replace our own earlier copy instead of leaving a trail of
            // "kosht-terms (1).pdf" behind after every tap.
            runCatching {
                resolver.delete(
                    collection,
                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                    arrayOf(fileName)
                )
            }
            val pending = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, MIME_PDF)
                // Hidden from other apps until the bytes are actually there.
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, pending) ?: return null
            resolver.openOutputStream(uri)?.use { output ->
                context.assets.open(assetName).use { input -> input.copyTo(output) }
            } ?: return null
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )
            uri
        }.getOrNull()

    private fun open(context: Context, uri: Uri) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, MIME_PDF)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    private const val MIME_PDF = "application/pdf"
}

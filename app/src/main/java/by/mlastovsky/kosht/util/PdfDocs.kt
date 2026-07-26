package by.mlastovsky.kosht.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi

object PdfDocs {

    sealed interface Outcome {

        data class Saved(val fileName: String) : Outcome

        data object Opened : Outcome

        data object Failed : Outcome
    }

    fun download(context: Context, assetName: String, fileName: String): Outcome {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToDownloads(context, assetName, fileName)?.let { uri ->

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

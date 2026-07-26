package by.mlastovsky.kosht.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object AssetPdf {

    fun open(context: Context, assetName: String, fileName: String): Boolean = runCatching {
        val dir = File(context.cacheDir, "docs").apply { mkdirs() }
        val file = File(dir, fileName)
        context.assets.open(assetName).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        true
    }.getOrDefault(false)
}

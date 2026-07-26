package by.mlastovsky.kosht.ui.components

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberBitmapFromPath(path: String?, maxDimension: Int = 1280): ImageBitmap? {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) { decode(path, maxDimension)?.asImageBitmap() }
        }
    }
    return bitmap
}

@Composable
fun rememberBitmapFromUri(uri: Uri?, maxDimension: Int = 1280): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = if (uri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                decodeUri(context.contentResolver, uri, maxDimension)?.asImageBitmap()
            }
        }
    }
    return bitmap
}

private fun decodeUri(
    resolver: ContentResolver,
    uri: Uri,
    maxDimension: Int
): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0) return null
    var sampleSize = 1
    while (
        bounds.outWidth / (sampleSize * 2) >= maxDimension / 2 &&
        bounds.outHeight / (sampleSize * 2) >= maxDimension / 2
    ) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}.getOrNull()

private fun decode(path: String, maxDimension: Int): Bitmap? = runCatching {
    if (!File(path).exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    var sampleSize = 1
    while (
        bounds.outWidth / (sampleSize * 2) >= maxDimension / 2 &&
        bounds.outHeight / (sampleSize * 2) >= maxDimension / 2
    ) {
        sampleSize *= 2
    }
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
}.getOrNull()

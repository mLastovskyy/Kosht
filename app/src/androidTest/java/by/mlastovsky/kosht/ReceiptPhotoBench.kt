package by.mlastovsky.kosht

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import by.mlastovsky.kosht.data.receipt.ReceiptScanner
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptPhotoBench {

    @Test
    fun readsThePhotographsItWasGiven() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val folders = listOfNotNull(
            File(context.filesDir, "bench"),
            context.getExternalFilesDir(null)?.let { File(it, "bench") }
        )
        val folder = folders.firstOrNull { dir ->
            dir.listFiles { file -> file.extension.lowercase() in IMAGES }?.isNotEmpty() == true
        }
        if (folder == null) {
            Log.i(TAG, "no photos in ${folders.joinToString { it.absolutePath }}")
            return@runBlocking
        }
        val photos = folder.listFiles { file -> file.extension.lowercase() in IMAGES }
            ?.sortedBy { it.name }
            .orEmpty()

        val scanner = ReceiptScanner(context)
        var expected = 0
        var right = 0
        photos.forEach { photo ->
            val started = SystemClock.uptimeMillis()
            val scanned = scanner.scan(Uri.fromFile(photo))
            val took = SystemClock.uptimeMillis() - started
            val wanted = File(folder, photo.nameWithoutExtension + ".total")
                .takeIf { it.exists() }
                ?.readText()
                ?.trim()
                ?.toLongOrNull()
            val read = scanned?.parsed?.amountMinor
            if (wanted != null) {
                expected++
                if (read == wanted) right++
            }
            Log.i(
                TAG,
                "${photo.name}: total=$read wanted=$wanted " +
                    "merchant=${scanned?.parsed?.merchant} " +
                    "items=${scanned?.parsed?.items?.size} in ${took}ms" +
                    scanned?.parsed?.items?.joinToString("; ", prefix = " | ") {
                        "${it.name}=${it.amountMinor}"
                    }.orEmpty()
            )
        }
        Log.i(TAG, "score: $right / $expected")
        assertTrue("no photo was read at all", expected == 0 || right > 0)
    }

    private companion object {
        const val TAG = "ReceiptBench"
        val IMAGES = setOf("jpg", "jpeg", "png", "webp")
    }
}

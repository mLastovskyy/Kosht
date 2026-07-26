package by.mlastovsky.kosht.ui.editor

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import by.mlastovsky.kosht.R
import java.io.File

@Composable
fun EReceiptDialog(
    url: String?,
    documentPath: String?,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val savedCopy = documentPath?.let { File(it) }?.takeIf { it.isFile && it.length() > 0 }
    val target = savedCopy?.let { "file://${it.absolutePath}" } ?: url

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ereceipt_title)) },
        text = {
            if (target == null) {
                Text(stringResource(R.string.ereceipt_unavailable))
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f)
                ) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = false
                                settings.allowFileAccess = savedCopy != null
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                            }
                        },
                        update = { it.loadUrl(target) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        dismissButton = {
            TextButton(onClick = onRemove) {
                Text(
                    text = stringResource(R.string.ereceipt_remove),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

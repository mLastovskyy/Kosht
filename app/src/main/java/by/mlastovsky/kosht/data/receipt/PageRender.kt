package by.mlastovsky.kosht.data.receipt

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener

class PageRender(private val context: Context) {

    suspend fun html(url: String): String? = withContext(Dispatchers.Main) {
        val view = runCatching { WebView(context) }.getOrNull() ?: return@withContext null
        try {
            prepare(view)
            val loaded = CompletableDeferred<Unit>()
            view.webViewClient = object : WebViewClient() {

                override fun onPageFinished(view: WebView, url: String) {
                    loaded.complete(Unit)
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    if (request.isForMainFrame) loaded.complete(Unit)
                }
            }
            view.loadUrl(url)
            withTimeoutOrNull(LOAD_WAIT) { loaded.await() }
            settled(view)
        } catch (e: Exception) {
            null
        } finally {
            view.stopLoading()
            view.destroy()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun prepare(view: WebView) {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = false
            blockNetworkImage = true
            userAgentString = BROWSER_AGENT
        }
        view.measure(
            View.MeasureSpec.makeMeasureSpec(VIEWPORT_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(VIEWPORT_HEIGHT, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
    }

    private suspend fun settled(view: WebView): String? {
        var previous: String? = null
        repeat(SETTLE_TRIES) {
            delay(SETTLE_STEP)
            val html = read(view) ?: return previous
            if (html == previous && ReceiptQr.showsReceipt(html)) return html
            previous = html
        }
        return previous
    }

    private suspend fun read(view: WebView): String? = suspendCancellableCoroutine { waiting ->
        view.evaluateJavascript("document.documentElement.outerHTML") { value ->
            waiting.resume(value?.let(::unquoted))
        }
    }

    private fun unquoted(value: String): String? =
        runCatching { JSONTokener(value).nextValue() as? String }.getOrNull()

    companion object {

        const val BROWSER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0 Mobile Safari/537.36"

        fun keepable(html: String, url: String): String {
            val still = html.replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
            val base = "<base href=\"" + url.replace("\"", "%22") + "\">"
            val head = Regex("(?i)<head[^>]*>").find(still) ?: return base + still
            return still.substring(0, head.range.last + 1) + base +
                still.substring(head.range.last + 1)
        }

        private const val LOAD_WAIT = 12_000L
        private const val SETTLE_STEP = 400L
        private const val SETTLE_TRIES = 12
        private const val VIEWPORT_WIDTH = 1080
        private const val VIEWPORT_HEIGHT = 2400
    }
}

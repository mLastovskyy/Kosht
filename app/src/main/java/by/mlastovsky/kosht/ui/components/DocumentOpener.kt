package by.mlastovsky.kosht.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.util.PdfDocs

@Composable
fun rememberDocumentOpener(): (asset: String, fileName: String) -> Unit {
    val context = LocalContext.current
    val savedTemplate = stringResource(R.string.doc_saved)
    val failed = stringResource(R.string.guide_pdf_error)
    return { asset, fileName ->
        val message = when (val outcome = PdfDocs.download(context, asset, fileName)) {
            is PdfDocs.Outcome.Saved -> String.format(savedTemplate, outcome.fileName)
            PdfDocs.Outcome.Opened -> null
            PdfDocs.Outcome.Failed -> failed
        }
        message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
}

object LegalDocs {
    const val TERMS_ASSET = "legal/terms.pdf"
    const val TERMS_FILE = "kosht-terms.pdf"
    const val PRIVACY_ASSET = "legal/privacy-policy.pdf"
    const val PRIVACY_FILE = "kosht-privacy-policy.pdf"
}

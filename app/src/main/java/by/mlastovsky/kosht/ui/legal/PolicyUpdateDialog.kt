package by.mlastovsky.kosht.ui.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.ui.components.LegalDocs
import by.mlastovsky.kosht.ui.components.rememberDocumentOpener

/**
 * Says that the Terms and the data policy have changed since the version this
 * person was last shown, and offers both documents right there.
 *
 * A person who agreed to one text should not silently end up bound by another,
 * so this is asked once per version rather than left to be discovered in
 * Settings — and it cannot be dismissed by tapping outside: acknowledging it is
 * the point. [onAcknowledge] is what records that it happened.
 */
@Composable
fun PolicyUpdateDialog(onAcknowledge: () -> Unit) {
    val openDocument = rememberDocumentOpener()

    AlertDialog(
        // Only the button closes it; a stray tap is not an acknowledgement.
        onDismissRequest = { },
        title = { Text(stringResource(R.string.policy_updated_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.policy_updated_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DocumentButton(
                    labelRes = R.string.legal_terms,
                    icon = Icons.AutoMirrored.Rounded.Article,
                    onClick = { openDocument(LegalDocs.TERMS_ASSET, LegalDocs.TERMS_FILE) }
                )
                DocumentButton(
                    labelRes = R.string.legal_privacy,
                    icon = Icons.Rounded.PrivacyTip,
                    onClick = { openDocument(LegalDocs.PRIVACY_ASSET, LegalDocs.PRIVACY_FILE) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAcknowledge) {
                Text(stringResource(R.string.policy_updated_accept))
            }
        }
    )
}

@Composable
private fun DocumentButton(
    labelRes: Int,
    icon: ImageVector,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, Modifier.size(18.dp))
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

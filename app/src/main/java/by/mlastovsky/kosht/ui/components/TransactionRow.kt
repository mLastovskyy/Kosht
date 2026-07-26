package by.mlastovsky.kosht.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.data.db.AccountEntity
import by.mlastovsky.kosht.data.db.TransactionWithCategory
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.ui.CategoryVisuals
import by.mlastovsky.kosht.ui.theme.KoshtTheme
import by.mlastovsky.kosht.ui.transfer.transferDetails
import by.mlastovsky.kosht.ui.transfer.transferRoute
import by.mlastovsky.kosht.util.Money

@Composable
fun TransactionRow(
    item: TransactionWithCategory,
    currencyCode: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    /** Needed to name both ends of a transfer; ordinary records ignore it. */
    accounts: List<AccountEntity> = emptyList()
) {
    val transaction = item.transaction
    val isIncome = transaction.type == TransactionType.INCOME
    val transfer = transaction.isTransfer
    val amountText = when {
        // A transfer is neither income nor expense: the money is still there,
        // so it carries no sign — only what left the source account.
        transfer -> Money.format(transaction.transferTotalMinor, currencyCode)
        isIncome -> "+" + Money.format(transaction.amountMinor, currencyCode)
        else -> "−" + Money.format(transaction.amountMinor, currencyCode)
    }
    val amountColor = when {
        transfer -> MaterialTheme.colorScheme.onSurfaceVariant
        isIncome -> KoshtTheme.colors.income
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (transfer) {
            TransferBadge()
        } else {
            CategoryBadge(
                iconKey = item.category.iconKey,
                color = Color(item.category.colorArgb)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (transfer) {
                        transferRoute(transaction, accounts)
                    } else {
                        CategoryVisuals.displayName(item.category)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                // Where the figures came from, long after the scan itself.
                if (transaction.scanned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.DocumentScanner,
                        contentDescription = stringResource(R.string.scanned_mark),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            val secondary = when {
                transfer -> transferDetails(transaction, currencyCode)
                else -> supportingText ?: transaction.note
            }
            if (secondary.isNotBlank()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = amountText,
                style = MaterialTheme.typography.titleMedium,
                color = amountColor
            )
            val bynMinor = transaction.bynMinor
            if (currencyCode != "BYN" && bynMinor != null) {
                Text(
                    text = "≈ " + Money.format(bynMinor, "BYN"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** A transfer belongs to no category, so it gets a badge of its own. */
@Composable
private fun TransferBadge() {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.SwapHoriz,
            contentDescription = stringResource(R.string.transfer_title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

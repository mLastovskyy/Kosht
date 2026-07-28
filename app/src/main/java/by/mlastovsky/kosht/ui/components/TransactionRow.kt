package by.mlastovsky.kosht.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.data.db.AccountEntity
import by.mlastovsky.kosht.data.db.TransactionItemEntity
import by.mlastovsky.kosht.data.db.TransactionWithCategory
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.ui.CategoryVisuals
import by.mlastovsky.kosht.ui.countedAt
import by.mlastovsky.kosht.ui.tabular
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

    accounts: List<AccountEntity> = emptyList(),

    items: List<TransactionItemEntity> = emptyList(),
    itemsShown: Boolean = false,
    onItemsClick: (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        RecordRow(
            item = item,
            currencyCode = currencyCode,
            onClick = onClick,
            supportingText = supportingText,
            accounts = accounts,
            items = items,
            itemsShown = itemsShown,
            onItemsClick = onItemsClick
        )
        AnimatedVisibility(
            visible = itemsShown && items.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ItemsPanel(items = items, currencyCode = currencyCode)
        }
    }
}

@Composable
private fun ItemsPanel(items: List<TransactionItemEntity>, currencyCode: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PANEL_SIDE, end = PANEL_SIDE, bottom = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(
                start = TEXT_START - PANEL_SIDE,
                end = ROW_SIDE - PANEL_SIDE,
                top = 12.dp,
                bottom = 12.dp
            )
    ) {
        items.forEach { line ->
            ItemLine(item = line, currencyCode = currencyCode)
        }
    }
}

@Composable
private fun ItemLine(item: TransactionItemEntity, currencyCode: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TruncatedText(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = Money.format(item.amountMinor, currencyCode),
                style = MaterialTheme.typography.bodyMedium.tabular(),
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
        val counted = countedAt(item.quantity, item.amountMinor, currencyCode)
        if (counted != null) {
            Text(
                text = counted,
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RecordRow(
    item: TransactionWithCategory,
    currencyCode: String,
    onClick: () -> Unit,
    supportingText: String?,
    accounts: List<AccountEntity>,
    items: List<TransactionItemEntity>,
    itemsShown: Boolean,
    onItemsClick: (() -> Unit)?
) {
    val transaction = item.transaction
    val isIncome = transaction.type == TransactionType.INCOME
    val transfer = transaction.isTransfer
    val amountText = when {

        transfer -> Money.format(transaction.transferTotalMinor, currencyCode)
        isIncome -> "+" + Money.format(transaction.amountMinor, currencyCode)
        else -> "−" + Money.format(transaction.amountMinor, currencyCode)
    }
    val amountColor = when {
        transfer -> MaterialTheme.colorScheme.onSurfaceVariant
        isIncome -> KoshtTheme.colors.income
        else -> MaterialTheme.colorScheme.onSurface
    }
    val openItems = onItemsClick?.takeIf { items.isNotEmpty() }

    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = ROW_SIDE, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (transfer) {
            TransferBadge()
        } else {
            CategoryBadge(
                iconKey = item.category.iconKey,
                color = Color(item.category.colorArgb),
                iconPath = item.category.iconPath
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TruncatedText(
                    text = if (transfer) {
                        transferRoute(transaction, accounts)
                    } else {
                        CategoryVisuals.displayName(item.category)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f, fill = false)
                )

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
            if (openItems != null || secondary.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (openItems != null) {
                        ItemsChip(
                            count = items.size,
                            expanded = itemsShown,
                            onClick = openItems
                        )
                    }
                    if (secondary.isNotBlank()) {
                        TruncatedText(
                            text = secondary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = amountText,
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = amountColor
            )
            val bynMinor = transaction.bynMinor
            if (currencyCode != "BYN" && bynMinor != null) {
                Text(
                    text = "≈ " + Money.format(bynMinor, "BYN"),
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

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

private val ROW_SIDE = 16.dp

private val PANEL_SIDE = 8.dp

private val TEXT_START = 72.dp

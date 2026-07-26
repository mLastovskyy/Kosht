package by.mlastovsky.kosht.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.data.ItemDraft
import by.mlastovsky.kosht.data.ItemSuggestions
import by.mlastovsky.kosht.ui.components.TextInput
import by.mlastovsky.kosht.util.Money

@Composable
fun ItemsDialog(
    items: List<ItemDraft>,
    suggestions: List<String>,
    categoryKey: String?,
    currencyCode: String,

    recordAmountMinor: Long,
    onAdd: (name: String, priceMinor: Long, quantity: Double?) -> Unit,
    onUpdate: (index: Int, name: String, priceMinor: Long, quantity: Double?) -> Unit,
    onRemove: (index: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("") }

    var editing by remember { mutableIntStateOf(-1) }

    fun reset() {
        name = ""
        priceText = ""
        quantityText = ""
        editing = -1
    }

    fun submit() {
        val price = Money.parseToMinor(priceText, currencyCode) ?: 0L
        val quantity = quantityText.replace(',', '.').toDoubleOrNull()
        if (editing >= 0) {
            onUpdate(editing, name, price, quantity)
        } else {
            onAdd(name, price, quantity)
        }
        reset()
    }

    val seeded = ItemSuggestions.forCategory(categoryKey).map { stringResource(it) }
    val typed = name.trim()
    val matching = (suggestions + seeded)
        .distinctBy { it.lowercase() }
        .filter { typed.isEmpty() || it.contains(typed, ignoreCase = true) }
        .filter { suggestion -> items.none { it.name.equals(suggestion, ignoreCase = true) } }
        .take(SUGGESTION_LIMIT)

    val listedMinor = items.sumOf { it.amountMinor }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.items_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (items.isEmpty()) {
                    Text(
                        text = stringResource(R.string.items_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ListedSummary(
                        count = items.size,
                        listedMinor = listedMinor,
                        recordAmountMinor = recordAmountMinor,
                        currencyCode = currencyCode
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        itemsIndexed(items) { index, item ->
                            ItemRow(
                                item = item,
                                currencyCode = currencyCode,
                                highlighted = index == editing,
                                onClick = {
                                    editing = index
                                    name = item.name
                                    priceText = unitPriceInput(item, currencyCode)
                                    quantityText = item.quantity
                                        ?.let { formatQuantity(it) }
                                        .orEmpty()
                                },
                                onRemove = {
                                    if (editing == index) reset()
                                    onRemove(index)
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(NAME_MAX) },
                    label = { Text(stringResource(R.string.items_name)) },
                    singleLine = true,
                    keyboardOptions = TextInput.Sentence,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.take(12) },
                        label = { Text(stringResource(R.string.items_price)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1.4f)
                    )
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it.take(6) },
                        label = { Text(stringResource(R.string.items_quantity)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (matching.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(matching) { suggestion ->
                            AssistChip(
                                onClick = { name = suggestion },
                                label = { Text(suggestion, maxLines = 1) }
                            )
                        }
                    }
                }
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { submit() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
                    Text(
                        text = stringResource(
                            if (editing >= 0) R.string.editor_save else R.string.action_add
                        ),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun ListedSummary(
    count: Int,
    listedMinor: Long,
    recordAmountMinor: Long,
    currencyCode: String
) {
    val counted = pluralStringResource(R.plurals.items_count, count, count)
    val over = recordAmountMinor > 0 && listedMinor > recordAmountMinor
    Text(
        text = when {
            recordAmountMinor <= 0 ->
                stringResource(R.string.items_summary, counted, Money.format(listedMinor, currencyCode))

            else -> stringResource(
                R.string.items_summary_of,
                counted,
                Money.format(listedMinor, currencyCode),
                Money.format(recordAmountMinor, currencyCode)
            )
        },
        style = MaterialTheme.typography.labelLarge,
        color = if (over) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

@Composable
private fun ItemRow(
    item: ItemDraft,
    currencyCode: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val quantity = item.quantity
            if (quantity != null && item.amountMinor > 0) {
                Text(
                    text = formatQuantity(quantity) + " × " +
                        Money.format(unitPrice(item), currencyCode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (item.amountMinor > 0) {
            Text(
                text = Money.format(item.amountMinor, currencyCode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.editor_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

fun unitPrice(item: ItemDraft): Long {
    val quantity = item.quantity ?: return item.amountMinor
    if (quantity <= 0) return item.amountMinor
    return Math.round(item.amountMinor / quantity)
}

private fun unitPriceInput(item: ItemDraft, currencyCode: String): String {
    if (item.amountMinor <= 0) return ""
    return Money.editableText(unitPrice(item), currencyCode)
}

fun formatQuantity(quantity: Double): String {
    if (quantity == quantity.toLong().toDouble()) return quantity.toLong().toString()

    return String.format(java.util.Locale.US, "%.3f", quantity)
        .trimEnd('0')
        .trimEnd('.')
        .replace('.', ',')
}

private const val NAME_MAX = 60
private const val SUGGESTION_LIMIT = 8

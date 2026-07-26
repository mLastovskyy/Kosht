package by.mlastovsky.kosht.ui.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.data.db.AccountEntity
import by.mlastovsky.kosht.data.db.TransactionEntity
import by.mlastovsky.kosht.ui.AccountVisuals
import by.mlastovsky.kosht.ui.AppViewModelProvider
import by.mlastovsky.kosht.ui.CategoryVisuals
import by.mlastovsky.kosht.ui.components.TextInput
import by.mlastovsky.kosht.ui.relativeDate
import by.mlastovsky.kosht.util.Dates
import by.mlastovsky.kosht.util.Money
import by.mlastovsky.kosht.util.Notes
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferDialog(
    initial: TransactionEntity? = null,
    onDismiss: () -> Unit,
    viewModel: TransferViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accounts = state.accounts
    if (accounts.size < 2) return

    var fromId by remember(initial, accounts) {
        mutableStateOf(initial?.accountId ?: accounts.first().id)
    }
    var toId by remember(initial, accounts) {
        mutableStateOf(
            initial?.transferToAccountId ?: accounts.firstOrNull { it.id != fromId }?.id
        )
    }
    var amountText by remember(initial) {
        mutableStateOf(
            initial?.let {
                Money.format(it.amountMinor, state.currencyCode)
                    .filter { char -> char.isDigit() || char == ',' }
            }.orEmpty()
        )
    }
    var feeText by remember(initial) {
        mutableStateOf(
            initial?.takeIf { it.transferFeeMinor > 0 }?.let {
                Money.format(it.transferFeeMinor, state.currencyCode)
                    .filter { char -> char.isDigit() || char == ',' }
            }.orEmpty()
        )
    }
    var note by remember(initial) { mutableStateOf(initial?.note.orEmpty()) }
    var date by remember(initial) {
        mutableStateOf(initial?.let { Dates.toLocalDate(it.timestamp) } ?: Dates.today())
    }
    var showDatePicker by remember { mutableStateOf(false) }

    val amountMinor = Money.parseToMinor(amountText, state.currencyCode) ?: 0L
    val feeMinor = if (state.feeEnabled) {
        Money.parseToMinor(feeText, state.currencyCode) ?: 0L
    } else {
        initial?.transferFeeMinor ?: 0L
    }
    val destination = accounts.firstOrNull { it.id == toId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.transfer_new else R.string.transfer_title
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.transfer_from),
                    style = MaterialTheme.typography.labelLarge
                )
                AccountChips(
                    accounts = accounts,
                    selectedId = fromId,
                    onSelect = { id ->
                        fromId = id

                        if (toId == id) toId = accounts.firstOrNull { it.id != id }?.id
                    }
                )
                Text(
                    text = stringResource(R.string.transfer_to),
                    style = MaterialTheme.typography.labelLarge
                )
                AccountChips(
                    accounts = accounts.filter { it.id != fromId },
                    selectedId = toId,
                    onSelect = { toId = it }
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.take(12) },
                    label = { Text(stringResource(R.string.amount_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.feeEnabled) {
                    OutlinedTextField(
                        value = feeText,
                        onValueChange = { feeText = it.take(12) },
                        label = { Text(stringResource(R.string.transfer_fee)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AssistChip(
                    onClick = { showDatePicker = true },
                    leadingIcon = {
                        Icon(Icons.Rounded.Event, contentDescription = null, Modifier.size(18.dp))
                    },
                    label = { Text(relativeDate(date)) }
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(Notes.MAX_LENGTH) },
                    placeholder = { Text(stringResource(R.string.editor_note_hint)) },
                    singleLine = true,
                    keyboardOptions = TextInput.Sentence,
                    modifier = Modifier.fillMaxWidth()
                )

                if (amountMinor > 0 && feeMinor > 0) {
                    Text(
                        text = stringResource(
                            R.string.transfer_total,
                            Money.format(amountMinor + feeMinor, state.currencyCode)
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amountMinor > 0 && destination != null,
                onClick = {
                    viewModel.save(
                        original = initial,
                        fromAccountId = fromId,
                        toAccountId = destination!!.id,
                        amountMinor = amountMinor,
                        feeMinor = feeMinor,
                        note = note,
                        date = date,
                        onDone = onDismiss
                    )
                }
            ) {
                Text(
                    stringResource(
                        if (initial == null) R.string.action_add else R.string.editor_save
                    )
                )
            }
        },
        dismissButton = {
            if (initial != null) {
                TextButton(onClick = { viewModel.delete(initial, onDismiss) }) {
                    Text(
                        text = stringResource(R.string.editor_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) { Text(stringResource(R.string.action_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

@Composable
private fun AccountChips(
    accounts: List<AccountEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(accounts, key = { it.id }) { account ->
            FilterChip(
                selected = account.id == selectedId,
                onClick = { onSelect(account.id) },
                leadingIcon = {
                    Icon(
                        CategoryVisuals.icon(account.iconKey),
                        contentDescription = null,
                        tint = Color(account.colorArgb),
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = { Text(AccountVisuals.displayName(account), maxLines = 1) }
            )
        }
    }
}

@Composable
fun transferRoute(
    transfer: TransactionEntity,
    accounts: List<AccountEntity>
): String {
    val from = accounts.firstOrNull { it.id == transfer.accountId }
        ?: accounts.firstOrNull().takeIf { transfer.accountId == null }
    val to = accounts.firstOrNull { it.id == transfer.transferToAccountId }
    if (from == null || to == null) return stringResource(R.string.transfer_title)
    return AccountVisuals.displayName(from) + " → " + AccountVisuals.displayName(to)
}

@Composable
fun transferDetails(
    transfer: TransactionEntity,
    currencyCode: String
): String {
    val date = relativeDate(Dates.toLocalDate(transfer.timestamp))
    if (transfer.transferFeeMinor <= 0) return date
    return date + " · " + stringResource(
        R.string.transfer_fee_short,
        Money.format(transfer.transferFeeMinor, currencyCode)
    )
}

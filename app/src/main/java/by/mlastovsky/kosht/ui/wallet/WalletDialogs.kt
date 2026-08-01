package by.mlastovsky.kosht.ui.wallet

import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.ui.components.rememberPictureChoice
import by.mlastovsky.kosht.ui.components.PicturePickerRow
import by.mlastovsky.kosht.ui.components.AccountBadge
import android.net.Uri
import by.mlastovsky.kosht.data.db.AccountEntity
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.data.db.DebtEntity
import by.mlastovsky.kosht.data.db.RecurringEntity
import by.mlastovsky.kosht.data.db.SavingEntity
import by.mlastovsky.kosht.data.db.SavingGoalEntity
import by.mlastovsky.kosht.model.DebtDirection
import by.mlastovsky.kosht.model.RecurringFrequency
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.ui.AccountVisuals
import by.mlastovsky.kosht.ui.CategoryVisuals
import by.mlastovsky.kosht.ui.components.CategoryActions
import by.mlastovsky.kosht.ui.components.CategoryBadge
import by.mlastovsky.kosht.ui.components.CategoryPickerRow
import by.mlastovsky.kosht.ui.components.ConfirmDeleteDialog
import by.mlastovsky.kosht.ui.components.CurrencyChips
import by.mlastovsky.kosht.ui.components.TextInput
import by.mlastovsky.kosht.ui.components.rememberRowScrolledTo
import by.mlastovsky.kosht.ui.relativeDate
import by.mlastovsky.kosht.util.Dates
import by.mlastovsky.kosht.util.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs

@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(12)) },
        placeholder = { Text(stringResource(R.string.amount_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun DebtDialog(
    initial: DebtEntity?,
    defaultCurrency: String,
    onConfirm: (
        name: String,
        direction: DebtDirection,
        amountMinor: Long,
        currency: String,
        note: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.personName ?: "") }
    var direction by remember { mutableStateOf(initial?.direction ?: DebtDirection.I_OWE) }
    var amountText by remember {
        mutableStateOf(initial?.let { Money.editableText(it.amountMinor, it.currencyCode) } ?: "")
    }
    var currency by remember { mutableStateOf(initial?.currencyCode ?: defaultCurrency) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val amountMinor = Money.parseToMinor(amountText, currency) ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.debt_new else R.string.debt_edit))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DirectionToggle(direction) { direction = it }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    placeholder = {
                        Text(
                            stringResource(
                                if (direction == DebtDirection.I_OWE) {
                                    R.string.debt_person_owe
                                } else {
                                    R.string.debt_person_owed
                                }
                            )
                        )
                    },
                    singleLine = true,
                    keyboardOptions = TextInput.Name,
                    modifier = Modifier.fillMaxWidth()
                )
                AmountField(amountText, { amountText = it })
                CurrencyChips(currency) { currency = it }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    placeholder = { Text(stringResource(R.string.editor_note_hint)) },
                    singleLine = true,
                    keyboardOptions = TextInput.Sentence,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && amountMinor > 0,
                onClick = { onConfirm(name, direction, amountMinor, currency, note) }
            ) {
                Text(
                    stringResource(
                        if (initial == null) R.string.action_add else R.string.editor_save
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionToggle(
    direction: DebtDirection,
    onChange: (DebtDirection) -> Unit
) {
    val options = listOf(
        DebtDirection.I_OWE to stringResource(R.string.debt_i_owe),
        DebtDirection.OWED_TO_ME to stringResource(R.string.debt_owed_to_me)
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = direction == option,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label) }
            )
        }
    }
}

@Composable
fun DebtActionsDialog(
    debt: DebtEntity,
    accounts: List<AccountEntity>,
    bornAsRecord: Boolean,
    onRepay: (amountMinor: Long, note: String?, accountId: Long?) -> Unit,
    onClose: (note: String?, accountId: Long?) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var repayText by remember { mutableStateOf("") }
    var record by remember { mutableStateOf(!bornAsRecord) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var accountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }
    val repayMinor = Money.parseToMinor(repayText, debt.currencyCode) ?: 0L

    val closedNote = stringResource(R.string.debt_note_closed, debt.personName)
    val partNote = stringResource(R.string.debt_note_part, debt.personName)
    fun noteFor(amountMinor: Long): String? = when {
        !record -> null
        amountMinor >= debt.amountMinor -> closedNote
        else -> partNote
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = debt.personName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.debt_edit)
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = Money.format(debt.amountMinor, debt.currencyCode),
                    style = MaterialTheme.typography.headlineSmall
                )
                if (debt.note.isNotBlank()) {
                    Text(
                        text = debt.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AmountField(repayText, { repayText = it })
                if (!bornAsRecord) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { record = !record }
                    ) {
                        Checkbox(checked = record, onCheckedChange = { record = it })
                        Text(
                            text = stringResource(R.string.debt_record_history),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (record && accounts.size > 1) {
                        AccountChips(accounts, accountId) { accountId = it }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = repayMinor > 0,
                        onClick = {
                            onRepay(repayMinor, noteFor(repayMinor), accountId.takeIf { record })
                        }
                    ) { Text(stringResource(R.string.debt_repay)) }
                    TextButton(
                        onClick = {
                            onClose(noteFor(debt.amountMinor), accountId.takeIf { record })
                        }
                    ) {
                        Text(stringResource(R.string.debt_close))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        dismissButton = {
            TextButton(onClick = { confirmingDelete = true }) {
                Text(
                    stringResource(R.string.editor_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )

    if (confirmingDelete) {
        ConfirmDeleteDialog(
            name = debt.personName,
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
            onDismiss = { confirmingDelete = false }
        )
    }
}

@Composable
fun AddSavingDialog(
    withdraw: Boolean,
    defaultCurrency: String,
    goals: List<GoalUi>,
    accounts: List<AccountEntity>,
    rateOf: (from: String, to: String) -> Double?,
    ownRate: Boolean,
    onConfirm: (
        amountMinor: Long,
        currency: String,
        note: String,
        goalId: Long?,
        deduct: Boolean,
        accountId: Long?,
        deductMinor: Long?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(defaultCurrency) }
    var note by remember { mutableStateOf("") }
    var goalId by remember { mutableStateOf<Long?>(null) }

    var deduct by remember { mutableStateOf(false) }
    var accountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }

    val selectedGoal = goals.firstOrNull { it.goal.id == goalId }
    val savedIn = selectedGoal?.goal?.currencyCode ?: defaultCurrency
    val typedMinor = Money.parseToMinor(amountText, currency) ?: 0L
    val converting = currency != savedIn
    var rateText by remember { mutableStateOf("") }
    var convertedText by remember { mutableStateOf("") }

    val officialRate = if (converting) rateOf(currency, savedIn) else null

    LaunchedEffect(currency, savedIn) {
        rateText = officialRate?.let { rateLabel(it) }.orEmpty()
    }
    LaunchedEffect(typedMinor, rateText, currency, savedIn) {
        val rate = rateText.replace(',', '.').toDoubleOrNull()
        convertedText = if (!converting || typedMinor <= 0 || rate == null || rate <= 0.0) {
            ""
        } else {
            Money.editableText(Math.round(typedMinor * rate), savedIn)
        }
    }
    val amountMinor = if (converting) {
        Money.parseToMinor(convertedText, savedIn) ?: 0L
    } else {
        typedMinor
    }
    val deductMinor = when {
        currency == defaultCurrency -> typedMinor
        savedIn == defaultCurrency -> amountMinor
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (withdraw) R.string.savings_withdraw else R.string.savings_deposit
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AmountField(amountText, { amountText = it })
                if (!withdraw && goals.isNotEmpty()) {
                    Text(
                        stringResource(R.string.goals_pick),
                        style = MaterialTheme.typography.labelLarge
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = goalId == null,
                                onClick = { goalId = null },
                                label = { Text(stringResource(R.string.goals_none)) }
                            )
                        }
                        items(goals, key = { it.goal.id }) { goalUi ->
                            FilterChip(
                                selected = goalId == goalUi.goal.id,
                                onClick = { goalId = goalUi.goal.id },
                                label = { Text(goalUi.goal.title) }
                            )
                        }
                    }
                }
                CurrencyChips(currency) { currency = it }

                if (converting) {
                    if (ownRate) {
                        OutlinedTextField(
                            value = rateText,
                            onValueChange = { rateText = it.take(10) },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.savings_rate,
                                        Money.symbol(currency),
                                        Money.symbol(savedIn)
                                    )
                                )
                            },
                            supportingText = {
                                officialRate?.let {
                                    Text(stringResource(R.string.rate_hint_nbrb, rateLabel(it)))
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = convertedText,
                        onValueChange = { convertedText = it.take(12) },
                        label = {
                            Text(stringResource(R.string.savings_in_currency, Money.symbol(savedIn)))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    placeholder = { Text(stringResource(R.string.editor_note_hint)) },
                    singleLine = true,
                    keyboardOptions = TextInput.Sentence,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { deduct = !deduct }
                ) {
                    Checkbox(checked = deduct, onCheckedChange = { deduct = it })
                    Text(
                        text = stringResource(
                            if (withdraw) R.string.savings_return_account
                            else R.string.savings_deduct_account
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (deduct && accounts.size > 1) {
                    AccountChips(accounts, accountId) { accountId = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amountMinor > 0,
                onClick = {
                    val typedRate = rateText.replace(',', '.').toDoubleOrNull()
                    val rateNote = if (ownRate && converting && typedRate != null) {
                        "1 ${Money.symbol(currency)} = ${rateLabel(typedRate)} ${Money.symbol(savedIn)}"
                    } else {
                        null
                    }
                    onConfirm(
                        if (withdraw) -amountMinor else amountMinor,
                        savedIn,
                        listOfNotNull(note.trim().takeIf { it.isNotEmpty() }, rateNote)
                            .joinToString(", "),
                        if (withdraw) null else goalId,
                        deduct,
                        accountId,
                        deductMinor
                    )
                }
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

private fun rateLabel(rate: Double): String = Money.rateText(rate)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSavingDialog(
    saving: SavingEntity,
    goals: List<GoalUi>,
    onConfirm: (
        amountMinor: Long,
        currency: String,
        note: String,
        date: LocalDate,
        goalId: Long?
    ) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var withdraw by remember { mutableStateOf(saving.amountMinor < 0) }
    var amountText by remember {
        mutableStateOf(Money.editableText(abs(saving.amountMinor), saving.currencyCode))
    }
    var currency by remember { mutableStateOf(saving.currencyCode) }
    var note by remember { mutableStateOf(saving.note) }
    var date by remember { mutableStateOf(Dates.toLocalDate(saving.timestamp)) }
    var goalId by remember { mutableStateOf(saving.goalId) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val goal = goals.firstOrNull { it.goal.id == goalId }
    val savedIn = goal?.goal?.currencyCode ?: currency
    val typedMinor = Money.parseToMinor(amountText, savedIn) ?: 0L
    val title = saving.note.ifBlank {
        stringResource(if (withdraw) R.string.savings_withdraw else R.string.savings_deposit)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.saving_edit),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { confirmingDelete = true }) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.editor_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SavingDirectionToggle(withdraw) { withdraw = it }
                AmountField(amountText, { amountText = it })
                if (goal == null) {
                    CurrencyChips(currency) { currency = it }
                } else {
                    Text(
                        text = stringResource(R.string.savings_in_currency, Money.symbol(savedIn)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(
                    onClick = { showDatePicker = true },
                    leadingIcon = {
                        Icon(Icons.Rounded.Event, contentDescription = null, Modifier.size(18.dp))
                    },
                    label = {
                        Text(stringResource(R.string.saving_date) + ": " + relativeDate(date))
                    }
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    placeholder = { Text(stringResource(R.string.editor_note_hint)) },
                    singleLine = true,
                    keyboardOptions = TextInput.Sentence,
                    modifier = Modifier.fillMaxWidth()
                )
                if (goals.isNotEmpty()) {
                    Text(
                        stringResource(R.string.goals_pick),
                        style = MaterialTheme.typography.labelLarge
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = goalId == null,
                                onClick = { goalId = null },
                                label = { Text(stringResource(R.string.goals_none)) }
                            )
                        }
                        items(goals, key = { it.goal.id }) { option ->
                            FilterChip(
                                selected = goalId == option.goal.id,
                                onClick = { goalId = option.goal.id },
                                label = { Text(option.goal.title, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = typedMinor > 0,
                onClick = {
                    onConfirm(
                        if (withdraw) -typedMinor else typedMinor,
                        savedIn,
                        note,
                        date,
                        goalId
                    )
                }
            ) { Text(stringResource(R.string.editor_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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

    if (confirmingDelete) {
        ConfirmDeleteDialog(
            name = title,
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
            onDismiss = { confirmingDelete = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavingDirectionToggle(withdraw: Boolean, onChange: (Boolean) -> Unit) {
    val options = listOf(
        false to stringResource(R.string.savings_deposit),
        true to stringResource(R.string.savings_withdraw)
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = withdraw == option,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label) }
            )
        }
    }
}

@Composable
fun GoalDialog(
    initial: SavingGoalEntity?,
    defaultCurrency: String,
    onConfirm: (title: String, targetMinor: Long, currency: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var amountText by remember {
        mutableStateOf(initial?.let { Money.editableText(it.targetMinor, it.currencyCode) } ?: "")
    }
    var currency by remember { mutableStateOf(initial?.currencyCode ?: defaultCurrency) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val targetMinor = Money.parseToMinor(amountText, currency) ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (initial == null) R.string.goal_new else R.string.goal_edit
                    ),
                    modifier = Modifier.weight(1f)
                )

                if (onDelete != null) {
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.editor_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    placeholder = { Text(stringResource(R.string.goal_title_hint)) },
                    singleLine = true,
                    keyboardOptions = TextInput.Sentence,
                    modifier = Modifier.fillMaxWidth()
                )
                AmountField(amountText, { amountText = it })
                CurrencyChips(currency) { currency = it }

                if (initial != null && currency != initial.currencyCode) {
                    Text(
                        text = stringResource(R.string.goal_currency_converts),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && targetMinor > 0,
                onClick = { onConfirm(title, targetMinor, currency) }
            ) {
                Text(
                    stringResource(
                        if (initial == null) R.string.action_add else R.string.editor_save
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )

    if (confirmingDelete && onDelete != null) {
        ConfirmDeleteDialog(
            name = initial?.title.orEmpty(),
            message = stringResource(R.string.confirm_delete_goal),
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
            onDismiss = { confirmingDelete = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecurringDialog(
    expenseCategories: List<CategoryEntity>,
    incomeCategories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    currencyCode: String,
    onConfirm: (
        title: String,
        amountMinor: Long,
        currency: String,
        categoryId: Long,
        firstDue: LocalDate,
        frequency: RecurringFrequency,
        type: TransactionType,
        accountId: Long?
    ) -> Unit,
    categoryActions: CategoryActions,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(currencyCode) }
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    val income = type == TransactionType.INCOME
    val categories = if (income) incomeCategories else expenseCategories
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var accountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id) }
    var firstDue by remember { mutableStateOf(LocalDate.now()) }
    var frequency by remember { mutableStateOf(RecurringFrequency.MONTHLY) }
    var showDatePicker by remember { mutableStateOf(false) }
    val amountMinor = Money.parseToMinor(amountText, currency) ?: 0L

    val effectiveCategoryId = categoryId?.takeIf { id -> categories.any { it.id == id } }
        ?: categories.firstOrNull()?.id

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(recurringTitleRes(income))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RecurringTypeToggle(type) { type = it }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    placeholder = { Text(stringResource(recurringNameRes(income))) },
                    singleLine = true,
                    keyboardOptions = TextInput.Sentence,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.take(12) },
                    placeholder = { Text(stringResource(recurringAmountRes(income))) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                AssistChip(
                    onClick = { showDatePicker = true },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Event,
                            contentDescription = null,
                            Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            stringResource(recurringDateRes(income)) + ": " +
                                relativeDate(firstDue)
                        )
                    }
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RecurringFrequency.entries) { option ->
                        FilterChip(
                            selected = frequency == option,
                            onClick = { frequency = option },
                            label = { Text(frequencyLabel(option)) }
                        )
                    }
                }
                CurrencyChips(currency) { currency = it }
                CategoryRow(
                    categories = categories,
                    selectedId = effectiveCategoryId,
                    onSelect = { categoryId = it },
                    type = type,
                    actions = categoryActions
                )
                if (accounts.size > 1) {
                    Text(
                        stringResource(R.string.editor_account),
                        style = MaterialTheme.typography.labelLarge
                    )
                    AccountChips(accounts, accountId) { accountId = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && amountMinor > 0 && effectiveCategoryId != null,
                onClick = {
                    onConfirm(
                        title,
                        amountMinor,
                        currency,
                        effectiveCategoryId!!,
                        firstDue,
                        frequency,
                        type,
                        accountId
                    )
                }
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = firstDue
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            firstDue = Instant.ofEpochMilli(millis)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringTypeToggle(
    type: TransactionType,
    onChange: (TransactionType) -> Unit
) {
    val options = listOf(
        TransactionType.EXPENSE to stringResource(R.string.type_expense),
        TransactionType.INCOME to stringResource(R.string.type_income)
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = type == option,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun CategoryRow(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    type: TransactionType,
    actions: CategoryActions
) {
    CategoryPickerRow(
        categories = categories,
        selectedId = selectedId,
        onSelect = onSelect,
        type = type,
        actions = actions,
        badgeSize = 40.dp
    )
}

@StringRes
private fun recurringTitleRes(income: Boolean): Int =
    if (income) R.string.recurring_new_income else R.string.recurring_new

@StringRes
private fun recurringNameRes(income: Boolean): Int =
    if (income) R.string.recurring_source_hint else R.string.recurring_title_hint

@StringRes
private fun recurringAmountRes(income: Boolean): Int =
    if (income) R.string.recurring_amount_income else R.string.recurring_amount_expense

@StringRes
private fun recurringDateRes(income: Boolean): Int =
    if (income) R.string.recurring_first_date_income else R.string.recurring_first_date

@StringRes
private fun recurringConvertedRes(income: Boolean): Int =
    if (income) R.string.recurring_converted_income else R.string.recurring_converted

@StringRes
private fun recurringChargedRes(income: Boolean): Int =
    if (income) R.string.recurring_credited else R.string.recurring_charged

@Composable
private fun AccountChips(
    accounts: List<AccountEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(accounts, key = { it.id }) { account ->
            FilterChip(
                selected = selectedId == account.id,
                onClick = { onSelect(account.id) },
                leadingIcon = {
                    AccountBadge(
                        iconKey = account.iconKey,
                        color = Color(account.colorArgb),
                        iconPath = account.iconPath,
                        size = 18.dp
                    )
                },
                label = {
                    Text(
                        AccountVisuals.displayName(account),
                        maxLines = 1
                    )
                }
            )
        }
    }
}

@Composable
fun AddAccountDialog(
    onConfirm: (name: String, iconKey: String, colorArgb: Long, iconUri: Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var iconKey by remember {
        mutableStateOf(AccountVisuals.pickableIconKeys.first())
    }
    var colorArgb by remember {
        mutableLongStateOf(CategoryVisuals.pickableColors.first())
    }
    val picture = rememberPictureChoice(null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.account_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    placeholder = { Text(stringResource(R.string.category_name_hint)) },
                    singleLine = true,
                    keyboardOptions = TextInput.Sentence,
                    modifier = Modifier.fillMaxWidth()
                )
                PicturePickerRow(picture)
                if (!picture.hasPicture) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(AccountVisuals.pickableIconKeys) { key ->
                            CategoryBadge(
                                iconKey = key,
                                color = Color(colorArgb),
                                selected = key == iconKey,
                                size = 40.dp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { iconKey = key }
                            )
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CategoryVisuals.pickableColors) { color ->
                            Box(
                                modifier = Modifier
                                    .size(if (color == colorArgb) 40.dp else 32.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                                    .clickable { colorArgb = color }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), iconKey, colorArgb, picture.picked) }
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun AccountBalanceDialog(
    account: AccountEntity,
    currentBalanceMinor: Long,
    currencyCode: String,
    deletable: Boolean,
    onSetBalance: (targetMinor: Long) -> Unit,
    onUpdateAppearance: (
        name: String,
        iconKey: String,
        colorArgb: Long,
        renamed: Boolean,
        iconUri: Uri?,
        iconCleared: Boolean
    ) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val resolvedName = AccountVisuals.displayName(account)
    var balanceText by remember {
        mutableStateOf(Money.editableText(currentBalanceMinor, currencyCode))
    }
    var editMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(resolvedName) }
    var iconKey by remember { mutableStateOf(account.iconKey) }
    var colorArgb by remember { mutableLongStateOf(account.colorArgb) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val picture = rememberPictureChoice(account.iconPath)
    val iconRow = rememberRowScrolledTo(
        AccountVisuals.pickableIconKeys.indexOf(account.iconKey)
    )
    val colorRow = rememberRowScrolledTo(
        CategoryVisuals.pickableColors.indexOf(account.colorArgb)
    )
    val target = Money.parseToMinor(balanceText.replace("-", ""), currencyCode)
        ?.let { if (balanceText.startsWith("-")) -it else it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountBadge(
                    iconKey = iconKey,
                    color = Color(colorArgb),
                    iconPath = picture.path,
                    size = 28.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { editMode = true }
                )
                IconButton(onClick = { editMode = !editMode }) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.account_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (deletable && editMode) {
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.editor_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (editMode) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        label = { Text(stringResource(R.string.category_name_hint)) },
                        singleLine = true,
                        keyboardOptions = TextInput.Name,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PicturePickerRow(picture)
                    if (!picture.hasPicture) {
                        LazyRow(
                            state = iconRow,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(AccountVisuals.pickableIconKeys) { key ->
                                CategoryBadge(
                                    iconKey = key,
                                    color = Color(colorArgb),
                                    selected = key == iconKey,
                                    size = 40.dp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { iconKey = key }
                                )
                            }
                        }
                        LazyRow(
                            state = colorRow,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(CategoryVisuals.pickableColors) { color ->
                                Box(
                                    modifier = Modifier
                                        .size(if (color == colorArgb) 40.dp else 32.dp)
                                        .clip(CircleShape)
                                        .background(Color(color))
                                        .clickable { colorArgb = color }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it.take(13) },
                    label = { Text(stringResource(R.string.account_balance)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.account_balance_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = target != null && name.isNotBlank(),
                onClick = {
                    val trimmed = name.trim()
                    val appearanceChanged = trimmed != resolvedName ||
                        iconKey != account.iconKey || colorArgb != account.colorArgb ||
                        picture.picked != null || picture.cleared
                    if (appearanceChanged) {
                        onUpdateAppearance(
                            trimmed,
                            iconKey,
                            colorArgb,
                            trimmed != resolvedName,
                            picture.picked,
                            picture.cleared
                        )
                    }
                    onSetBalance(target!!)
                }
            ) { Text(stringResource(R.string.editor_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )

    if (confirmingDelete) {
        ConfirmDeleteDialog(
            name = resolvedName,
            message = stringResource(R.string.confirm_delete_account),
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
            onDismiss = { confirmingDelete = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecurringDialog(
    initial: RecurringEntity,
    expenseCategories: List<CategoryEntity>,
    incomeCategories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    onConfirm: (
        title: String,
        amountMinor: Long,
        nextDue: LocalDate,
        frequency: RecurringFrequency,
        type: TransactionType,
        categoryId: Long,
        accountId: Long?
    ) -> Unit,
    categoryActions: CategoryActions,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initial.title) }
    var amountText by remember {
        mutableStateOf(Money.editableText(initial.amountMinor, initial.currencyCode))
    }
    var due by remember { mutableStateOf(initial.nextDueDate) }
    var frequency by remember { mutableStateOf(initial.frequency) }
    var type by remember { mutableStateOf(initial.type) }
    var categoryId by remember { mutableStateOf(initial.categoryId) }
    var accountId by remember(accounts) {
        mutableStateOf(initial.accountId ?: accounts.firstOrNull()?.id)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    val amountMinor = Money.parseToMinor(amountText, initial.currencyCode) ?: 0L
    val income = type == TransactionType.INCOME
    val categories = if (income) incomeCategories else expenseCategories
    val effectiveCategoryId = categoryId.takeIf { id -> categories.any { it.id == id } }
        ?: categories.firstOrNull()?.id

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(initial.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RecurringTypeToggle(type) { type = it }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text(stringResource(recurringNameRes(income))) },
                    singleLine = true,
                    keyboardOptions = TextInput.Sentence,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.take(12) },
                    label = { Text(stringResource(recurringAmountRes(income))) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                AssistChip(
                    onClick = { showDatePicker = true },
                    leadingIcon = {
                        Icon(Icons.Rounded.Event, contentDescription = null, Modifier.size(18.dp))
                    },
                    label = {
                        Text(
                            stringResource(recurringDateRes(income)) + ": " +
                                relativeDate(due)
                        )
                    }
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RecurringFrequency.entries) { option ->
                        FilterChip(
                            selected = frequency == option,
                            onClick = { frequency = option },
                            label = { Text(frequencyLabel(option)) }
                        )
                    }
                }
                CategoryRow(
                    categories = categories,
                    selectedId = effectiveCategoryId,
                    onSelect = { categoryId = it },
                    type = type,
                    actions = categoryActions
                )
                if (accounts.size > 1) {
                    Text(
                        stringResource(R.string.editor_account),
                        style = MaterialTheme.typography.labelLarge
                    )
                    AccountChips(accounts, accountId) { accountId = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && amountMinor > 0 && effectiveCategoryId != null,
                onClick = {
                    onConfirm(
                        title,
                        amountMinor,
                        due,
                        frequency,
                        type,
                        effectiveCategoryId!!,
                        accountId
                    )
                }
            ) { Text(stringResource(R.string.editor_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = due
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            due = Instant.ofEpochMilli(millis)
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
fun frequencyLabel(frequency: RecurringFrequency): String = stringResource(
    when (frequency) {
        RecurringFrequency.WEEKLY -> R.string.freq_weekly
        RecurringFrequency.MONTHLY -> R.string.freq_monthly
        RecurringFrequency.QUARTERLY -> R.string.freq_quarterly
        RecurringFrequency.YEARLY -> R.string.freq_yearly
    }
)

@Composable
fun ConfirmRecurringDialog(
    title: String,
    initialAmountMinor: Long,
    currencyCode: String,
    appCurrencyCode: String,
    suggestedRate: Double?,
    accounts: List<AccountEntity>,
    defaultAccountId: Long?,
    type: TransactionType,
    onConfirm: (amountMinor: Long, chargedMinor: Long, accountId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val sameCurrency = currencyCode == appCurrencyCode
    var amountText by remember {
        mutableStateOf(Money.editableText(initialAmountMinor, currencyCode))
    }
    var accountId by remember(accounts, defaultAccountId) {
        mutableStateOf(
            defaultAccountId?.takeIf { id -> accounts.any { it.id == id } }
                ?: accounts.firstOrNull()?.id
        )
    }
    val amountMinor = Money.parseToMinor(amountText, currencyCode) ?: 0L
    val atOfficialRate = suggestedRate
        ?.takeIf { it > 0 }
        ?.let { Math.round(amountMinor * it) }
        ?: 0L

    var chargedText by remember { mutableStateOf("") }
    var chargedByHand by remember { mutableStateOf(false) }
    LaunchedEffect(atOfficialRate, chargedByHand) {
        if (!chargedByHand) {
            chargedText = if (atOfficialRate > 0) {
                Money.editableText(atOfficialRate, appCurrencyCode)
            } else {
                ""
            }
        }
    }

    val chargedMinor = when {
        sameCurrency -> amountMinor
        else -> Money.parseToMinor(chargedText, appCurrencyCode) ?: 0L
    }
    val ownRate = if (amountMinor > 0 && chargedMinor > 0) {
        chargedMinor.toDouble() / amountMinor
    } else {
        0.0
    }
    val income = type == TransactionType.INCOME

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.take(12) },
                    label = {
                        Text(
                            stringResource(recurringAmountRes(income)) + " (${Money.symbol(currencyCode)})"
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(
                        if (income) {
                            R.string.recurring_confirm_income
                        } else {
                            R.string.recurring_confirm_expense
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (accounts.size > 1) {
                    Text(
                        stringResource(R.string.editor_account),
                        style = MaterialTheme.typography.labelLarge
                    )
                    AccountChips(accounts, accountId) { accountId = it }
                }
                if (!sameCurrency) {
                    OutlinedTextField(
                        value = chargedText,
                        onValueChange = {
                            chargedByHand = true
                            chargedText = it.take(12)
                        },
                        label = {
                            Text(
                                stringResource(
                                    recurringChargedRes(income),
                                    appCurrencyCode
                                )
                            )
                        },
                        supportingText = { Text(stringResource(R.string.recurring_charged_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (ownRate > 0) {
                        Text(
                            text = stringResource(
                                R.string.recurring_rate_value,
                                currencyCode,
                                appCurrencyCode,
                                "%.4f".format(ownRate)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amountMinor > 0 && chargedMinor > 0,
                onClick = { onConfirm(amountMinor, chargedMinor, accountId) }
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

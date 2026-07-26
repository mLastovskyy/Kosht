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
import by.mlastovsky.kosht.data.db.AccountEntity
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.data.db.DebtEntity
import by.mlastovsky.kosht.data.db.RecurringEntity
import by.mlastovsky.kosht.data.db.SavingGoalEntity
import by.mlastovsky.kosht.model.DebtDirection
import by.mlastovsky.kosht.model.RecurringFrequency
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.ui.AccountVisuals
import by.mlastovsky.kosht.ui.CategoryVisuals
import by.mlastovsky.kosht.ui.components.CategoryActions
import by.mlastovsky.kosht.ui.components.CategoryBadge
import by.mlastovsky.kosht.ui.components.CategoryPickerRow
import by.mlastovsky.kosht.ui.components.TextInput
import by.mlastovsky.kosht.ui.relativeDate
import by.mlastovsky.kosht.ui.settings.SettingsViewModel
import by.mlastovsky.kosht.util.Money
import java.time.LocalDate

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
private fun CurrencyChips(
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(SettingsViewModel.SUPPORTED_CURRENCIES) { code ->
            FilterChip(
                selected = code == selected,
                onClick = { onSelect(code) },
                label = { Text(code) }
            )
        }
    }
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
    onRepay: (amountMinor: Long, note: String?, accountId: Long?) -> Unit,
    onClose: (note: String?, accountId: Long?) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var repayText by remember { mutableStateOf("") }
    var record by remember { mutableStateOf(true) }
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
            TextButton(onClick = onDelete) {
                Text(
                    stringResource(R.string.editor_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

@Composable
fun AddSavingDialog(
    withdraw: Boolean,
    defaultCurrency: String,
    goals: List<GoalUi>,
    accounts: List<AccountEntity>,
    rateOf: (from: String, to: String) -> Double?,
    onConfirm: (
        amountMinor: Long,
        currency: String,
        note: String,
        goalId: Long?,
        deduct: Boolean,
        accountId: Long?
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
    var convertedText by remember { mutableStateOf("") }

    LaunchedEffect(typedMinor, currency, savedIn) {
        convertedText = if (!converting || typedMinor <= 0) {
            ""
        } else {
            val rate = rateOf(currency, savedIn)
            if (rate == null || rate <= 0.0) {
                ""
            } else {
                Money.editableText(Math.round(typedMinor * rate), savedIn)
            }
        }
    }
    val amountMinor = if (converting) {
        Money.parseToMinor(convertedText, savedIn) ?: 0L
    } else {
        typedMinor
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
                    OutlinedTextField(
                        value = convertedText,
                        onValueChange = { convertedText = it.take(12) },
                        label = { Text(stringResource(R.string.savings_in_currency, savedIn)) },
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
                    onConfirm(
                        if (withdraw) -amountMinor else amountMinor,
                        savedIn,
                        note,
                        if (withdraw) null else goalId,
                        deduct,
                        accountId
                    )
                }
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
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
                    IconButton(onClick = onDelete) {
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
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            firstDue = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneOffset.UTC).toLocalDate()
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
                    Icon(
                        CategoryVisuals.icon(account.iconKey),
                        contentDescription = null,
                        tint = Color(account.colorArgb),
                        modifier = Modifier.size(18.dp)
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
    onConfirm: (name: String, iconKey: String, colorArgb: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var iconKey by remember {
        mutableStateOf(AccountVisuals.pickableIconKeys.first())
    }
    var colorArgb by remember {
        mutableLongStateOf(CategoryVisuals.pickableColors.first())
    }

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
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim(), iconKey, colorArgb) }
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
    onUpdateAppearance: (name: String, iconKey: String, colorArgb: Long, renamed: Boolean) -> Unit,
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
    val target = Money.parseToMinor(balanceText.replace("-", ""), currencyCode)
        ?.let { if (balanceText.startsWith("-")) -it else it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    CategoryVisuals.icon(iconKey),
                    contentDescription = null,
                    tint = Color(colorArgb)
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
                    IconButton(onClick = onDelete) {
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
                        iconKey != account.iconKey || colorArgb != account.colorArgb
                    if (appearanceChanged) {
                        onUpdateAppearance(trimmed, iconKey, colorArgb, trimmed != resolvedName)
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
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            due = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneOffset.UTC).toLocalDate()
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
    onConfirm: (amountMinor: Long, rate: Double, accountId: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val sameCurrency = currencyCode == appCurrencyCode
    var amountText by remember {
        mutableStateOf(Money.editableText(initialAmountMinor, currencyCode))
    }
    var rateText by remember {
        mutableStateOf(suggestedRate?.let { "%.4f".format(it).replace(',', '.') } ?: "")
    }
    var accountId by remember(accounts, defaultAccountId) {
        mutableStateOf(
            defaultAccountId?.takeIf { id -> accounts.any { it.id == id } }
                ?: accounts.firstOrNull()?.id
        )
    }
    val amountMinor = Money.parseToMinor(amountText, currencyCode) ?: 0L
    val rate = if (sameCurrency) 1.0 else rateText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val convertedMinor = if (rate > 0) Math.round(amountMinor * rate) else 0L
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
                            stringResource(recurringAmountRes(income)) + " ($currencyCode)"
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
                        value = rateText,
                        onValueChange = { rateText = it.take(10) },
                        label = {
                            Text(
                                stringResource(
                                    R.string.recurring_rate,
                                    currencyCode,
                                    appCurrencyCode
                                )
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (convertedMinor > 0) {
                    Text(
                        text = stringResource(
                            recurringConvertedRes(income),
                            Money.format(convertedMinor, appCurrencyCode)
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amountMinor > 0 && rate > 0,
                onClick = { onConfirm(amountMinor, rate, accountId) }
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

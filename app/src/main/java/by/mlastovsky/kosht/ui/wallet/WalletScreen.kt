package by.mlastovsky.kosht.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.data.RatesRepository
import by.mlastovsky.kosht.data.db.DebtEntity
import by.mlastovsky.kosht.data.db.RateEntity
import by.mlastovsky.kosht.data.db.RecurringWithCategory
import by.mlastovsky.kosht.data.db.SavingEntity
import by.mlastovsky.kosht.model.DebtDirection
import by.mlastovsky.kosht.ui.AppViewModelProvider
import by.mlastovsky.kosht.ui.components.AnimatedAmountText
import by.mlastovsky.kosht.ui.components.CategoryBadge
import by.mlastovsky.kosht.ui.relativeDate
import by.mlastovsky.kosht.ui.theme.KoshtTheme
import by.mlastovsky.kosht.util.Dates
import by.mlastovsky.kosht.util.Money
import androidx.compose.ui.graphics.Color

@Composable
fun WalletScreen(
    viewModel: WalletViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDebt by remember { mutableStateOf(false) }
    var showAddRecurring by remember { mutableStateOf(false) }
    var savingDialogWithdraw by remember { mutableStateOf<Boolean?>(null) }
    var debtInAction by remember { mutableStateOf<DebtEntity?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item(key = "header") {
            Text(
                text = stringResource(R.string.nav_wallet),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        item(key = "rates") {
            RatesCard(
                rates = state.rates,
                updatedAt = state.ratesUpdatedAt,
                refreshing = state.refreshingRates,
                onRefresh = viewModel::refreshRates
            )
        }

        // --- Due recurring charges ---
        val due = state.recurring.filter { it.recurring.id in state.dueRecurringIds }
        if (due.isNotEmpty()) {
            item(key = "due-header") {
                SectionHeader(stringResource(R.string.section_due))
            }
            items(due, key = { "due-${it.recurring.id}" }) { item ->
                DueCard(
                    item = item,
                    currencyCode = state.currencyCode,
                    onConfirm = { viewModel.confirmRecurring(item) },
                    modifier = Modifier.animateItem()
                )
            }
        }

        // --- Recurring charges ---
        item(key = "recurring-header") {
            SectionHeaderRow(
                title = stringResource(R.string.section_recurring),
                onAdd = { showAddRecurring = true }
            )
        }
        if (state.loaded && state.recurring.isEmpty()) {
            item(key = "recurring-empty") {
                EmptyHint(stringResource(R.string.wallet_empty_recurring))
            }
        }
        items(state.recurring, key = { "rec-${it.recurring.id}" }) { item ->
            RecurringRow(
                item = item,
                currencyCode = state.currencyCode,
                onToggle = { enabled -> viewModel.setRecurringEnabled(item, enabled) },
                onDelete = { viewModel.deleteRecurring(item) },
                modifier = Modifier.animateItem()
            )
        }

        // --- Debts ---
        item(key = "debts-header") {
            SectionHeaderRow(
                title = stringResource(R.string.section_debts),
                onAdd = { showAddDebt = true }
            )
        }
        item(key = "debts-totals") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DebtTotalChip(
                    label = stringResource(R.string.debt_i_owe),
                    amountText = "≈ " + Money.format(state.iOweBynMinor, "BYN"),
                    color = KoshtTheme.colors.expense,
                    modifier = Modifier.weight(1f)
                )
                DebtTotalChip(
                    label = stringResource(R.string.debt_owed_to_me),
                    amountText = "≈ " + Money.format(state.owedToMeBynMinor, "BYN"),
                    color = KoshtTheme.colors.income,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (state.loaded && state.debts.isEmpty()) {
            item(key = "debts-empty") {
                EmptyHint(stringResource(R.string.wallet_empty_debts))
            }
        }
        items(state.debts, key = { "debt-${it.id}" }) { debt ->
            DebtRow(
                debt = debt,
                rates = state.rates,
                onClick = { debtInAction = debt },
                modifier = Modifier.animateItem()
            )
        }

        // --- Savings ---
        item(key = "savings-header") {
            SectionHeaderRow(
                title = stringResource(R.string.section_savings),
                onAdd = null
            )
        }
        item(key = "savings-total") {
            SavingsSummary(
                totalBynText = "≈ " + Money.format(state.savingsBynMinor, "BYN"),
                perCurrency = state.savingTotals.map {
                    Money.format(it.total, it.currencyCode)
                },
                onDeposit = { savingDialogWithdraw = false },
                onWithdraw = { savingDialogWithdraw = true }
            )
        }
        if (state.loaded && state.savings.isEmpty()) {
            item(key = "savings-empty") {
                EmptyHint(stringResource(R.string.wallet_empty_savings))
            }
        }
        items(state.savings, key = { "sav-${it.id}" }) { saving ->
            SavingRow(
                saving = saving,
                rates = state.rates,
                onDelete = { viewModel.deleteSaving(saving) },
                modifier = Modifier.animateItem()
            )
        }
    }

    if (showAddDebt) {
        AddDebtDialog(
            defaultCurrency = state.currencyCode,
            onConfirm = { name, direction, amount, currency, note ->
                viewModel.addDebt(name, direction, amount, currency, note)
                showAddDebt = false
            },
            onDismiss = { showAddDebt = false }
        )
    }

    debtInAction?.let { debt ->
        DebtActionsDialog(
            debt = debt,
            onRepay = { amount ->
                viewModel.repayDebt(debt, amount)
                debtInAction = null
            },
            onClose = {
                viewModel.closeDebt(debt)
                debtInAction = null
            },
            onDelete = {
                viewModel.deleteDebt(debt)
                debtInAction = null
            },
            onDismiss = { debtInAction = null }
        )
    }

    savingDialogWithdraw?.let { withdraw ->
        AddSavingDialog(
            withdraw = withdraw,
            defaultCurrency = state.currencyCode,
            onConfirm = { amount, currency, note ->
                viewModel.addSaving(amount, currency, note)
                savingDialogWithdraw = null
            },
            onDismiss = { savingDialogWithdraw = null }
        )
    }

    if (showAddRecurring) {
        AddRecurringDialog(
            categories = state.expenseCategories,
            currencyCode = state.currencyCode,
            onConfirm = { title, amount, categoryId, day ->
                viewModel.addRecurring(title, amount, categoryId, day)
                showAddRecurring = false
            },
            onDismiss = { showAddRecurring = false }
        )
    }
}

@Composable
private fun RatesCard(
    rates: Map<String, RateEntity>,
    updatedAt: Long?,
    refreshing: Boolean,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.wallet_rates),
                    style = MaterialTheme.typography.titleSmall
                )
                if (updatedAt != null) {
                    Text(
                        text = stringResource(
                            R.string.rates_updated,
                            java.time.Instant.ofEpochMilli(updatedAt)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(
                                    java.time.format.DateTimeFormatter
                                        .ofPattern("d MMMM, HH:mm")
                                )
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (refreshing) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp)
                )
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.rates_refresh),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("USD", "EUR", "RUB", "PLN").forEach { code ->
                val rate = rates[code] ?: return@forEach
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = if (rate.scale != 1) "${rate.scale} $code" else code,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%.2f".format(rate.rate),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SectionHeaderRow(title: String, onAdd: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        if (onAdd != null) {
            IconButton(onClick = onAdd) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.action_add),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun DueCard(
    item: RecurringWithCategory,
    currencyCode: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CategoryBadge(
                iconKey = item.category.iconKey,
                color = Color(item.category.colorArgb),
                size = 40.dp
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.recurring.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = Money.format(item.recurring.amountMinor, currencyCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.action_confirm))
            }
        }
    }
}

@Composable
private fun RecurringRow(
    item: RecurringWithCategory,
    currencyCode: String,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryBadge(
            iconKey = item.category.iconKey,
            color = Color(item.category.colorArgb),
            size = 40.dp
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = item.recurring.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.recurring_day_format, item.recurring.dayOfMonth) +
                    " · " + Money.format(item.recurring.amountMinor, currencyCode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.editor_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = item.recurring.enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun DebtTotalChip(
    label: String,
    amountText: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AnimatedAmountText(
            text = amountText,
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
    }
}

@Composable
private fun DebtRow(
    debt: DebtEntity,
    rates: Map<String, RateEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bynEquivalent = if (debt.currencyCode != "BYN") {
        RatesRepository.toBynMinor(debt.amountMinor, debt.currencyCode, rates)
    } else {
        null
    }
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = debt.personName.trim().take(2).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = debt.personName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(
                    if (debt.direction == DebtDirection.I_OWE) {
                        R.string.debt_i_owe
                    } else {
                        R.string.debt_owed_to_me
                    }
                ) + debt.note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Money.format(debt.amountMinor, debt.currencyCode),
                style = MaterialTheme.typography.titleMedium,
                color = if (debt.direction == DebtDirection.I_OWE) {
                    KoshtTheme.colors.expense
                } else {
                    KoshtTheme.colors.income
                }
            )
            if (bynEquivalent != null) {
                Text(
                    text = "≈ " + Money.format(bynEquivalent, "BYN"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SavingsSummary(
    totalBynText: String,
    perCurrency: List<String>,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.savings_total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AnimatedAmountText(
            text = totalBynText,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        if (perCurrency.size > 1) {
            Text(
                text = perCurrency.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onDeposit, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
                Text(
                    stringResource(R.string.savings_deposit),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            OutlinedButton(onClick = onWithdraw, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Remove, contentDescription = null, Modifier.size(18.dp))
                Text(
                    stringResource(R.string.savings_withdraw),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SavingRow(
    saving: SavingEntity,
    rates: Map<String, RateEntity>,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val positive = saving.amountMinor >= 0
    val bynEquivalent = if (saving.currencyCode != "BYN") {
        RatesRepository.toBynMinor(saving.amountMinor, saving.currencyCode, rates)
    } else {
        null
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (positive) {
                        KoshtTheme.colors.incomeContainer
                    } else {
                        KoshtTheme.colors.expenseContainer
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (positive) Icons.Rounded.Add else Icons.Rounded.Remove,
                contentDescription = null,
                tint = if (positive) KoshtTheme.colors.income else KoshtTheme.colors.expense,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = saving.note.ifBlank {
                    stringResource(
                        if (positive) R.string.savings_deposit else R.string.savings_withdraw
                    )
                },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = relativeDate(Dates.toLocalDate(saving.timestamp)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = (if (positive) "+" else "") +
                    Money.format(saving.amountMinor, saving.currencyCode),
                style = MaterialTheme.typography.titleSmall,
                color = if (positive) KoshtTheme.colors.income else KoshtTheme.colors.expense
            )
            if (bynEquivalent != null) {
                Text(
                    text = "≈ " + Money.format(bynEquivalent, "BYN"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.editor_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

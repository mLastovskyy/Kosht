package by.mlastovsky.kosht.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.data.RatesRepository
import by.mlastovsky.kosht.data.db.AccountEntity
import by.mlastovsky.kosht.data.db.DebtEntity
import by.mlastovsky.kosht.data.db.RateEntity
import by.mlastovsky.kosht.data.db.RecurringWithCategory
import by.mlastovsky.kosht.data.db.SavingEntity
import by.mlastovsky.kosht.model.DebtDirection
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.ui.AccountVisuals
import by.mlastovsky.kosht.ui.AppViewModelProvider
import by.mlastovsky.kosht.ui.components.AnimatedAmountText
import by.mlastovsky.kosht.ui.components.rememberReorderList
import by.mlastovsky.kosht.ui.components.CategoryActions
import by.mlastovsky.kosht.ui.components.CategoryBadge
import by.mlastovsky.kosht.ui.components.ConfirmDeleteDialog
import by.mlastovsky.kosht.ui.relativeDate
import by.mlastovsky.kosht.ui.theme.KoshtTheme
import by.mlastovsky.kosht.ui.transfer.TransferDialog
import by.mlastovsky.kosht.util.Dates
import by.mlastovsky.kosht.util.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WalletScreen(
    viewModel: WalletViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDebt by remember { mutableStateOf(false) }
    var showAddRecurring by remember { mutableStateOf(false) }
    val categoryActions = remember(viewModel) {
        CategoryActions(
            add = viewModel::addCategory,
            reorder = viewModel::reorderCategories,
            update = viewModel::updateCategory,
            delete = viewModel::deleteCategory
        )
    }
    var savingDialogWithdraw by remember { mutableStateOf<Boolean?>(null) }
    var debtInAction by remember { mutableStateOf<DebtEntity?>(null) }
    var debtToEdit by remember { mutableStateOf<DebtEntity?>(null) }
    var goalInAction by remember { mutableStateOf<GoalUi?>(null) }
    var recurringToConfirm by remember { mutableStateOf<RecurringWithCategory?>(null) }
    var recurringToSkip by remember { mutableStateOf<RecurringWithCategory?>(null) }
    var recurringToEdit by remember { mutableStateOf<RecurringWithCategory?>(null) }
    var recurringToDelete by remember { mutableStateOf<RecurringWithCategory?>(null) }
    var savingToEdit by remember { mutableStateOf<SavingEntity?>(null) }
    var showAddGoal by remember { mutableStateOf(false) }
    var showAddAccount by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    var accountInAction by remember {
        mutableStateOf<Pair<AccountEntity, Long>?>(null)
    }

    val context = LocalContext.current
    val offlineMessage = stringResource(R.string.rates_offline)
    LaunchedEffect(Unit) {
        viewModel.rateRefreshFailed.collect {
            android.widget.Toast
                .makeText(context, offlineMessage, android.widget.Toast.LENGTH_LONG)
                .show()
        }
    }

    val listState = rememberLazyListState()
    val accountOrder = rememberReorderList(listState) { key ->
        (key as? String)?.removePrefix(ACCOUNT_KEY)?.takeIf { it != key }?.toLongOrNull()
    }
    val shownAccounts = remember(
        state.accountsWithBalances,
        accountOrder.held,
        accountOrder.offset
    ) {
        accountOrder.arrange(state.accountsWithBalances) { it.first.id }
    }
    LaunchedEffect(state.accountsWithBalances, accountOrder.held) {
        accountOrder.forget(state.accountsWithBalances.map { it.first.id })
    }
    LaunchedEffect(accountOrder.held) { accountOrder.followEdges() }

    LazyColumn(
        state = listState,
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
        if (state.showRates) {
            item(key = "rates") {
                RatesCard(
                    rates = state.rates,
                    updatedAt = state.ratesUpdatedAt,
                    refreshing = state.refreshingRates,
                    onRefresh = viewModel::refreshRates
                )
            }
        }

        item(key = "accounts-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp)
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_accounts),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (state.multiAccount) {

                    if (state.accountsWithBalances.size > 1) {
                        IconButton(onClick = { showTransfer = true }) {
                            Icon(
                                Icons.Rounded.SwapHoriz,
                                contentDescription = stringResource(R.string.transfer_new),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = { showAddAccount = true }) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.action_add),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Switch(
                    checked = state.multiAccount,
                    onCheckedChange = viewModel::setMultiAccount
                )
            }
        }
        if (state.multiAccount) {
            items(
                shownAccounts,
                key = { ACCOUNT_KEY + it.first.id }
            ) { (account, balance) ->
                val held = accountOrder.held == account.id
                val ids = shownAccounts.map { it.first.id }
                Row(
                    modifier = Modifier
                        .zIndex(if (held) 1f else 0f)
                        .then(
                            if (held) {
                                Modifier
                            } else {
                                Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                            }
                        )
                        .graphicsLayer {
                            if (!held) return@graphicsLayer
                            translationY = accountOrder.offset
                            scaleX = HELD_SCALE
                            scaleY = HELD_SCALE
                        }
                        .background(MaterialTheme.colorScheme.background)
                        .clickable { accountInAction = account to balance }
                        .pointerInput(account.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { accountOrder.start(account.id, ids) },
                                onDrag = { change, amount ->
                                    change.consume()
                                    accountOrder.drag(amount.y, kotlin.math.abs(amount.y))
                                },
                                onDragEnd = {
                                    accountOrder.release()?.let(viewModel::reorderAccounts)
                                },
                                onDragCancel = accountOrder::cancel
                            )
                        }
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CategoryBadge(
                        iconKey = account.iconKey,
                        color = Color(account.colorArgb),
                        size = 40.dp,
                        iconPath = account.iconPath
                    )
                    Text(
                        text = AccountVisuals.displayName(account),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = Money.format(balance, state.currencyCode),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                }
            }
        }

        val due = state.recurring.filter { it.recurring.id in state.dueRecurringIds }
        if (due.isNotEmpty()) {
            item(key = "due-header") {
                SectionHeader(stringResource(R.string.section_due))
            }
            items(due, key = { "due-${it.recurring.id}" }) { item ->
                DueCard(
                    item = item,
                    onConfirm = { recurringToConfirm = item },
                    onSkip = { recurringToSkip = item },
                    modifier = Modifier.animateItem()
                )
            }
        }

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
                onToggle = { enabled -> viewModel.setRecurringEnabled(item, enabled) },
                onDelete = { recurringToDelete = item },
                onClick = { recurringToEdit = item },
                modifier = Modifier.animateItem()
            )
        }

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

        item(key = "goals-header") {
            SectionHeaderRow(
                title = stringResource(R.string.goals_title),
                onAdd = { showAddGoal = true }
            )
        }
        if (state.loaded && state.goals.isEmpty()) {
            item(key = "goals-empty") {
                EmptyHint(stringResource(R.string.goals_empty))
            }
        }
        items(state.goals, key = { "goal-${it.goal.id}" }) { goalUi ->
            GoalCard(
                goalUi = goalUi,
                onClick = { goalInAction = goalUi },
                modifier = Modifier.animateItem()
            )
        }

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
                onClick = { savingToEdit = saving },
                modifier = Modifier.animateItem()
            )
        }
    }

    if (showAddDebt) {
        DebtDialog(
            initial = null,
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
            accounts = state.pickableAccounts,
            bornAsRecord = debt.id in state.debtsBornAsRecord,
            onRepay = { amount, note, accountId ->
                viewModel.repayDebt(debt, amount, note, accountId)
                debtInAction = null
            },
            onClose = { note, accountId ->
                viewModel.closeDebt(debt, note, accountId)
                debtInAction = null
            },
            onEdit = {
                debtInAction = null
                debtToEdit = debt
            },
            onDelete = {
                viewModel.deleteDebt(debt)
                debtInAction = null
            },
            onDismiss = { debtInAction = null }
        )
    }

    debtToEdit?.let { debt ->
        DebtDialog(
            initial = debt,
            defaultCurrency = state.currencyCode,
            onConfirm = { name, direction, amount, currency, note ->
                viewModel.updateDebt(debt, name, direction, amount, currency, note)
                debtToEdit = null
            },
            onDismiss = { debtToEdit = null }
        )
    }

    savingDialogWithdraw?.let { withdraw ->

        val savingsNote = stringResource(
            if (withdraw) R.string.savings_withdraw else R.string.savings_deposit
        )
        AddSavingDialog(
            withdraw = withdraw,
            defaultCurrency = state.currencyCode,
            goals = state.goals.filter { !it.achieved },
            accounts = state.pickableAccounts,
            rateOf = viewModel::suggestedRate,
            ownRate = state.ownRate,
            onConfirm = { amount, currency, note, goalId, deduct, accountId, deductMinor ->
                viewModel.addSaving(
                    amountMinor = amount,
                    currencyCode = currency,
                    note = note,
                    goalId = goalId,

                    deductNote = if (deduct) note.ifBlank { savingsNote } else null,
                    accountId = accountId,
                    deductMinor = deductMinor
                )
                savingDialogWithdraw = null
            },
            onDismiss = { savingDialogWithdraw = null }
        )
    }

    if (showAddAccount) {
        AddAccountDialog(
            onConfirm = { name, iconKey, color, iconUri ->
                viewModel.addAccount(name, iconKey, color, iconUri)
                showAddAccount = false
            },
            onDismiss = { showAddAccount = false }
        )
    }

    accountInAction?.let { (account, balance) ->
        AccountBalanceDialog(
            account = account,
            currentBalanceMinor = balance,
            currencyCode = state.currencyCode,
            deletable = state.accountsWithBalances.size > 1,
            onSetBalance = { target ->
                viewModel.setAccountBalance(account, target)
                accountInAction = null
            },
            onUpdateAppearance = { name, iconKey, colorArgb, renamed, iconUri, iconCleared ->
                viewModel.updateAccountAppearance(
                    account,
                    name,
                    iconKey,
                    colorArgb,
                    renamed,
                    iconUri,
                    iconCleared
                )
            },
            onDelete = {
                viewModel.deleteAccount(account)
                accountInAction = null
            },
            onDismiss = { accountInAction = null }
        )
    }

    if (showAddGoal) {
        GoalDialog(
            initial = null,
            defaultCurrency = state.currencyCode,
            onConfirm = { title, target, currency ->
                viewModel.addGoal(title, target, currency)
                showAddGoal = false
            },
            onDelete = null,
            onDismiss = { showAddGoal = false }
        )
    }

    goalInAction?.let { goalUi ->
        GoalDialog(
            initial = goalUi.goal,
            defaultCurrency = state.currencyCode,
            onConfirm = { title, target, currency ->
                viewModel.updateGoal(goalUi, title, target, currency)
                goalInAction = null
            },
            onDelete = {
                viewModel.deleteGoal(goalUi)
                goalInAction = null
            },
            onDismiss = { goalInAction = null }
        )
    }

    if (showTransfer) {
        TransferDialog(
            onDismiss = { showTransfer = false }
        )
    }

    if (showAddRecurring) {
        AddRecurringDialog(
            expenseCategories = state.expenseCategories,
            incomeCategories = state.incomeCategories,
            accounts = state.pickableAccounts,
            currencyCode = state.currencyCode,
            onConfirm = { title, amount, currency, categoryId, firstDue, frequency, type, account ->
                viewModel.addRecurring(
                    title, amount, currency, categoryId, firstDue, frequency, type, account
                )
                showAddRecurring = false
            },
            categoryActions = categoryActions,
            onDismiss = { showAddRecurring = false }
        )
    }

    recurringToEdit?.let { item ->
        EditRecurringDialog(
            initial = item.recurring,
            expenseCategories = state.expenseCategories,
            incomeCategories = state.incomeCategories,
            accounts = state.pickableAccounts,
            onConfirm = { title, amount, due, freq, type, categoryId, account ->
                viewModel.updateRecurringDetails(
                    item, title, amount, due, freq, type, categoryId, account
                )
                recurringToEdit = null
            },
            categoryActions = categoryActions,
            onDismiss = { recurringToEdit = null }
        )
    }

    recurringToDelete?.let { item ->
        ConfirmDeleteDialog(
            name = item.recurring.title,
            onConfirm = {
                viewModel.deleteRecurring(item)
                recurringToDelete = null
            },
            onDismiss = { recurringToDelete = null }
        )
    }

    savingToEdit?.let { saving ->
        EditSavingDialog(
            saving = saving,
            goals = state.goals,
            onConfirm = { amount, currency, note, date, goalId ->
                viewModel.updateSaving(saving, amount, currency, note, date, goalId)
                savingToEdit = null
            },
            onDelete = {
                viewModel.deleteSaving(saving)
                savingToEdit = null
            },
            onDismiss = { savingToEdit = null }
        )
    }

    recurringToConfirm?.let { item ->
        ConfirmRecurringDialog(
            title = item.recurring.title,
            initialAmountMinor = item.recurring.amountMinor,
            currencyCode = item.recurring.currencyCode,
            appCurrencyCode = state.currencyCode,
            suggestedRate = viewModel.suggestedRate(
                from = item.recurring.currencyCode,
                to = state.currencyCode
            ),

            accounts = state.pickableAccounts,
            defaultAccountId = item.recurring.accountId,
            type = item.recurring.type,
            onConfirm = { amountMinor, chargedMinor, accountId ->
                viewModel.confirmRecurring(item, amountMinor, chargedMinor, accountId)
                recurringToConfirm = null
            },
            onDismiss = { recurringToConfirm = null }
        )
    }

    recurringToSkip?.let { item ->
        AlertDialog(
            onDismissRequest = { recurringToSkip = null },
            icon = { Icon(Icons.Rounded.SkipNext, contentDescription = null) },
            title = { Text(stringResource(R.string.recurring_skip)) },
            text = {
                Text(
                    stringResource(
                        R.string.recurring_skip_text,
                        item.recurring.title,
                        nextDueLabel(item)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.skipRecurring(item)
                        recurringToSkip = null
                    }
                ) { Text(stringResource(R.string.recurring_skip)) }
            },
            dismissButton = {
                TextButton(onClick = { recurringToSkip = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private fun nextDueLabel(item: RecurringWithCategory): String =
    LocalDate.ofEpochDay(item.recurring.advanced().nextDueEpochDay)
        .format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))

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

private const val ACCOUNT_KEY = "acc-"

private const val HELD_SCALE = 1.03f

private const val PAUSED_ALPHA = 0.45f

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

private fun signedAmount(item: RecurringWithCategory): String {
    val sign = if (item.recurring.type == TransactionType.INCOME) {
        "+"
    } else {
        "−"
    }
    return sign + Money.format(item.recurring.amountMinor, item.recurring.currencyCode)
}

@Composable
private fun DueCard(
    item: RecurringWithCategory,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
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
                size = 40.dp,
                iconPath = item.category.iconPath
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
                    text = signedAmount(item),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.recurring_skip), maxLines = 1)
            }
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.action_confirm), maxLines = 1)
            }
        }
    }
}

@Composable
private fun RecurringRow(
    item: RecurringWithCategory,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fade = if (item.recurring.enabled) 1f else PAUSED_ALPHA
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryBadge(
            iconKey = item.category.iconKey,
            color = Color(item.category.colorArgb),
            size = 40.dp,
            iconPath = item.category.iconPath,
            modifier = Modifier.alpha(fade)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = item.recurring.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = fade),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = relativeDate(item.recurring.nextDueDate) +
                    " · " + frequencyLabel(item.recurring.frequency) +
                    " · " + signedAmount(item),
                style = MaterialTheme.typography.bodyMedium,
                color = if (item.recurring.type == TransactionType.INCOME) {
                    KoshtTheme.colors.income.copy(alpha = fade)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = fade)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
private fun GoalCard(
    goalUi: GoalUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val goal = goalUi.goal
    val accent = if (goalUi.achieved) KoshtTheme.colors.income else MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)

            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (goalUi.achieved) {
                Icon(
                    Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(22.dp)
                )
            }
            Text(
                text = goal.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = Money.format(goalUi.savedMinor, goal.currencyCode) + " / " +
                    Money.format(goal.targetMinor, goal.currencyCode),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                maxLines = 1
            )
        }
        LinearProgressIndicator(
            progress = { goalUi.progress },
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
        if (goalUi.achieved) {
            Text(
                text = stringResource(R.string.goal_achieved),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                modifier = Modifier.padding(top = 6.dp)
            )
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    onClick: () -> Unit,
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
            .clickable(onClick = onClick)
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
    }
}

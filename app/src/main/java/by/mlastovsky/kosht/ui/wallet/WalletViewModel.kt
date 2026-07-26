package by.mlastovsky.kosht.ui.wallet

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.AccountRepository
import by.mlastovsky.kosht.data.AppSettings
import by.mlastovsky.kosht.data.CategorySeed
import by.mlastovsky.kosht.data.LedgerEntry
import by.mlastovsky.kosht.data.RatesRepository
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.WalletRepository
import by.mlastovsky.kosht.data.db.AccountEntity
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.data.db.DebtEntity
import by.mlastovsky.kosht.data.db.RateEntity
import by.mlastovsky.kosht.data.db.RecurringWithCategory
import by.mlastovsky.kosht.data.db.SavingEntity
import by.mlastovsky.kosht.data.db.SavingGoalEntity
import by.mlastovsky.kosht.data.db.SavingTotal
import by.mlastovsky.kosht.model.DebtDirection
import by.mlastovsky.kosht.model.RecurringFrequency
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.ui.components.CategoryEdit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalUi(
    val goal: SavingGoalEntity,
    val savedMinor: Long
) {
    val progress: Float
        get() = if (goal.targetMinor > 0) {
            (savedMinor.toFloat() / goal.targetMinor).coerceIn(0f, 1f)
        } else {
            0f
        }

    val achieved: Boolean
        get() = goal.achievedAt != null || savedMinor >= goal.targetMinor
}

data class WalletUiState(
    val loaded: Boolean = false,
    val currencyCode: String = SettingsRepository.DEFAULT_CURRENCY,
    val rates: Map<String, RateEntity> = emptyMap(),
    val ratesUpdatedAt: Long? = null,
    val refreshingRates: Boolean = false,
    val showRates: Boolean = true,
    val debts: List<DebtEntity> = emptyList(),
    val iOweBynMinor: Long = 0,
    val owedToMeBynMinor: Long = 0,
    val savings: List<SavingEntity> = emptyList(),
    val savingTotals: List<SavingTotal> = emptyList(),
    val savingsBynMinor: Long = 0,
    val goals: List<GoalUi> = emptyList(),
    val recurring: List<RecurringWithCategory> = emptyList(),
    val dueRecurringIds: Set<Long> = emptySet(),
    val expenseCategories: List<CategoryEntity> = emptyList(),

    val incomeCategories: List<CategoryEntity> = emptyList(),
    val multiAccount: Boolean = false,

    val accountsWithBalances: List<Pair<AccountEntity, Long>> =
        emptyList()
) {

    val pickableAccounts: List<AccountEntity>
        get() = if (multiAccount) accountsWithBalances.map { it.first } else emptyList()
}

class WalletViewModel(
    private val walletRepository: WalletRepository,
    private val transactionRepository: TransactionRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val refreshingRates = MutableStateFlow(false)

    private val _rateRefreshFailed = MutableSharedFlow<Unit>()

    val rateRefreshFailed: SharedFlow<Unit> =
        _rateRefreshFailed.asSharedFlow()

    init {
        viewModelScope.launch { ratesRepository.refreshIfStale() }
    }

    fun refreshRates() {
        if (refreshingRates.value) return
        viewModelScope.launch {
            refreshingRates.value = true
            val result = runCatching { ratesRepository.refresh() }
            refreshingRates.value = false
            if (result.isFailure) _rateRefreshFailed.emit(Unit)
        }
    }

    private val goals = combine(
        walletRepository.observeGoals(),
        walletRepository.observeGoalProgress()
    ) { goals, progress ->
        goals.map { goal ->
            GoalUi(
                goal = goal,
                savedMinor = progress.firstOrNull { it.goalId == goal.id }?.total ?: 0L
            )
        }
    }

    private val wallet = combine(
        walletRepository.observeDebts(),
        walletRepository.observeSavings(SAVINGS_LIMIT),
        walletRepository.observeSavingTotals(),
        walletRepository.observeRecurring(),
        goals,
        ::WalletData
    )

    private data class ContextData(
        val rates: Map<String, RateEntity>,
        val settings: AppSettings,
        val categories: Pair<List<CategoryEntity>, List<CategoryEntity>>,
        val refreshing: Boolean,
        val accountsWithBalances: List<Pair<AccountEntity, Long>>
    )

    private val categories = combine(
        transactionRepository.observeCategories(TransactionType.EXPENSE),
        transactionRepository.observeCategories(TransactionType.INCOME),
        ::Pair
    )

    private val accountsWithBalances = combine(
        accountRepository.observeAccounts(),
        accountRepository.observeBalances()
    ) { accounts, balances ->
        val primaryId = accounts.firstOrNull()?.id
        accounts.map { account ->
            var sum = (balances.firstOrNull { it.accountId == account.id }?.balance ?: 0L) +
                account.adjustmentMinor
            if (account.id == primaryId) {
                sum += balances.firstOrNull { it.accountId == null }?.balance ?: 0L
            }
            account to sum
        }
    }

    private val context = combine(
        ratesRepository.rates,
        settingsRepository.settings,
        categories,
        refreshingRates,
        accountsWithBalances,
        ::ContextData
    )

    private data class WalletData(
        val debts: List<DebtEntity>,
        val savings: List<SavingEntity>,
        val savingTotals: List<SavingTotal>,
        val recurring: List<RecurringWithCategory>,
        val goals: List<GoalUi>
    )

    val uiState: StateFlow<WalletUiState> = combine(wallet, context) { data,
        (rates, settings, categories, refreshing, accountsWithBalances) ->
        WalletUiState(
            multiAccount = settings.multiAccount,
            accountsWithBalances = accountsWithBalances,
            loaded = true,
            currencyCode = settings.currencyCode,
            rates = rates,
            ratesUpdatedAt = rates.values.maxOfOrNull { it.updatedAt },
            refreshingRates = refreshing,
            showRates = settings.showRates,
            debts = data.debts,
            iOweBynMinor = data.debts.sumInByn(DebtDirection.I_OWE, rates),
            owedToMeBynMinor = data.debts.sumInByn(DebtDirection.OWED_TO_ME, rates),
            savings = data.savings,
            savingTotals = data.savingTotals,
            savingsBynMinor = data.savingTotals.sumOf {
                RatesRepository.toBynMinor(it.total, it.currencyCode, rates) ?: 0L
            },
            goals = data.goals,
            recurring = data.recurring,
            dueRecurringIds = data.recurring
                .filter { it.recurring.isDue() }
                .map { it.recurring.id }
                .toSet(),
            expenseCategories = categories.first,
            incomeCategories = categories.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletUiState())

    private fun List<DebtEntity>.sumInByn(
        direction: DebtDirection,
        rates: Map<String, RateEntity>
    ): Long = filter { it.direction == direction }
        .sumOf { RatesRepository.toBynMinor(it.amountMinor, it.currencyCode, rates) ?: 0L }

    fun addDebt(
        personName: String,
        direction: DebtDirection,
        amountMinor: Long,
        currencyCode: String,
        note: String
    ) {
        if (personName.isBlank() || amountMinor <= 0) return
        viewModelScope.launch {
            walletRepository.addDebt(personName, direction, amountMinor, currencyCode, note)
        }
    }

    fun repayDebt(debt: DebtEntity, amountMinor: Long, note: String?, accountId: Long?) {
        if (amountMinor <= 0) return
        viewModelScope.launch {
            walletRepository.repayDebt(
                debt,
                amountMinor,
                debtEntry(debt, amountMinor, note, accountId)
            )
        }
    }

    fun closeDebt(debt: DebtEntity, note: String?, accountId: Long?) {
        viewModelScope.launch {
            walletRepository.closeDebt(
                debt,
                debtEntry(debt, debt.amountMinor, note, accountId)
            )
        }
    }

    private fun debtEntry(
        debt: DebtEntity,
        amountMinor: Long,
        note: String?,
        accountId: Long?
    ): LedgerEntry? {
        if (note == null || amountMinor <= 0) return null
        val state = uiState.value
        val converted = inAppCurrency(amountMinor, debt.currencyCode) ?: return null
        val income = debt.direction == DebtDirection.OWED_TO_ME
        return LedgerEntry(
            categoryKey = if (income) CategorySeed.DEBT_INCOME else CategorySeed.DEBT_EXPENSE,
            type = if (income) TransactionType.INCOME else TransactionType.EXPENSE,
            amountMinor = converted,
            note = note,
            bynMinor = RatesRepository.toBynMinor(converted, state.currencyCode, state.rates),
            accountId = accountId
        )
    }

    private fun inAppCurrency(amountMinor: Long, from: String): Long? {
        val to = uiState.value.currencyCode
        if (from == to) return amountMinor
        val rate = suggestedRate(from, to) ?: return null
        return Math.round(amountMinor * rate)
    }

    fun deleteDebt(debt: DebtEntity) {
        viewModelScope.launch { walletRepository.deleteDebt(debt.id) }
    }

    fun updateDebt(
        debt: DebtEntity,
        personName: String,
        direction: DebtDirection,
        amountMinor: Long,
        currencyCode: String,
        note: String
    ) {
        if (personName.isBlank() || amountMinor <= 0) return
        viewModelScope.launch {
            walletRepository.updateDebt(
                debt.copy(
                    personName = personName.trim(),
                    direction = direction,
                    amountMinor = amountMinor,
                    currencyCode = currencyCode,
                    note = note.trim()
                )
            )
        }
    }

    fun setMultiAccount(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMultiAccount(enabled) }
    }

    fun addAccount(name: String, iconKey: String, colorArgb: Long, iconUri: Uri? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            accountRepository.addAccount(name, iconKey, colorArgb, iconUri)
        }
    }

    fun reorderAccounts(ids: List<Long>) {
        viewModelScope.launch { accountRepository.reorderAccounts(ids) }
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch { accountRepository.deleteAccount(account) }
    }

    fun setAccountBalance(account: AccountEntity, targetMinor: Long) {
        viewModelScope.launch { accountRepository.setAccountBalance(account, targetMinor) }
    }

    fun updateAccountAppearance(
        account: AccountEntity,
        name: String,
        iconKey: String,
        colorArgb: Long,
        renamed: Boolean,
        iconUri: Uri?,
        iconCleared: Boolean
    ) {
        viewModelScope.launch {
            accountRepository.updateAppearance(
                account,
                name,
                iconKey,
                colorArgb,
                renamed,
                iconUri,
                iconCleared
            )
        }
    }

    fun addSaving(
        amountMinor: Long,
        currencyCode: String,
        note: String,
        goalId: Long? = null,
        deductNote: String? = null,
        accountId: Long? = null
    ) {
        if (amountMinor == 0L) return
        viewModelScope.launch {
            walletRepository.addSaving(
                amountMinor,
                currencyCode,
                note,
                goalId,
                savingEntry(amountMinor, currencyCode, deductNote, accountId)
            )
        }
    }

    private fun savingEntry(
        amountMinor: Long,
        currencyCode: String,
        note: String?,
        accountId: Long?
    ): LedgerEntry? {
        if (note == null || amountMinor == 0L) return null
        val state = uiState.value
        val converted = inAppCurrency(Math.abs(amountMinor), currencyCode) ?: return null
        val withdrawal = amountMinor < 0
        return LedgerEntry(
            categoryKey = if (withdrawal) {
                CategorySeed.SAVINGS_INCOME
            } else {
                CategorySeed.SAVINGS_EXPENSE
            },
            type = if (withdrawal) TransactionType.INCOME else TransactionType.EXPENSE,
            amountMinor = converted,
            note = note,
            bynMinor = RatesRepository.toBynMinor(converted, state.currencyCode, state.rates),
            accountId = accountId
        )
    }

    fun addGoal(title: String, targetMinor: Long, currencyCode: String) {
        if (title.isBlank() || targetMinor <= 0) return
        viewModelScope.launch { walletRepository.addGoal(title, targetMinor, currencyCode) }
    }

    fun deleteGoal(goal: GoalUi) {
        viewModelScope.launch { walletRepository.deleteGoal(goal.goal.id) }
    }

    fun updateGoal(
        goalUi: GoalUi,
        title: String,
        targetMinor: Long,
        currencyCode: String
    ) {
        if (title.isBlank() || targetMinor <= 0) return
        val goal = goalUi.goal
        val factor = if (currencyCode != goal.currencyCode) {
            suggestedRate(goal.currencyCode, currencyCode)
        } else {
            null
        }
        viewModelScope.launch {
            walletRepository.updateGoal(
                goal.copy(
                    title = title.trim(),
                    targetMinor = targetMinor,
                    currencyCode = currencyCode
                ),
                savingsFactor = factor
            )
        }
    }

    fun deleteSaving(saving: SavingEntity) {
        viewModelScope.launch { walletRepository.deleteSaving(saving.id) }
    }

    fun addRecurring(
        title: String,
        amountMinor: Long,
        currencyCode: String,
        categoryId: Long,
        firstDue: java.time.LocalDate,
        frequency: RecurringFrequency,
        type: TransactionType,
        accountId: Long?
    ) {
        if (title.isBlank() || amountMinor <= 0) return
        viewModelScope.launch {
            walletRepository.addRecurring(
                title, amountMinor, currencyCode, categoryId, firstDue, frequency,
                type, accountId
            )
        }
    }

    fun addCategory(edit: CategoryEdit, type: TransactionType, onCreated: (Long) -> Unit) {
        if (edit.name.isBlank()) return
        viewModelScope.launch {
            onCreated(
                transactionRepository.addCategory(
                    edit.name,
                    edit.iconKey,
                    edit.colorArgb,
                    type,
                    edit.iconUri
                )
            )
        }
    }

    fun reorderCategories(ids: List<Long>) {
        viewModelScope.launch { transactionRepository.reorderCategories(ids) }
    }

    fun updateCategory(id: Long, edit: CategoryEdit) {
        viewModelScope.launch {
            transactionRepository.updateCategory(
                id,
                edit.name,
                edit.iconKey,
                edit.colorArgb,
                edit.iconUri,
                edit.iconCleared
            )
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch { transactionRepository.deleteCategory(category) }
    }

    fun updateRecurringDetails(
        item: RecurringWithCategory,
        title: String,
        amountMinor: Long,
        nextDue: java.time.LocalDate,
        frequency: RecurringFrequency,
        type: TransactionType,
        categoryId: Long,
        accountId: Long?
    ) {
        if (title.isBlank() || amountMinor <= 0) return
        viewModelScope.launch {
            walletRepository.updateRecurring(
                item.recurring.copy(
                    title = title.trim(),
                    amountMinor = amountMinor,
                    nextDueEpochDay = nextDue.toEpochDay(),
                    frequency = frequency,
                    type = type,
                    categoryId = categoryId,
                    accountId = accountId
                )
            )
        }
    }

    fun setRecurringEnabled(item: RecurringWithCategory, enabled: Boolean) {
        viewModelScope.launch { walletRepository.setRecurringEnabled(item.recurring, enabled) }
    }

    fun deleteRecurring(item: RecurringWithCategory) {
        viewModelScope.launch { walletRepository.deleteRecurring(item.recurring.id) }
    }

    fun confirmRecurring(
        item: RecurringWithCategory,
        amountMinor: Long,
        rate: Double,
        accountId: Long? = null
    ) {
        if (amountMinor <= 0 || rate <= 0.0) return
        viewModelScope.launch {
            val state = uiState.value
            val converted = Math.round(amountMinor * rate)
            val byn = RatesRepository.toBynMinor(converted, state.currencyCode, state.rates)
            walletRepository.confirmRecurring(item.recurring, converted, byn, accountId)
        }
    }

    fun suggestedRate(from: String, to: String): Double? {
        val rates = uiState.value.rates
        val fromRate = rates[from] ?: return null
        val toRate = rates[to] ?: return null
        if (fromRate.scale <= 0 || toRate.scale <= 0 || toRate.rate <= 0.0) return null
        return (fromRate.rate / fromRate.scale) / (toRate.rate / toRate.scale)
    }

    private companion object {
        const val SAVINGS_LIMIT = 6
    }
}

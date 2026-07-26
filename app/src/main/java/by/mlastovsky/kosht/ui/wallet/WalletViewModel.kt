package by.mlastovsky.kosht.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.RatesRepository
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.WalletRepository
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.data.db.DebtEntity
import by.mlastovsky.kosht.data.db.RateEntity
import by.mlastovsky.kosht.data.db.RecurringWithCategory
import by.mlastovsky.kosht.data.db.SavingEntity
import by.mlastovsky.kosht.data.db.SavingGoalEntity
import by.mlastovsky.kosht.data.db.SavingTotal
import by.mlastovsky.kosht.model.DebtDirection
import by.mlastovsky.kosht.model.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
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
    /** Planned payments can be income too, and those need their own categories. */
    val incomeCategories: List<CategoryEntity> = emptyList(),
    val multiAccount: Boolean = false,
    /** Accounts with their shown balances (transactions + adjustment). */
    val accountsWithBalances: List<Pair<by.mlastovsky.kosht.data.db.AccountEntity, Long>> =
        emptyList()
) {
    /**
     * Accounts worth offering in a picker: none until the user keeps several,
     * so a single-account setup never has to answer the question.
     */
    val pickableAccounts: List<by.mlastovsky.kosht.data.db.AccountEntity>
        get() = if (multiAccount) accountsWithBalances.map { it.first } else emptyList()
}

class WalletViewModel(
    private val walletRepository: WalletRepository,
    transactionRepository: TransactionRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: by.mlastovsky.kosht.data.AccountRepository
) : ViewModel() {

    private val refreshingRates = MutableStateFlow(false)

    private val _rateRefreshFailed = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()

    /** Emitted when a manual refresh could not reach the rates server. */
    val rateRefreshFailed: kotlinx.coroutines.flow.SharedFlow<Unit> =
        _rateRefreshFailed.asSharedFlow()

    init {
        viewModelScope.launch { ratesRepository.refreshIfStale() }
    }

    /**
     * Manual refresh. Offline it keeps the rates from the last successful
     * check and reports the failure so the UI can say so.
     */
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
        val settings: by.mlastovsky.kosht.data.AppSettings,
        val categories: Pair<List<CategoryEntity>, List<CategoryEntity>>,
        val refreshing: Boolean,
        val accountsWithBalances: List<Pair<by.mlastovsky.kosht.data.db.AccountEntity, Long>>
    )

    /** Expense and income categories, for whichever a planned payment is. */
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

    fun repayDebt(debt: DebtEntity, amountMinor: Long) {
        if (amountMinor <= 0) return
        viewModelScope.launch { walletRepository.repayDebt(debt, amountMinor) }
    }

    fun closeDebt(debt: DebtEntity) {
        viewModelScope.launch { walletRepository.closeDebt(debt) }
    }

    fun deleteDebt(debt: DebtEntity) {
        viewModelScope.launch { walletRepository.deleteDebt(debt.id) }
    }

    /** Corrects a debt: who, which way, how much, in what, and the note. */
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

    fun addAccount(name: String, iconKey: String, colorArgb: Long) {
        if (name.isBlank()) return
        viewModelScope.launch { accountRepository.addAccount(name, iconKey, colorArgb) }
    }

    fun deleteAccount(account: by.mlastovsky.kosht.data.db.AccountEntity) {
        viewModelScope.launch { accountRepository.deleteAccount(account) }
    }

    fun setAccountBalance(account: by.mlastovsky.kosht.data.db.AccountEntity, targetMinor: Long) {
        viewModelScope.launch { accountRepository.setAccountBalance(account, targetMinor) }
    }

    /**
     * Changes how an account looks. A rename drops the built-in key so the
     * custom name wins over the localized one.
     */
    fun updateAccountAppearance(
        account: by.mlastovsky.kosht.data.db.AccountEntity,
        name: String,
        iconKey: String,
        colorArgb: Long,
        renamed: Boolean
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            accountRepository.updateAccount(
                account.copy(
                    name = name.trim(),
                    iconKey = iconKey,
                    colorArgb = colorArgb,
                    key = if (renamed) null else account.key
                )
            )
        }
    }

    fun addSaving(amountMinor: Long, currencyCode: String, note: String, goalId: Long? = null) {
        if (amountMinor == 0L) return
        viewModelScope.launch {
            walletRepository.addSaving(amountMinor, currencyCode, note, goalId)
        }
    }

    fun addGoal(title: String, targetMinor: Long, currencyCode: String) {
        if (title.isBlank() || targetMinor <= 0) return
        viewModelScope.launch { walletRepository.addGoal(title, targetMinor, currencyCode) }
    }

    fun deleteGoal(goal: GoalUi) {
        viewModelScope.launch { walletRepository.deleteGoal(goal.goal.id) }
    }

    /**
     * Renames a goal, moves its target or changes its currency. Switching the
     * currency carries what is already set aside toward it across at the
     * official rate — the same thing changing the app currency does — so the
     * progress bar keeps meaning what it says.
     */
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
        frequency: by.mlastovsky.kosht.model.RecurringFrequency,
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

    fun updateRecurringDetails(
        item: RecurringWithCategory,
        title: String,
        amountMinor: Long,
        nextDue: java.time.LocalDate,
        frequency: by.mlastovsky.kosht.model.RecurringFrequency,
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

    /**
     * Confirms a due payment with a user-checked amount (in the payment's own
     * currency) and rate: recorded = amount × rate, in the app currency, on the
     * chosen account — or on the one the plan already names.
     */
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

    /** Official cross rate between the charge currency and the app currency. */
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

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
    val debts: List<DebtEntity> = emptyList(),
    val iOweBynMinor: Long = 0,
    val owedToMeBynMinor: Long = 0,
    val savings: List<SavingEntity> = emptyList(),
    val savingTotals: List<SavingTotal> = emptyList(),
    val savingsBynMinor: Long = 0,
    val goals: List<GoalUi> = emptyList(),
    val recurring: List<RecurringWithCategory> = emptyList(),
    val dueRecurringIds: Set<Long> = emptySet(),
    val expenseCategories: List<CategoryEntity> = emptyList()
)

class WalletViewModel(
    private val walletRepository: WalletRepository,
    transactionRepository: TransactionRepository,
    private val ratesRepository: RatesRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val refreshingRates = MutableStateFlow(false)

    init {
        viewModelScope.launch { ratesRepository.refreshIfStale() }
    }

    fun refreshRates() {
        if (refreshingRates.value) return
        viewModelScope.launch {
            refreshingRates.value = true
            runCatching { ratesRepository.refresh() }
            refreshingRates.value = false
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
        val expenseCategories: List<CategoryEntity>,
        val refreshing: Boolean
    )

    private val context = combine(
        ratesRepository.rates,
        settingsRepository.settings,
        transactionRepository.observeCategories(TransactionType.EXPENSE),
        refreshingRates,
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
        (rates, settings, expenseCategories, refreshing) ->
        WalletUiState(
            loaded = true,
            currencyCode = settings.currencyCode,
            rates = rates,
            ratesUpdatedAt = rates.values.maxOfOrNull { it.updatedAt },
            refreshingRates = refreshing,
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
            expenseCategories = expenseCategories
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

    fun deleteSaving(saving: SavingEntity) {
        viewModelScope.launch { walletRepository.deleteSaving(saving.id) }
    }

    fun addRecurring(
        title: String,
        amountMinor: Long,
        currencyCode: String,
        categoryId: Long,
        firstDue: java.time.LocalDate,
        frequency: by.mlastovsky.kosht.model.RecurringFrequency
    ) {
        if (title.isBlank() || amountMinor <= 0) return
        viewModelScope.launch {
            walletRepository.addRecurring(
                title, amountMinor, currencyCode, categoryId, firstDue, frequency
            )
        }
    }

    fun setRecurringEnabled(item: RecurringWithCategory, enabled: Boolean) {
        viewModelScope.launch { walletRepository.setRecurringEnabled(item.recurring, enabled) }
    }

    fun deleteRecurring(item: RecurringWithCategory) {
        viewModelScope.launch { walletRepository.deleteRecurring(item.recurring.id) }
    }

    /** Confirms a charge defined in the app currency (no conversion needed). */
    fun confirmRecurring(item: RecurringWithCategory) {
        viewModelScope.launch {
            val state = uiState.value
            val amount = item.recurring.amountMinor
            val byn = RatesRepository.toBynMinor(amount, state.currencyCode, state.rates)
            walletRepository.confirmRecurring(item.recurring, amount, byn)
        }
    }

    /**
     * Confirms a foreign-currency charge using a manually adjustable rate:
     * charged amount = recurring amount × [rate], in the app currency.
     */
    fun confirmRecurringWithRate(item: RecurringWithCategory, rate: Double) {
        if (rate <= 0.0) return
        viewModelScope.launch {
            val state = uiState.value
            val converted = Math.round(item.recurring.amountMinor * rate)
            val byn = RatesRepository.toBynMinor(converted, state.currencyCode, state.rates)
            walletRepository.confirmRecurring(item.recurring, converted, byn)
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

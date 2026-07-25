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
import by.mlastovsky.kosht.data.db.SavingTotal
import by.mlastovsky.kosht.model.DebtDirection
import by.mlastovsky.kosht.model.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val wallet = combine(
        walletRepository.observeDebts(),
        walletRepository.observeSavings(SAVINGS_LIMIT),
        walletRepository.observeSavingTotals(),
        walletRepository.observeRecurring(),
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
        val recurring: List<RecurringWithCategory>
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

    fun addSaving(amountMinor: Long, currencyCode: String, note: String) {
        if (amountMinor == 0L) return
        viewModelScope.launch { walletRepository.addSaving(amountMinor, currencyCode, note) }
    }

    fun deleteSaving(saving: SavingEntity) {
        viewModelScope.launch { walletRepository.deleteSaving(saving.id) }
    }

    fun addRecurring(title: String, amountMinor: Long, categoryId: Long, dayOfMonth: Int) {
        if (title.isBlank() || amountMinor <= 0) return
        viewModelScope.launch {
            walletRepository.addRecurring(title, amountMinor, categoryId, dayOfMonth)
        }
    }

    fun setRecurringEnabled(item: RecurringWithCategory, enabled: Boolean) {
        viewModelScope.launch { walletRepository.setRecurringEnabled(item.recurring, enabled) }
    }

    fun deleteRecurring(item: RecurringWithCategory) {
        viewModelScope.launch { walletRepository.deleteRecurring(item.recurring.id) }
    }

    fun confirmRecurring(item: RecurringWithCategory) {
        viewModelScope.launch { walletRepository.confirmRecurring(item.recurring) }
    }

    private companion object {
        const val SAVINGS_LIMIT = 6
    }
}

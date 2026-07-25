package by.mlastovsky.kosht.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.db.TransactionWithCategory
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class HomeUiState(
    val loaded: Boolean = false,
    val balanceMinor: Long = 0,
    val monthIncomeMinor: Long = 0,
    val monthExpenseMinor: Long = 0,
    val recent: List<TransactionWithCategory> = emptyList(),
    val currencyCode: String = SettingsRepository.DEFAULT_CURRENCY
)

class HomeViewModel(
    repository: TransactionRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val monthRange = Dates.monthRange(YearMonth.now())

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeBalance(),
        repository.observeTotal(TransactionType.INCOME, monthRange.first, monthRange.last + 1),
        repository.observeTotal(TransactionType.EXPENSE, monthRange.first, monthRange.last + 1),
        repository.observeRecent(RECENT_LIMIT),
        settingsRepository.settings
    ) { balance, income, expense, recent, settings ->
        HomeUiState(
            loaded = true,
            balanceMinor = balance,
            monthIncomeMinor = income,
            monthExpenseMinor = expense,
            recent = recent,
            currencyCode = settings.currencyCode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private companion object {
        const val RECENT_LIMIT = 8
    }
}

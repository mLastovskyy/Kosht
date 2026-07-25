package by.mlastovsky.kosht.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.RatesRepository
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.UserProfile
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
    /** Balance converted to BYN when the app currency differs; null otherwise. */
    val balanceBynMinor: Long? = null,
    val monthIncomeMinor: Long = 0,
    val monthExpenseMinor: Long = 0,
    val recent: List<TransactionWithCategory> = emptyList(),
    val currencyCode: String = SettingsRepository.DEFAULT_CURRENCY,
    val profile: UserProfile? = null
)

class HomeViewModel(
    repository: TransactionRepository,
    settingsRepository: SettingsRepository,
    ratesRepository: RatesRepository
) : ViewModel() {

    private val monthRange = Dates.monthRange(YearMonth.now())

    private val totals = combine(
        repository.observeBalance(),
        repository.observeTotal(TransactionType.INCOME, monthRange.first, monthRange.last + 1),
        repository.observeTotal(TransactionType.EXPENSE, monthRange.first, monthRange.last + 1),
        ::Triple
    )

    private data class HomeContext(
        val recent: List<TransactionWithCategory>,
        val settings: by.mlastovsky.kosht.data.AppSettings,
        val rates: Map<String, by.mlastovsky.kosht.data.db.RateEntity>,
        val profile: UserProfile
    )

    private val context = combine(
        repository.observeRecent(RECENT_LIMIT),
        settingsRepository.settings,
        ratesRepository.rates,
        settingsRepository.profile,
        ::HomeContext
    )

    val uiState: StateFlow<HomeUiState> = combine(totals, context) { (balance, income, expense),
        (recent, settings, rates, profile) ->
        val bynEquivalent = if (settings.currencyCode != "BYN") {
            RatesRepository.toBynMinor(balance, settings.currencyCode, rates)
        } else {
            null
        }
        HomeUiState(
            loaded = true,
            balanceMinor = balance,
            balanceBynMinor = bynEquivalent,
            monthIncomeMinor = income,
            monthExpenseMinor = expense,
            recent = recent,
            currencyCode = settings.currencyCode,
            profile = profile
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private companion object {
        const val RECENT_LIMIT = 8
    }
}

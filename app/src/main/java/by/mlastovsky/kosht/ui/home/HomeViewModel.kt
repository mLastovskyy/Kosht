package by.mlastovsky.kosht.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.AccountRepository
import by.mlastovsky.kosht.data.AppSettings
import by.mlastovsky.kosht.data.RatesRepository
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.UserProfile
import by.mlastovsky.kosht.data.db.AccountEntity
import by.mlastovsky.kosht.data.db.RateEntity
import by.mlastovsky.kosht.data.db.TransactionWithCategory
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.util.Dates
import by.mlastovsky.kosht.util.Streak
import java.time.YearMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loaded: Boolean = false,
    val balanceMinor: Long = 0,

    val balanceBynMinor: Long? = null,
    val monthIncomeMinor: Long = 0,
    val monthExpenseMinor: Long = 0,
    val recent: List<TransactionWithCategory> = emptyList(),
    val currencyCode: String = SettingsRepository.DEFAULT_CURRENCY,
    val profile: UserProfile? = null,
    val streakDays: Int = 0,
    val showGreeting: Boolean = true,
    val showStreak: Boolean = true,

    val accountBalances: List<Pair<AccountEntity, Long>> = emptyList(),

    val accounts: List<AccountEntity> = emptyList()
)

class HomeViewModel(
    repository: TransactionRepository,
    settingsRepository: SettingsRepository,
    ratesRepository: RatesRepository,
    accountRepository: AccountRepository
) : ViewModel() {

    private val monthRange = Dates.monthRange(YearMonth.now())

    private data class Totals(
        val balance: Long,
        val income: Long,
        val expense: Long,
        val spendByDay: Map<java.time.LocalDate, Long>,
        val firstRecordDay: java.time.LocalDate?
    )

    private val totals = combine(
        repository.observeBalance(),
        repository.observeTotal(TransactionType.INCOME, monthRange.first, monthRange.last + 1),
        repository.observeTotal(TransactionType.EXPENSE, monthRange.first, monthRange.last + 1),
        repository.observeBetween(
            Dates.toEpochMillis(java.time.LocalDate.now().minusDays(90)),
            Long.MAX_VALUE
        )
    ) { balance, income, expense, window ->
        Totals(
            balance = balance,
            income = income,
            expense = expense,
            spendByDay = Streak.spendByDay(window),
            firstRecordDay = window
                .filter { !it.transaction.isTransfer }
                .minOfOrNull { it.transaction.timestamp }
                ?.let(Dates::toLocalDate)
        )
    }

    private data class HomeContext(
        val recent: List<TransactionWithCategory>,
        val settings: AppSettings,
        val rates: Map<String, RateEntity>,
        val profile: UserProfile,
        val accountBalances: List<Pair<AccountEntity, Long>>
    )

    private val accountBalances = combine(
        accountRepository.observeAccounts(),
        accountRepository.observeBalances()
    ) { accounts, balances ->
        val primaryId = accounts.firstOrNull()?.id
        accounts.map { account ->
            val own = balances.firstOrNull { it.accountId == account.id }?.balance ?: 0L

            val legacy = if (account.id == primaryId) {
                balances.firstOrNull { it.accountId == null }?.balance ?: 0L
            } else {
                0L
            }
            account to (own + legacy + account.adjustmentMinor)
        }
    }

    private val context = combine(
        repository.observeRecent(RECENT_LIMIT),
        settingsRepository.settings,
        ratesRepository.rates,
        settingsRepository.profile,
        accountBalances,
        ::HomeContext
    )

    val uiState: StateFlow<HomeUiState> = combine(totals, context) { t, ctx ->
        val settings = ctx.settings

        val totalBalance = t.balance +
            ctx.accountBalances.sumOf { it.first.adjustmentMinor }
        val bynEquivalent = if (settings.currencyCode != "BYN") {
            RatesRepository.toBynMinor(totalBalance, settings.currencyCode, ctx.rates)
        } else {
            null
        }
        val budget = settings.dailyBudgetMinor.takeIf { it > 0 }
            ?: Streak.autoDailyBudget(t.spendByDay)
        HomeUiState(
            loaded = true,
            balanceMinor = totalBalance,
            balanceBynMinor = bynEquivalent,
            monthIncomeMinor = t.income,
            monthExpenseMinor = t.expense,
            recent = ctx.recent,
            currencyCode = settings.currencyCode,
            profile = ctx.profile,
            streakDays = Streak.budgetStreak(t.spendByDay, budget, t.firstRecordDay),
            showGreeting = settings.showGreeting,
            showStreak = settings.showStreak,
            accountBalances = if (settings.multiAccount && ctx.accountBalances.size > 1) {
                ctx.accountBalances
            } else {
                emptyList()
            },
            accounts = ctx.accountBalances.map { it.first }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private companion object {
        const val RECENT_LIMIT = 8
    }
}

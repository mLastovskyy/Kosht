package by.mlastovsky.kosht.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.UserProfile
import by.mlastovsky.kosht.data.WalletRepository
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.data.db.TransactionWithCategory
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.util.Dates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

data class CategorySlice(
    val category: CategoryEntity,
    val totalMinor: Long,
    val share: Float
)

enum class ReportVerdict { GREAT, OK, BAD }

enum class ReportTip {
    OVERSPEND,       // expenses exceed income
    GROWTH,          // expenses grew a lot vs last month
    TOP_HEAVY,       // one category dominates
    START_SAVING,    // positive month but nothing set aside
    KEEP_IT_UP       // spending went down
}

data class ReportUi(
    val verdict: ReportVerdict,
    val expenseMinor: Long,
    val prevExpenseMinor: Long,
    /** Expenses change vs previous month in percent; null if no base. */
    val deltaPercent: Int?,
    val incomeMinor: Long,
    val netMinor: Long,
    val avgPerDayMinor: Long,
    val daysWithoutSpending: Int,
    val topSlice: CategorySlice?,
    val tips: List<ReportTip>,
    val userName: String
)

data class StatsUiState(
    val loaded: Boolean = false,
    val month: YearMonth = YearMonth.now(),
    val type: TransactionType = TransactionType.EXPENSE,
    val totalMinor: Long = 0,
    val slices: List<CategorySlice> = emptyList(),
    /** Sum per day of month, index 0 = first day. */
    val daily: List<Long> = emptyList(),
    /** Transactions of the selected type grouped by day, for the calendar view. */
    val byDay: Map<LocalDate, List<TransactionWithCategory>> = emptyMap(),
    val report: ReportUi? = null,
    val accounts: List<by.mlastovsky.kosht.data.db.AccountEntity> = emptyList(),
    val accountFilter: Long? = null,
    val currencyCode: String = SettingsRepository.DEFAULT_CURRENCY
) {
    val isCurrentMonth: Boolean
        get() = month == YearMonth.now()

    val hasData: Boolean
        get() = totalMinor > 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    repository: TransactionRepository,
    settingsRepository: SettingsRepository,
    walletRepository: WalletRepository,
    accountRepository: by.mlastovsky.kosht.data.AccountRepository
) : ViewModel() {

    private data class Selector(
        val month: YearMonth = YearMonth.now(),
        val type: TransactionType = TransactionType.EXPENSE,
        val accountId: Long? = null
    )

    private val selector = MutableStateFlow(Selector())

    private val transactions = selector.flatMapLatest { s ->
        val range = Dates.monthRange(s.month)
        repository.observeBetween(range.first, range.last + 1)
    }

    private val prevMonthTransactions = selector.flatMapLatest { s ->
        val range = Dates.monthRange(s.month.minusMonths(1))
        repository.observeBetween(range.first, range.last + 1)
    }

    private data class ReportContext(
        val prev: List<TransactionWithCategory>,
        val savings: List<by.mlastovsky.kosht.data.db.SavingEntity>,
        val profile: UserProfile,
        val accounts: List<by.mlastovsky.kosht.data.db.AccountEntity>
    )

    private val reportContext = combine(
        prevMonthTransactions,
        walletRepository.observeSavingsSince(0L),
        settingsRepository.profile,
        accountRepository.observeAccounts(),
        ::ReportContext
    )

    val uiState: StateFlow<StatsUiState> = combine(
        selector,
        transactions,
        settingsRepository.settings,
        reportContext
    ) { s, all, settings, report ->
        val primaryId = report.accounts.firstOrNull()?.id
        val byAccount = if (s.accountId == null) {
            all
        } else {
            all.filter { (it.transaction.accountId ?: primaryId) == s.accountId }
        }
        val relevant = byAccount.filter { it.transaction.type == s.type }
        val total = relevant.sumOf { it.transaction.amountMinor }

        val slices = relevant
            .groupBy { it.category }
            .map { (category, items) ->
                val sum = items.sumOf { it.transaction.amountMinor }
                CategorySlice(
                    category = category,
                    totalMinor = sum,
                    share = if (total > 0) sum.toFloat() / total else 0f
                )
            }
            .sortedByDescending { it.totalMinor }

        val daysInMonth = s.month.lengthOfMonth()
        val daily = LongArray(daysInMonth)
        relevant.forEach {
            val day = Dates.toLocalDate(it.transaction.timestamp).dayOfMonth
            daily[day - 1] += it.transaction.amountMinor
        }

        StatsUiState(
            loaded = true,
            month = s.month,
            type = s.type,
            totalMinor = total,
            slices = slices,
            daily = daily.toList(),
            byDay = relevant.groupBy { Dates.toLocalDate(it.transaction.timestamp) },
            report = buildReport(s.month, byAccount, report),
            accounts = report.accounts,
            accountFilter = s.accountId,
            currencyCode = settings.currencyCode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private fun buildReport(
        month: YearMonth,
        current: List<TransactionWithCategory>,
        context: ReportContext
    ): ReportUi {
        val expenses = current.filter { it.transaction.type == TransactionType.EXPENSE }
        val expense = expenses.sumOf { it.transaction.amountMinor }
        val income = current.filter { it.transaction.type == TransactionType.INCOME }
            .sumOf { it.transaction.amountMinor }
        val prevExpense = context.prev
            .filter { it.transaction.type == TransactionType.EXPENSE }
            .sumOf { it.transaction.amountMinor }
        val deltaPercent = if (prevExpense > 0) {
            (((expense - prevExpense) * 100.0) / prevExpense).toInt()
        } else {
            null
        }
        val net = income - expense

        // For the current month, count only elapsed days.
        val daysElapsed = if (month == YearMonth.now()) {
            LocalDate.now().dayOfMonth
        } else {
            month.lengthOfMonth()
        }
        val spendingDays = expenses
            .map { Dates.toLocalDate(it.transaction.timestamp) }
            .toSet().size
        val daysWithoutSpending = (daysElapsed - spendingDays).coerceAtLeast(0)

        val topSlice = expenses
            .groupBy { it.category }
            .map { (category, items) ->
                val sum = items.sumOf { it.transaction.amountMinor }
                CategorySlice(
                    category = category,
                    totalMinor = sum,
                    share = if (expense > 0) sum.toFloat() / expense else 0f
                )
            }
            .maxByOrNull { it.totalMinor }

        val monthRange = Dates.monthRange(month)
        val savedThisMonth = context.savings
            .filter { it.timestamp in monthRange && it.amountMinor > 0 }
            .sumOf { it.amountMinor }

        val tips = buildList {
            if (income in 1 until expense) add(ReportTip.OVERSPEND)
            if (deltaPercent != null && deltaPercent > 20) add(ReportTip.GROWTH)
            if (topSlice != null && topSlice.share > 0.35f) add(ReportTip.TOP_HEAVY)
            if (net > 0 && savedThisMonth == 0L) add(ReportTip.START_SAVING)
            if (deltaPercent != null && deltaPercent < -10) add(ReportTip.KEEP_IT_UP)
        }

        val verdict = when {
            income > 0 && expense > income -> ReportVerdict.BAD
            income == 0L && expense > 0 -> ReportVerdict.OK
            deltaPercent != null && deltaPercent > 20 -> ReportVerdict.OK
            else -> ReportVerdict.GREAT
        }

        return ReportUi(
            verdict = verdict,
            expenseMinor = expense,
            prevExpenseMinor = prevExpense,
            deltaPercent = deltaPercent,
            incomeMinor = income,
            netMinor = net,
            avgPerDayMinor = if (daysElapsed > 0) expense / daysElapsed else 0,
            daysWithoutSpending = daysWithoutSpending,
            topSlice = topSlice,
            tips = tips,
            userName = context.profile.name.ifBlank { context.profile.nickname }
        )
    }

    fun previousMonth() = selector.update { it.copy(month = it.month.minusMonths(1)) }

    fun nextMonth() = selector.update { s ->
        if (s.month < YearMonth.now()) s.copy(month = s.month.plusMonths(1)) else s
    }

    fun setType(type: TransactionType) = selector.update { it.copy(type = type) }

    fun setAccountFilter(accountId: Long?) =
        selector.update { it.copy(accountId = accountId) }
}

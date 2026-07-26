package by.mlastovsky.kosht.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.UserProfile
import by.mlastovsky.kosht.data.WalletRepository
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.data.db.TransactionWithCategory
import by.mlastovsky.kosht.model.ReportField
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.util.Dates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class CategorySlice(
    val category: CategoryEntity,
    val totalMinor: Long,
    val share: Float
)

/** One product across the period on screen: how often, how many, how much. */
data class ProductRow(
    val name: String,
    /** In how many records it turned up. */
    val lines: Int,
    /** Sum of the quantities, when the lines said any. */
    val quantity: Double?,
    val totalMinor: Long,
    /** Share of what the listed products of its category cost, for the bar. */
    val share: Float
)

enum class ReportVerdict { GREAT, OK, BAD }

/** The report window: current week, month, quarter or year (± shifts). */
enum class ReportPeriod { WEEK, MONTH, QUARTER, YEAR }

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
    /** Expenses change vs the previous period in percent; null if no base. */
    val deltaPercent: Int?,
    val incomeMinor: Long,
    val netMinor: Long,
    val avgPerDayMinor: Long,
    val daysWithoutSpending: Int,
    val topSlice: CategorySlice?,
    val tips: List<ReportTip>,
    val userName: String,
    val periodStart: LocalDate,
    val periodEnd: LocalDate
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
    val reportPeriod: ReportPeriod = ReportPeriod.MONTH,
    /** 0 = current period, −1 = previous one, and so on. */
    val reportShift: Int = 0,
    /** Which metric rows the report shows. */
    val reportFields: Set<ReportField> = ReportField.entries.toSet(),
    val accounts: List<by.mlastovsky.kosht.data.db.AccountEntity> = emptyList(),
    val accountFilter: Long? = null,
    val currencyCode: String = SettingsRepository.DEFAULT_CURRENCY,
    /**
     * Products behind each category, biggest spend first. A category with none
     * is simply absent — that is what keeps the list of categories a list of
     * categories until someone asks it for more.
     */
    val productsByCategory: Map<Long, List<ProductRow>> = emptyMap()
) {
    val isCurrentMonth: Boolean
        get() = month == YearMonth.now()

    val hasData: Boolean
        get() = totalMinor > 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    repository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    walletRepository: WalletRepository,
    accountRepository: by.mlastovsky.kosht.data.AccountRepository
) : ViewModel() {

    private data class Selector(
        val month: YearMonth = YearMonth.now(),
        val type: TransactionType = TransactionType.EXPENSE,
        val accountId: Long? = null,
        val reportShift: Int = 0
    )

    private val selector = MutableStateFlow(Selector())

    /** The report window kind lives in Settings; only the shift is local. */
    private val reportPeriod = settingsRepository.settings
        .map { parseReportPeriod(it.reportPeriod) }
        .distinctUntilChanged()

    private data class ReportWindow(val period: ReportPeriod, val shift: Int)

    private val reportWindow = combine(reportPeriod, selector) { period, s ->
        ReportWindow(period, s.reportShift)
    }.distinctUntilChanged()

    private data class PeriodData(
        val transactions: List<TransactionWithCategory>,
        /** The product lines of the same month, when any were written down. */
        val items: List<by.mlastovsky.kosht.data.db.ItemInContext>
    )

    private val period = selector.flatMapLatest { s ->
        val range = Dates.monthRange(s.month)
        combine(
            repository.observeBetween(range.first, range.last + 1).spendingOnly(),
            repository.observeItemsBetween(range.first, range.last + 1),
            ::PeriodData
        )
    }

    private val reportTransactions = reportWindow.flatMapLatest { w ->
        val (start, end) = reportBounds(w.period, w.shift)
        repository.observeBetween(
            Dates.toEpochMillis(start),
            Dates.toEpochMillis(end.plusDays(1))
        ).spendingOnly()
    }

    private val prevReportTransactions = reportWindow.flatMapLatest { w ->
        val (start, end) = reportBounds(w.period, w.shift - 1)
        repository.observeBetween(
            Dates.toEpochMillis(start),
            Dates.toEpochMillis(end.plusDays(1))
        ).spendingOnly()
    }

    /**
     * Every chart, the calendar and the report answer a question about spending
     * or earning, and a transfer between one's own accounts is neither — so
     * none of them is ever handed one.
     */
    private fun Flow<List<TransactionWithCategory>>.spendingOnly():
        Flow<List<TransactionWithCategory>> =
        map { list -> list.filter { !it.transaction.isTransfer } }

    init {
        // Switching the window kind in Settings starts from the current one.
        viewModelScope.launch {
            reportPeriod.collect { selector.update { s -> s.copy(reportShift = 0) } }
        }
    }

    private data class ReportContext(
        val current: List<TransactionWithCategory>,
        val prev: List<TransactionWithCategory>,
        val savings: List<by.mlastovsky.kosht.data.db.SavingEntity>,
        val profile: UserProfile,
        val accounts: List<by.mlastovsky.kosht.data.db.AccountEntity>
    )

    private val reportContext = combine(
        reportTransactions,
        prevReportTransactions,
        walletRepository.observeSavingsSince(0L),
        settingsRepository.profile,
        accountRepository.observeAccounts(),
        ::ReportContext
    )

    val uiState: StateFlow<StatsUiState> = combine(
        selector,
        period,
        settingsRepository.settings,
        reportContext
    ) { s, periodData, settings, report ->
        val all = periodData.transactions
        val primaryId = report.accounts.firstOrNull()?.id
        fun List<TransactionWithCategory>.byAccount() = if (s.accountId == null) {
            this
        } else {
            filter { (it.transaction.accountId ?: primaryId) == s.accountId }
        }
        val activeReportPeriod = parseReportPeriod(settings.reportPeriod)
        val relevant = all.byAccount().filter { it.transaction.type == s.type }
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

        // What was actually bought, when the records say so — kept per category,
        // because that is where it is asked for: tap a category to see it.
        // Grouped on the settled name, so one product is one row however it
        // happened to be typed.
        val productsByCategory = periodData.items
            .filter { s.accountId == null || (it.accountId ?: primaryId) == s.accountId }
            .groupBy { it.categoryId }
            .mapValues { (_, itemsOfCategory) ->
                val categoryTotal = itemsOfCategory.sumOf { it.amountMinor }
                itemsOfCategory
                    .groupBy { it.name }
                    .map { (name, lines) ->
                        val sum = lines.sumOf { it.amountMinor }
                        ProductRow(
                            name = name,
                            lines = lines.size,
                            quantity = lines.mapNotNull { it.quantity }
                                .takeIf { it.isNotEmpty() }?.sum(),
                            totalMinor = sum,
                            share = if (categoryTotal > 0) sum.toFloat() / categoryTotal else 0f
                        )
                    }
                    .sortedWith(
                        compareByDescending<ProductRow> { it.totalMinor }.thenBy { it.name }
                    )
            }

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
            report = buildReport(
                period = activeReportPeriod,
                shift = s.reportShift,
                current = report.current.byAccount(),
                prev = report.prev.byAccount(),
                savings = report.savings,
                profile = report.profile
            ),
            reportPeriod = activeReportPeriod,
            reportShift = s.reportShift,
            reportFields = settings.reportFields
                .mapNotNull { name -> ReportField.entries.firstOrNull { it.name == name } }
                .toSet(),
            accounts = if (settings.multiAccount) report.accounts else emptyList(),
            accountFilter = s.accountId,
            currencyCode = settings.currencyCode,
            productsByCategory = productsByCategory
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private fun buildReport(
        period: ReportPeriod,
        shift: Int,
        current: List<TransactionWithCategory>,
        prev: List<TransactionWithCategory>,
        savings: List<by.mlastovsky.kosht.data.db.SavingEntity>,
        profile: UserProfile
    ): ReportUi {
        val (start, end) = reportBounds(period, shift)
        val expenses = current.filter { it.transaction.type == TransactionType.EXPENSE }
        val expense = expenses.sumOf { it.transaction.amountMinor }
        val income = current.filter { it.transaction.type == TransactionType.INCOME }
            .sumOf { it.transaction.amountMinor }
        val prevExpense = prev
            .filter { it.transaction.type == TransactionType.EXPENSE }
            .sumOf { it.transaction.amountMinor }
        val deltaPercent = if (prevExpense > 0) {
            (((expense - prevExpense) * 100.0) / prevExpense).toInt()
        } else {
            null
        }
        val net = income - expense

        // For a period still running, count only elapsed days.
        val today = LocalDate.now()
        val daysTotal = (end.toEpochDay() - start.toEpochDay() + 1).toInt()
        val daysElapsed = when {
            today < start -> 0
            today > end -> daysTotal
            else -> (today.toEpochDay() - start.toEpochDay() + 1).toInt()
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

        val periodMillis =
            Dates.toEpochMillis(start) until Dates.toEpochMillis(end.plusDays(1))
        val savedThisPeriod = savings
            .filter { it.timestamp in periodMillis && it.amountMinor > 0 }
            .sumOf { it.amountMinor }

        val tips = buildList {
            if (income in 1 until expense) add(ReportTip.OVERSPEND)
            if (deltaPercent != null && deltaPercent > 20) add(ReportTip.GROWTH)
            if (topSlice != null && topSlice.share > 0.35f) add(ReportTip.TOP_HEAVY)
            if (net > 0 && savedThisPeriod == 0L) add(ReportTip.START_SAVING)
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
            userName = profile.name.ifBlank { profile.nickname },
            periodStart = start,
            periodEnd = end
        )
    }

    fun previousMonth() = selector.update { it.copy(month = it.month.minusMonths(1)) }

    fun nextMonth() = selector.update { s ->
        if (s.month < YearMonth.now()) s.copy(month = s.month.plusMonths(1)) else s
    }

    fun setType(type: TransactionType) = selector.update { it.copy(type = type) }

    fun setAccountFilter(accountId: Long?) =
        selector.update { it.copy(accountId = accountId) }

    fun previousReportPeriod() =
        selector.update { it.copy(reportShift = it.reportShift - 1) }

    fun nextReportPeriod() = selector.update { s ->
        if (s.reportShift < 0) s.copy(reportShift = s.reportShift + 1) else s
    }

    private companion object {

        fun parseReportPeriod(name: String): ReportPeriod =
            ReportPeriod.entries.firstOrNull { it.name == name } ?: ReportPeriod.MONTH

        /** Calendar bounds of the report window shifted by whole periods. */
        fun reportBounds(period: ReportPeriod, shift: Int): Pair<LocalDate, LocalDate> {
            val today = LocalDate.now()
            return when (period) {
                ReportPeriod.WEEK -> {
                    val start = today
                        .with(
                            java.time.temporal.TemporalAdjusters
                                .previousOrSame(java.time.DayOfWeek.MONDAY)
                        )
                        .plusWeeks(shift.toLong())
                    start to start.plusDays(6)
                }

                ReportPeriod.MONTH -> {
                    val month = YearMonth.now().plusMonths(shift.toLong())
                    month.atDay(1) to month.atEndOfMonth()
                }

                ReportPeriod.QUARTER -> {
                    val quarterStartMonth = ((today.monthValue - 1) / 3) * 3 + 1
                    val start = LocalDate.of(today.year, quarterStartMonth, 1)
                        .plusMonths(3L * shift)
                    start to start.plusMonths(3).minusDays(1)
                }

                ReportPeriod.YEAR -> {
                    val start = LocalDate.of(today.year + shift, 1, 1)
                    start to start.plusYears(1).minusDays(1)
                }
            }
        }
    }
}

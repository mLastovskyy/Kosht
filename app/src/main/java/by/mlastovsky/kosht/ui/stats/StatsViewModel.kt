package by.mlastovsky.kosht.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.AccountRepository
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.db.AccountEntity
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.data.db.ItemInContext
import by.mlastovsky.kosht.data.db.TransactionWithCategory
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.util.Dates
import by.mlastovsky.kosht.util.ItemNames
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CategorySlice(
    val category: CategoryEntity,
    val totalMinor: Long,
    val share: Float
)

data class ProductRow(
    val name: String,

    val lines: Int,

    val quantity: Double?,
    val totalMinor: Long,

    val share: Float
)

data class StatsUiState(
    val loaded: Boolean = false,
    val month: YearMonth = YearMonth.now(),
    val type: TransactionType = TransactionType.EXPENSE,
    val totalMinor: Long = 0,
    val slices: List<CategorySlice> = emptyList(),

    val daily: List<Long> = emptyList(),

    val byDay: Map<LocalDate, List<TransactionWithCategory>> = emptyMap(),
    val accounts: List<AccountEntity> = emptyList(),
    val accountFilter: Long? = null,
    val currencyCode: String = SettingsRepository.DEFAULT_CURRENCY,

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
    settingsRepository: SettingsRepository,
    accountRepository: AccountRepository
) : ViewModel() {

    private data class Selector(
        val month: YearMonth = YearMonth.now(),
        val type: TransactionType = TransactionType.EXPENSE,
        val accountId: Long? = null
    )

    private val selector = MutableStateFlow(Selector())

    private data class PeriodData(
        val transactions: List<TransactionWithCategory>,

        val items: List<ItemInContext>
    )

    private val period = selector.flatMapLatest { s ->
        val range = Dates.monthRange(s.month)
        combine(
            repository.observeBetween(range.first, range.last + 1).spendingOnly(),
            repository.observeItemsBetween(range.first, range.last + 1),
            ::PeriodData
        )
    }

    private fun Flow<List<TransactionWithCategory>>.spendingOnly():
        Flow<List<TransactionWithCategory>> =
        map { list -> list.filter { !it.transaction.isTransfer } }

    val uiState: StateFlow<StatsUiState> = combine(
        selector,
        period,
        settingsRepository.settings,
        accountRepository.observeAccounts()
    ) { s, periodData, settings, accounts ->
        val all = periodData.transactions
        val primaryId = accounts.firstOrNull()?.id
        fun List<TransactionWithCategory>.byAccount() = if (s.accountId == null) {
            this
        } else {
            filter { (it.transaction.accountId ?: primaryId) == s.accountId }
        }
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

        val productsByCategory = periodData.items
            .filter { it.type == s.type }
            .filter { s.accountId == null || (it.accountId ?: primaryId) == s.accountId }
            .groupBy { it.categoryId }
            .mapValues { (_, itemsOfCategory) ->
                val categoryTotal = itemsOfCategory.sumOf { it.amountMinor }
                itemsOfCategory
                    .groupBy { ItemNames.key(it.name) }
                    .map { (_, lines) ->
                        val sum = lines.sumOf { it.amountMinor }
                        ProductRow(

                            name = lines.groupingBy { it.name }.eachCount()
                                .maxByOrNull { it.value }?.key.orEmpty(),
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
            accounts = if (settings.multiAccount) accounts else emptyList(),
            accountFilter = s.accountId,
            currencyCode = settings.currencyCode,
            productsByCategory = productsByCategory
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun previousMonth() = selector.update { it.copy(month = it.month.minusMonths(1)) }

    fun nextMonth() = selector.update { s ->
        if (s.month < YearMonth.now()) s.copy(month = s.month.plusMonths(1)) else s
    }

    fun setType(type: TransactionType) = selector.update { it.copy(type = type) }

    fun setAccountFilter(accountId: Long?) =
        selector.update { it.copy(accountId = accountId) }
}

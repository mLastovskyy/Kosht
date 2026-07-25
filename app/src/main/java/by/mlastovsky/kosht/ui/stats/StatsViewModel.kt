package by.mlastovsky.kosht.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
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
    settingsRepository: SettingsRepository
) : ViewModel() {

    private data class Selector(
        val month: YearMonth = YearMonth.now(),
        val type: TransactionType = TransactionType.EXPENSE
    )

    private val selector = MutableStateFlow(Selector())

    private val transactions = selector.flatMapLatest { s ->
        val range = Dates.monthRange(s.month)
        repository.observeBetween(range.first, range.last + 1)
    }

    val uiState: StateFlow<StatsUiState> = combine(
        selector,
        transactions,
        settingsRepository.settings
    ) { s, all, settings ->
        val relevant = all.filter { it.transaction.type == s.type }
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
            currencyCode = settings.currencyCode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun previousMonth() = selector.update { it.copy(month = it.month.minusMonths(1)) }

    fun nextMonth() = selector.update { s ->
        if (s.month < YearMonth.now()) s.copy(month = s.month.plusMonths(1)) else s
    }

    fun setType(type: TransactionType) = selector.update { it.copy(type = type) }
}

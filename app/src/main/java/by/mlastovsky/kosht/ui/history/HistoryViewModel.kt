package by.mlastovsky.kosht.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.mlastovsky.kosht.data.SettingsRepository
import by.mlastovsky.kosht.data.TransactionRepository
import by.mlastovsky.kosht.data.db.CategoryEntity
import by.mlastovsky.kosht.data.db.TransactionEntity
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class DayGroup(
    val date: LocalDate,
    val netMinor: Long,
    val items: List<TransactionWithCategory>
)

data class HistoryUiState(
    val loaded: Boolean = false,
    val month: YearMonth = YearMonth.now(),
    val typeFilter: TransactionType? = null,
    val categoryFilter: CategoryEntity? = null,
    /** Custom period; when set it replaces month navigation. */
    val rangeStart: LocalDate? = null,
    val rangeEnd: LocalDate? = null,
    val query: String = "",
    val groups: List<DayGroup> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val currencyCode: String = SettingsRepository.DEFAULT_CURRENCY
) {
    val isCurrentMonth: Boolean
        get() = month == YearMonth.now()

    val hasCustomRange: Boolean
        get() = rangeStart != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: TransactionRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private data class Filters(
        val month: YearMonth = YearMonth.now(),
        val type: TransactionType? = null,
        val categoryId: Long? = null,
        val rangeStart: LocalDate? = null,
        val rangeEnd: LocalDate? = null,
        val query: String = ""
    )

    private val filters = MutableStateFlow(Filters())

    private val periodTransactions = filters
        .flatMapLatest { f ->
            val (from, to) = if (f.rangeStart != null) {
                val end = f.rangeEnd ?: f.rangeStart
                Dates.toEpochMillis(f.rangeStart) to Dates.toEpochMillis(end.plusDays(1))
            } else {
                val range = Dates.monthRange(f.month)
                range.first to range.last + 1
            }
            repository.observeBetween(from, to)
        }

    val uiState: StateFlow<HistoryUiState> = combine(
        filters,
        periodTransactions,
        repository.observeCategories(),
        settingsRepository.settings
    ) { f, transactions, categories, settings ->
        val filtered = transactions
            .filter { f.type == null || it.transaction.type == f.type }
            .filter { f.categoryId == null || it.category.id == f.categoryId }
            .filter {
                f.query.isBlank() ||
                    it.transaction.note.contains(f.query.trim(), ignoreCase = true) ||
                    it.category.name.contains(f.query.trim(), ignoreCase = true)
            }
        val groups = filtered
            .groupBy { Dates.toLocalDate(it.transaction.timestamp) }
            .toSortedMap(compareByDescending { it })
            .map { (date, items) ->
                DayGroup(
                    date = date,
                    netMinor = items.sumOf {
                        if (it.transaction.type == TransactionType.INCOME) {
                            it.transaction.amountMinor
                        } else {
                            -it.transaction.amountMinor
                        }
                    },
                    items = items
                )
            }
        HistoryUiState(
            loaded = true,
            month = f.month,
            typeFilter = f.type,
            categoryFilter = categories.firstOrNull { it.id == f.categoryId },
            rangeStart = f.rangeStart,
            rangeEnd = f.rangeEnd,
            query = f.query,
            groups = groups,
            categories = categories,
            currencyCode = settings.currencyCode
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun previousMonth() = filters.update { it.copy(month = it.month.minusMonths(1)) }

    fun nextMonth() = filters.update { f ->
        if (f.month < YearMonth.now()) f.copy(month = f.month.plusMonths(1)) else f
    }

    fun setTypeFilter(type: TransactionType?) = filters.update { it.copy(type = type) }

    fun setCategoryFilter(categoryId: Long?) =
        filters.update { it.copy(categoryId = categoryId) }

    fun setDateRange(start: LocalDate, end: LocalDate?) =
        filters.update { it.copy(rangeStart = start, rangeEnd = end ?: start) }

    fun clearDateRange() = filters.update { it.copy(rangeStart = null, rangeEnd = null) }

    fun setQuery(query: String) = filters.update { it.copy(query = query.take(60)) }

    fun delete(item: TransactionWithCategory) {
        viewModelScope.launch { repository.deleteTransaction(item.transaction) }
    }

    /** Re-inserts a just-deleted transaction with its original id. */
    fun restore(transaction: TransactionEntity) {
        viewModelScope.launch { repository.addTransaction(transaction) }
    }
}

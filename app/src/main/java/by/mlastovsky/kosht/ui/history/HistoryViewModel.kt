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
    val currencyCode: String = SettingsRepository.DEFAULT_CURRENCY,
    /** Both ends of a transfer are named from these. */
    val accounts: List<by.mlastovsky.kosht.data.db.AccountEntity> = emptyList()
) {
    val isCurrentMonth: Boolean
        get() = month == YearMonth.now()

    val hasCustomRange: Boolean
        get() = rangeStart != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: TransactionRepository,
    settingsRepository: SettingsRepository,
    accountRepository: by.mlastovsky.kosht.data.AccountRepository
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
        settingsRepository.settings,
        accountRepository.observeAccounts()
    ) { f, transactions, categories, settings, accounts ->
        val filtered = transactions
            // A transfer is neither income nor expense and belongs to no
            // category, so a filter on either of those leaves it out rather
            // than showing it as something it is not.
            .filter { !it.transaction.isTransfer || (f.type == null && f.categoryId == null) }
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
                    netMinor = items.sumOf { dayEffect(it.transaction) },
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
            currencyCode = settings.currencyCode,
            accounts = accounts
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    /**
     * What a record did to the money on that day. A transfer moved it rather
     * than spent it, so only what the transfer cost counts.
     */
    private fun dayEffect(transaction: TransactionEntity): Long = when {
        transaction.isTransfer -> -transaction.transferFeeMinor
        transaction.type == TransactionType.INCOME -> transaction.amountMinor
        else -> -transaction.amountMinor
    }

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
        viewModelScope.launch {
            // The product lines go with the record, so they are read before it
            // and travel with the offer to undo.
            val items = repository.itemsOf(item.transaction.id)
            repository.deleteTransaction(item.transaction)
            // The offer to undo it, and the cleanup of its files if nobody
            // does, are handled in one place for the whole app.
            by.mlastovsky.kosht.data.DeletionEvents.report(item.transaction, items)
        }
    }
}

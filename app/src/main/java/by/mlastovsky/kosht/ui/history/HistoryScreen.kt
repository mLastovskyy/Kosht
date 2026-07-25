package by.mlastovsky.kosht.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.ui.AppViewModelProvider
import by.mlastovsky.kosht.ui.CategoryVisuals
import by.mlastovsky.kosht.ui.components.CategoryBadge
import by.mlastovsky.kosht.ui.components.EmptyState
import by.mlastovsky.kosht.ui.components.MonthSelector
import by.mlastovsky.kosht.ui.components.TransactionRow
import by.mlastovsky.kosht.ui.relativeDate
import by.mlastovsky.kosht.ui.theme.KoshtTheme
import by.mlastovsky.kosht.util.Money
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    onTransactionClick: (Long) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.transaction_deleted)
    val undoLabel = stringResource(R.string.undo)
    var showCategoryFilter by remember { mutableStateOf(false) }
    var showRangePicker by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            if (!state.hasCustomRange) {
                MonthSelector(
                    month = state.month,
                    nextEnabled = !state.isCurrentMonth,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = rangeLabel(state.rangeStart!!, state.rangeEnd),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = viewModel::clearDateRange) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                    }
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.history_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    TypeFilterChip(
                        null, state.typeFilter, R.string.filter_all, viewModel::setTypeFilter
                    )
                }
                item {
                    TypeFilterChip(
                        TransactionType.EXPENSE,
                        state.typeFilter,
                        R.string.type_expense,
                        viewModel::setTypeFilter
                    )
                }
                item {
                    TypeFilterChip(
                        TransactionType.INCOME,
                        state.typeFilter,
                        R.string.type_income,
                        viewModel::setTypeFilter
                    )
                }
                item {
                    val category = state.categoryFilter
                    FilterChip(
                        selected = category != null,
                        onClick = { showCategoryFilter = true },
                        label = {
                            Text(
                                category?.let { CategoryVisuals.displayName(it) }
                                    ?: stringResource(R.string.filter_category)
                            )
                        },
                        trailingIcon = if (category != null) {
                            {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { viewModel.setCategoryFilter(null) }
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
                item {
                    FilterChip(
                        selected = state.hasCustomRange,
                        onClick = { showRangePicker = true },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(stringResource(R.string.filter_period)) }
                    )
                }
            }

            if (state.loaded && state.groups.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = stringResource(R.string.history_empty_title),
                    subtitle = stringResource(R.string.history_empty_subtitle)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    state.groups.forEach { group ->
                        item(key = "header-${group.date}") {
                            DayHeader(
                                dateLabel = relativeDate(group.date),
                                netMinor = group.netMinor,
                                currencyCode = state.currencyCode,
                                modifier = Modifier.animateItem()
                            )
                        }
                        items(group.items, key = { it.transaction.id }) { item ->
                            DismissibleRow(
                                modifier = Modifier.animateItem(),
                                onDismiss = {
                                    viewModel.delete(item)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = deletedMessage,
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.restore(item.transaction)
                                        }
                                    }
                                }
                            ) {
                                TransactionRow(
                                    item = item,
                                    currencyCode = state.currencyCode,
                                    onClick = { onTransactionClick(item.transaction.id) },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.background)
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }

    if (showCategoryFilter) {
        CategoryFilterDialog(
            categories = state.categories,
            onSelect = { id ->
                viewModel.setCategoryFilter(id)
                showCategoryFilter = false
            },
            onDismiss = { showCategoryFilter = false }
        )
    }

    if (showRangePicker) {
        val rangeState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(
                    enabled = rangeState.selectedStartDateMillis != null,
                    onClick = {
                        val start = rangeState.selectedStartDateMillis
                        if (start != null) {
                            viewModel.setDateRange(
                                start = utcMillisToDate(start),
                                end = rangeState.selectedEndDateMillis?.let(::utcMillisToDate)
                            )
                        }
                        showRangePicker = false
                    }
                ) { Text(stringResource(R.string.action_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { showRangePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DateRangePicker(
                state = rangeState,
                showModeToggle = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun utcMillisToDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun rangeLabel(start: LocalDate, end: LocalDate?): String {
    val locale = androidx.compose.ui.platform.LocalLocale.current.platformLocale
    val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
    val effectiveEnd = end ?: start
    return if (start == effectiveEnd) {
        relativeDate(start)
    } else {
        "${start.format(formatter)} – ${effectiveEnd.format(formatter)}"
    }
}

@Composable
private fun CategoryFilterDialog(
    categories: List<by.mlastovsky.kosht.data.db.CategoryEntity>,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_category)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.filter_all_categories),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(null) }
                        .padding(vertical = 12.dp)
                )
                categories.forEach { category ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(category.id) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CategoryBadge(
                            iconKey = category.iconKey,
                            color = Color(category.colorArgb),
                            size = 36.dp
                        )
                        Text(
                            text = CategoryVisuals.displayName(category),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun TypeFilterChip(
    value: TransactionType?,
    current: TransactionType?,
    labelRes: Int,
    onSelect: (TransactionType?) -> Unit
) {
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        label = { Text(stringResource(labelRes)) }
    )
}

@Composable
private fun DayHeader(
    dateLabel: String,
    netMinor: Long,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = (if (netMinor > 0) "+" else "") + Money.format(netMinor, currencyCode),
            style = MaterialTheme.typography.titleSmall,
            color = if (netMinor > 0) {
                KoshtTheme.colors.income
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleRow(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDismiss()
        },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.editor_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        content()
    }
}


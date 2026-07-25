package by.mlastovsky.kosht.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.ui.AppViewModelProvider
import by.mlastovsky.kosht.ui.CategoryVisuals
import by.mlastovsky.kosht.ui.components.AnimatedAmountText
import by.mlastovsky.kosht.ui.components.CategoryBadge
import by.mlastovsky.kosht.ui.components.EmptyState
import by.mlastovsky.kosht.ui.components.MonthSelector
import by.mlastovsky.kosht.ui.components.TransactionRow
import by.mlastovsky.kosht.ui.relativeDate
import by.mlastovsky.kosht.util.Money
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    onTransactionClick: (Long) -> Unit,
    viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var calendarMode by rememberSaveable { mutableStateOf(false) }
    var selectedEpochDay by rememberSaveable {
        mutableLongStateOf(LocalDate.now().toEpochDay())
    }
    val selectedDay = LocalDate.ofEpochDay(selectedEpochDay)
        .takeIf { YearMonth.from(LocalDate.ofEpochDay(selectedEpochDay)) == state.month }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        MonthSelector(
            month = state.month,
            nextEnabled = !state.isCurrentMonth,
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TypeToggle(
                type = state.type,
                onTypeChange = viewModel::setType,
                modifier = Modifier.weight(1f)
            )
            FilledIconToggleButton(
                checked = !calendarMode,
                onCheckedChange = { calendarMode = false }
            ) {
                Icon(
                    Icons.Rounded.DonutLarge,
                    contentDescription = stringResource(R.string.stats_view_charts)
                )
            }
            FilledIconToggleButton(
                checked = calendarMode,
                onCheckedChange = { calendarMode = true }
            ) {
                Icon(
                    Icons.Rounded.CalendarMonth,
                    contentDescription = stringResource(R.string.stats_view_calendar)
                )
            }
        }

        if (state.loaded && !state.hasData) {
            EmptyState(
                icon = Icons.Rounded.DonutLarge,
                title = stringResource(R.string.stats_no_data_title),
                subtitle = stringResource(R.string.stats_no_data_subtitle)
            )
        } else if (calendarMode) {
            CalendarContent(
                state = state,
                selectedDay = selectedDay,
                onDaySelect = { selectedEpochDay = it.toEpochDay() },
                onTransactionClick = onTransactionClick
            )
        } else {
            ChartsContent(state = state)
        }
    }
}

@Composable
private fun ChartsContent(state: StatsUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item(key = "donut") {
            DonutChart(
                slices = state.slices,
                totalText = Money.format(state.totalMinor, state.currencyCode),
                chartKey = "${state.month}-${state.type}",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            )
        }
        item(key = "bars") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.stats_by_day),
                    style = MaterialTheme.typography.titleMedium
                )
                DailyBarChart(
                    daily = state.daily,
                    chartKey = "${state.month}-${state.type}",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .padding(vertical = 8.dp)
                )
            }
        }
        items(state.slices, key = { it.category.id }) { slice ->
            CategorySliceRow(
                slice = slice,
                currencyCode = state.currencyCode,
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun CalendarContent(
    state: StatsUiState,
    selectedDay: LocalDate?,
    onDaySelect: (LocalDate) -> Unit,
    onTransactionClick: (Long) -> Unit
) {
    val dayItems = selectedDay?.let { state.byDay[it] }.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item(key = "calendar") {
            CalendarHeatmap(
                month = state.month,
                daily = state.daily,
                selectedDay = selectedDay,
                onDayClick = onDaySelect,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (selectedDay != null) {
            item(key = "day-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = relativeDate(selectedDay),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    val dayTotal = dayItems.sumOf { it.transaction.amountMinor }
                    if (dayTotal > 0) {
                        Text(
                            text = Money.format(dayTotal, state.currencyCode),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (dayItems.isEmpty()) {
                item(key = "day-empty") {
                    Text(
                        text = stringResource(R.string.stats_empty_day),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            items(dayItems, key = { it.transaction.id }) { item ->
                TransactionRow(
                    item = item,
                    currencyCode = state.currencyCode,
                    onClick = { onTransactionClick(item.transaction.id) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeToggle(
    type: TransactionType,
    onTypeChange: (TransactionType) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        TransactionType.EXPENSE to stringResource(R.string.type_expense),
        TransactionType.INCOME to stringResource(R.string.type_income)
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = type == option,
                onClick = { onTypeChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun DonutChart(
    slices: List<CategorySlice>,
    totalText: String,
    chartKey: String,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(chartKey) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    val strokeWidth = with(LocalDensity.current) { 26.dp.toPx() }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(1f)
        ) {
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )

            val gap = if (slices.size > 1) 2f else 0f
            var startAngle = -90f
            slices.forEach { slice ->
                val fullSweep = slice.share * 360f
                val sweep = ((fullSweep - gap) * progress.value).coerceAtLeast(0f)
                if (sweep > 0f) {
                    drawArc(
                        color = Color(slice.category.colorArgb),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke
                    )
                }
                startAngle += fullSweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.stats_total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedAmountText(
                text = totalText,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

@Composable
private fun DailyBarChart(
    daily: List<Long>,
    chartKey: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(chartKey) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 600, easing = FastOutSlowInEasing))
    }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val max = (daily.maxOrNull() ?: 0L).coerceAtLeast(1L)

    Canvas(modifier = modifier) {
        if (daily.isEmpty()) return@Canvas
        val slot = size.width / daily.size
        val barWidth = slot * 0.62f
        val corner = barWidth / 2f
        daily.forEachIndexed { index, value ->
            val x = index * slot + (slot - barWidth) / 2f
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(x, size.height - 4.dp.toPx()),
                size = Size(barWidth, 4.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner)
            )
            if (value > 0) {
                val barHeight =
                    (value.toFloat() / max) * (size.height - 8.dp.toPx()) * progress.value
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner)
                )
            }
        }
    }
}

@Composable
private fun CategorySliceRow(
    slice: CategorySlice,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryBadge(
            iconKey = slice.category.iconKey,
            color = Color(slice.category.colorArgb),
            size = 40.dp
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = CategoryVisuals.displayName(slice.category),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(slice.share * 100).roundToInt()} %",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { slice.share },
                color = Color(slice.category.colorArgb),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }
        Text(
            text = Money.format(slice.totalMinor, currencyCode),
            style = MaterialTheme.typography.titleSmall
        )
    }
}

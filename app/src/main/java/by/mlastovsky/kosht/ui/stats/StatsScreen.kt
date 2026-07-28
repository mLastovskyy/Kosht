package by.mlastovsky.kosht.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import by.mlastovsky.kosht.R
import by.mlastovsky.kosht.model.TransactionType
import by.mlastovsky.kosht.ui.AccountVisuals
import by.mlastovsky.kosht.ui.AppViewModelProvider
import by.mlastovsky.kosht.ui.CategoryVisuals
import by.mlastovsky.kosht.ui.components.AnimatedAmountText
import by.mlastovsky.kosht.ui.components.CategoryBadge
import by.mlastovsky.kosht.ui.components.EmptyState
import by.mlastovsky.kosht.ui.components.ItemsChip
import by.mlastovsky.kosht.ui.components.MonthSelector
import by.mlastovsky.kosht.ui.components.TransactionRow
import by.mlastovsky.kosht.ui.components.TruncatedText
import by.mlastovsky.kosht.ui.components.monthTitle
import by.mlastovsky.kosht.ui.components.rememberFullTextReveal
import by.mlastovsky.kosht.ui.countedAt
import by.mlastovsky.kosht.ui.relativeDate
import by.mlastovsky.kosht.ui.rememberMoneyColumn
import by.mlastovsky.kosht.ui.tabular
import by.mlastovsky.kosht.util.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    onTransactionClick: (Long) -> Unit,
    viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var viewMode by rememberSaveable { mutableStateOf(0) }
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
        if (viewMode == 2) {

            ReportPeriodBar(
                state = state,
                onPrevious = viewModel::previousReportPeriod,
                onNext = viewModel::nextReportPeriod
            )
        } else {
            MonthSelector(
                month = state.month,
                nextEnabled = !state.isCurrentMonth,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),

            horizontalArrangement = if (viewMode == 2) {
                Arrangement.End
            } else {
                Arrangement.Start
            },
            verticalAlignment = Alignment.CenterVertically
        ) {

            if (viewMode != 2) {
                TypeToggle(
                    type = state.type,
                    onTypeChange = viewModel::setType,
                    modifier = Modifier.weight(1f)
                )
            }
            FilledIconToggleButton(
                checked = viewMode == 0,
                onCheckedChange = { viewMode = 0 }
            ) {
                Icon(
                    Icons.Rounded.DonutLarge,
                    contentDescription = stringResource(R.string.stats_view_charts)
                )
            }
            FilledIconToggleButton(
                checked = viewMode == 1,
                onCheckedChange = { viewMode = 1 }
            ) {
                Icon(
                    Icons.Rounded.CalendarMonth,
                    contentDescription = stringResource(R.string.stats_view_calendar)
                )
            }
            FilledIconToggleButton(
                checked = viewMode == 2,
                onCheckedChange = { viewMode = 2 }
            ) {
                Icon(
                    Icons.Rounded.Insights,
                    contentDescription = stringResource(R.string.stats_view_report)
                )
            }
        }

        if (state.accounts.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.accountFilter == null,
                        onClick = { viewModel.setAccountFilter(null) },
                        label = { Text(stringResource(R.string.stats_all_accounts)) }
                    )
                }
                items(state.accounts.size, key = { state.accounts[it].id }) { index ->
                    val account = state.accounts[index]
                    FilterChip(
                        selected = state.accountFilter == account.id,
                        onClick = { viewModel.setAccountFilter(account.id) },
                        label = {
                            Text(AccountVisuals.displayName(account))
                        }
                    )
                }
            }
        }

        if (state.loaded && !state.hasData && viewMode != 2) {
            EmptyState(
                icon = Icons.Rounded.DonutLarge,
                title = stringResource(R.string.stats_no_data_title),
                subtitle = stringResource(R.string.stats_no_data_subtitle)
            )
        } else {
            when (viewMode) {
                1 -> CalendarContent(
                    state = state,
                    selectedDay = selectedDay,
                    onDaySelect = { selectedEpochDay = it.toEpochDay() },
                    onTransactionClick = onTransactionClick
                )
                2 -> ReportContent(state = state)
                else -> ChartsContent(state = state)
            }
        }
    }
}

@Composable
private fun ChartsContent(state: StatsUiState) {

    var expandedCategory by rememberSaveable { mutableStateOf<Long?>(null) }

    val openProducts = expandedCategory?.let { state.productsByCategory[it] }.orEmpty()
    val productStyle = MaterialTheme.typography.titleSmall.tabular()
    val productColumn = rememberMoneyColumn(
        amounts = openProducts.map { Money.format(it.totalMinor, state.currencyCode) },
        style = productStyle
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item(key = "donut") {
            DonutChart(
                slices = state.slices,
                totalText = Money.format(state.totalMinor, state.currencyCode),
                currencyCode = state.currencyCode,
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
        state.slices.forEach { slice ->
            val products = state.productsByCategory[slice.category.id].orEmpty()
            item(key = "slice-${slice.category.id}") {
                CategorySliceRow(
                    slice = slice,
                    currencyCode = state.currencyCode,

                    products = products.size,
                    expanded = expandedCategory == slice.category.id,
                    onClick = {
                        expandedCategory = if (expandedCategory == slice.category.id) {
                            null
                        } else {
                            slice.category.id
                        }
                    },
                    modifier = Modifier.animateItem()
                )
            }
            if (expandedCategory == slice.category.id) {
                items(products, key = { "product-${slice.category.id}-${it.name}" }) { product ->
                    ProductRowItem(
                        product = product,
                        currencyCode = state.currencyCode,
                        color = Color(slice.category.colorArgb),
                        sumStyle = productStyle,
                        sumColumn = productColumn,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductRowItem(
    product: ProductRow,
    currencyCode: String,
    color: Color,
    sumStyle: TextStyle,
    sumColumn: Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 72.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TruncatedText(
                text = product.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = Money.format(product.totalMinor, currencyCode),
                style = sumStyle,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(sumColumn)
            )
        }
        Text(
            text = countedAt(product.quantity, product.totalMinor, currencyCode)
                ?: stringResource(R.string.stats_product_times, product.lines),
            style = MaterialTheme.typography.labelSmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        LinearProgressIndicator(
            progress = { product.share },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        )
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

@Composable
private fun ReportPeriodBar(
    state: StatsUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null)
        }
        Text(
            text = reportPeriodTitle(state),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onNext, enabled = state.reportShift < 0) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun reportPeriodTitle(state: StatsUiState): String {
    val report = state.report ?: return ""
    val start = report.periodStart
    val end = report.periodEnd
    return when (state.reportPeriod) {
        ReportPeriod.WEEK -> {
            val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
            start.format(formatter) + " – " + end.format(formatter)
        }
        ReportPeriod.MONTH -> monthTitle(YearMonth.from(start))
        ReportPeriod.QUARTER -> stringResource(
            R.string.report_quarter_label,
            (start.monthValue - 1) / 3 + 1,
            start.year
        )
        ReportPeriod.YEAR -> start.year.toString()
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
    currencyCode: String,
    chartKey: String,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(chartKey) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    var revealedId by remember(chartKey) { mutableStateOf<Long?>(null) }
    val revealed = slices.firstOrNull { it.category.id == revealedId }
    val strokeWidth = with(LocalDensity.current) { 26.dp.toPx() }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(1f)
                .pointerInput(slices) {
                    detectTapGestures { at ->
                        val touched = sliceAt(at, size, strokeWidth, slices)
                        revealedId = touched.takeIf { it != revealedId }
                    }
                }
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
                        style = if (slice.category.id == revealedId) {
                            Stroke(width = strokeWidth * 1.3f, cap = StrokeCap.Butt)
                        } else {
                            stroke
                        }
                    )
                }
                startAngle += fullSweep
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = DONUT_LABEL_MAX_WIDTH)
        ) {
            Text(
                text = if (revealed == null) {
                    stringResource(R.string.stats_total)
                } else {
                    CategoryVisuals.displayName(revealed.category)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (revealed == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    Color(revealed.category.colorArgb)
                },
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            AnimatedAmountText(
                text = revealed?.let { Money.format(it.totalMinor, currencyCode) } ?: totalText,
                style = MaterialTheme.typography.headlineSmall
            )
            if (revealed != null) {
                Text(
                    text = "${(revealed.share * 100).roundToInt()} %",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val DONUT_LABEL_MAX_WIDTH = 132.dp

private fun sliceAt(
    at: Offset,
    canvas: IntSize,
    strokeWidth: Float,
    slices: List<CategorySlice>
): Long? {
    val center = Offset(canvas.width / 2f, canvas.height / 2f)
    val radius = (minOf(canvas.width, canvas.height) - strokeWidth) / 2f
    val distance = (at - center).getDistance()
    if (distance < radius - strokeWidth || distance > radius + strokeWidth) return null
    val fromNoon = (
        Math.toDegrees(
            atan2((at.y - center.y).toDouble(), (at.x - center.x).toDouble())
        ).toFloat() + 450f
        ) % 360f
    var startAngle = 0f
    slices.forEach { slice ->
        val sweep = slice.share * 360f
        if (fromNoon >= startAngle && fromNoon < startAngle + sweep) return slice.category.id
        startAngle += sweep
    }
    return null
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
                cornerRadius = CornerRadius(corner)
            )
            if (value > 0) {
                val barHeight =
                    (value.toFloat() / max) * (size.height - 8.dp.toPx()) * progress.value
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(corner)
                )
            }
        }
    }
}

@Composable
private fun CategorySliceRow(
    slice: CategorySlice,
    currencyCode: String,
    modifier: Modifier = Modifier,

    products: Int = 0,
    expanded: Boolean = false,
    onClick: () -> Unit = {}
) {
    val reveal = rememberFullTextReveal()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (products > 0 || reveal.truncated) {
                    Modifier.clickable {
                        reveal.reveal()
                        if (products > 0) onClick()
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryBadge(
            iconKey = slice.category.iconKey,
            color = Color(slice.category.colorArgb),
            size = 40.dp,
            iconPath = slice.category.iconPath
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TruncatedText(
                    text = CategoryVisuals.displayName(slice.category),
                    style = MaterialTheme.typography.bodyLarge,
                    reveal = reveal,
                    revealOnClick = false,
                    modifier = Modifier.weight(1f)
                )

                if (products > 0) {
                    ItemsChip(
                        count = products,
                        expanded = expanded,
                        onClick = onClick,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                Text(
                    text = "${(slice.share * 100).roundToInt()} %",
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp)
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
